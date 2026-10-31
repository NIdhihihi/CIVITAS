package com.civitas.platform.modules.auth;

import com.civitas.platform.config.JwtService;
import com.civitas.platform.modules.audit.AuditService;
import com.civitas.platform.modules.members.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;

@Service
public class AuthService {
    private final MemberRepository members;
    private final EmailOtpRepository otps;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final EmailService email;
    private final AuditService audit;
    private final int otpMinutes;
    private final boolean exposeOtp;
    private final SecureRandom random = new SecureRandom();

    public AuthService(MemberRepository members, EmailOtpRepository otps, PasswordEncoder passwords,
                       JwtService jwt, EmailService email, AuditService audit,
                       @Value("${civitas.otp.expiration-minutes}") int otpMinutes,
                       @Value("${civitas.otp.expose-in-development}") boolean exposeOtp) {
        this.members = members;
        this.otps = otps;
        this.passwords = passwords;
        this.jwt = jwt;
        this.email = email;
        this.audit = audit;
        this.otpMinutes = otpMinutes;
        this.exposeOtp = exposeOtp;
    }

    @Transactional
    public AuthDtos.OtpResponse register(AuthDtos.RegisterRequest request) {
        String address = request.email().trim().toLowerCase();
        var member = members.findByEmailIgnoreCase(address).orElseGet(() ->
                new Member(address, request.fullName().trim(), passwords.encode(request.password())));
        if (member.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        if (member.getPasswordHash() != null && !passwords.matches(request.password(), member.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An unverified account already exists; use its password to request a new code");
        }
        boolean newlyCreated = member.getId() == null;
        String previousName = member.getFullName();
        String previousPasswordHash = member.getPasswordHash();
        member.setFullName(request.fullName().trim());
        member.setPasswordHash(passwords.encode(request.password()));
        member = members.save(member);
        String code;
        try {
            code = sendOtp(address);
        } catch (RuntimeException ex) {
            if (newlyCreated) members.delete(member);
            else {
                member.setFullName(previousName);
                member.setPasswordHash(previousPasswordHash);
                members.save(member);
            }
            throw ex;
        }
        audit.recordQuietly("ACCOUNT_REGISTRATION", address, null, Map.of("fullName", member.getFullName()));
        return otpResponse("Account created. Verify your email with the code sent to your inbox.", address, code);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthDtos.AuthResponse verify(AuthDtos.VerifyEmailRequest request) {
        String address = request.email().trim().toLowerCase();
        var challenge = otps.findTopByEmailOrderByCreatedAtDesc(address).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active verification code; request a new one"));
        if (!Instant.now().isBefore(challenge.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification code has expired");
        }
        if (challenge.getAttempts() >= 5) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many incorrect attempts; request a new code");
        }
        if (!passwords.matches(request.code(), challenge.getCodeHash())) {
            challenge.incrementAttempts();
            otps.save(challenge);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification code");
        }
        var member = members.findByEmailIgnoreCase(address).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (member.isEmailVerified()) {
            otps.deleteByEmail(address);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already verified. Sign in instead.");
        }
        member.setEmailVerified(true);
        members.save(member);
        otps.deleteByEmail(address);
        audit.record("EMAIL_VERIFIED", member.getRole().name(), member.getEmail(), null, null, "MEMBER", member.getId().toString(), Map.of());
        return response(member);
    }

    @Transactional
    public AuthDtos.OtpResponse resend(AuthDtos.ResendOtpRequest request) {
        String address = request.email().trim().toLowerCase();
        var member = members.findByEmailIgnoreCase(address).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (member.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already verified");
        }
        String code = sendOtp(address);
        return otpResponse("A new verification code has been sent", address, code);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        var member = authenticate(request);
        if (member.getRole() == Role.PLATFORM_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Use the platform administrator sign-in");
        }
        audit.record("LOGIN_SUCCESS", member.getRole().name(), member.getEmail(), null, null, "MEMBER", member.getId().toString(), Map.of("role", member.getRole().name()));
        return response(member);
    }

    @Transactional
    public AuthDtos.AuthResponse adminLogin(AuthDtos.LoginRequest request) {
        var member = authenticate(request);
        if (member.getRole() != Role.PLATFORM_ADMIN && member.getRole() != Role.TENANT_ADMIN && member.getRole() != Role.AUDITOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This sign-in is for election administrators and auditors");
        }
        audit.record("LOGIN_SUCCESS", member.getRole().name(), member.getEmail(), null, null, "MEMBER", member.getId().toString(), Map.of("role", member.getRole().name()));
        return response(member);
    }

    public AuthDtos.MessageResponse logout(String email) {
        audit.record("LOGOUT", "USER", email, null, null, "MEMBER", null, Map.of());
        return new AuthDtos.MessageResponse("Signed out. Remove the access token from this device.");
    }

    public AuthDtos.MemberResponse me(String address) {
        return members.findByEmailIgnoreCase(address).map(this::view).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private String sendOtp(String address) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        otps.deleteByEmail(address);
        otps.save(new EmailOtp(address, passwords.encode(code), Instant.now().plusSeconds(otpMinutes * 60L)));
        try {
            email.sendOtp(address, code, otpMinutes);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery failed; please try again", ex);
        }
        return code;
    }

    private Member authenticate(AuthDtos.LoginRequest request) {
        String address = request.email().trim().toLowerCase();
        var member = members.findByEmailIgnoreCase(address).orElseGet(() -> {
            audit.recordQuietly("LOGIN_FAILED", address, null, Map.of("reason", "unknown_account"));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        });
        if (member.getPasswordHash() == null || !passwords.matches(request.password(), member.getPasswordHash())) {
            audit.recordQuietly("LOGIN_FAILED", address, null, Map.of("reason", "bad_password"));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (!member.isEmailVerified()) {
            audit.recordQuietly("LOGIN_FAILED", address, null, Map.of("reason", "email_unverified"));
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verify your email before signing in");
        }
        return member;
    }

    private AuthDtos.OtpResponse otpResponse(String message, String address, String code) {
        if (exposeOtp && !email.isSmtpConfigured()) {
            return new AuthDtos.OtpResponse(message, address, code);
        }
        return new AuthDtos.OtpResponse(message, address, null);
    }

    private AuthDtos.AuthResponse response(Member member) {
        return new AuthDtos.AuthResponse(jwt.generate(member), "Bearer", jwt.getExpirationMinutes() * 60L, view(member));
    }

    private AuthDtos.MemberResponse view(Member member) {
        return new AuthDtos.MemberResponse(member.getId().toString(), member.getEmail(), member.getFullName(),
                member.getRole().name(), member.isEmailVerified());
    }
}
