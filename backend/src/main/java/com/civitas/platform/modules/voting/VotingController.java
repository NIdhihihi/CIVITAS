package com.civitas.platform.modules.voting;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/voting")
public class VotingController {
    private final VotingService voting;

    public VotingController(VotingService voting) {
        this.voting = voting;
    }

    @GetMapping("/elections")
    public List<VotingDtos.EligibleElectionResponse> eligible(Authentication auth) {
        return voting.eligible(auth.getName());
    }

    @PostMapping("/auth/request-otp")
    public VotingDtos.OtpChallengeResponse requestOtp(@Valid @RequestBody VotingDtos.RequestOtpRequest request) {
        return voting.requestOtp(request);
    }

    @PostMapping("/auth/verify-otp")
    public VotingDtos.SessionResponse verifyOtp(@Valid @RequestBody VotingDtos.VerifyOtpRequest request) {
        return voting.verifyOtp(request);
    }

    @PostMapping("/auth/biometric")
    public VotingDtos.SessionResponse biometric(Authentication auth,
                                                @Valid @RequestBody VotingDtos.BiometricRequest request) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in before using device biometrics.");
        }
        return voting.biometric(auth.getName(), request.electionId());
    }

    @GetMapping("/ballot")
    public VotingDtos.BallotSessionResponse ballot(
            @RequestHeader(value = "X-Voting-Session", required = false) String session) {
        return voting.ballot(session);
    }

    @PostMapping("/confirm")
    public VotingDtos.ConfirmationResponse confirm(
            @RequestHeader(value = "X-Voting-Session", required = false) String session,
            @Valid @RequestBody VotingDtos.ConfirmRequest request) {
        return voting.confirm(session, request);
    }

    @PostMapping("/confirmation/cancel")
    public void cancelConfirmation(@RequestHeader(value = "X-Voting-Session", required = false) String session) {
        voting.cancelConfirmation(session);
    }

    @PostMapping("/cast")
    public VotingDtos.ReceiptResponse cast(
            @RequestHeader(value = "X-Voting-Session", required = false) String session,
            @Valid @RequestBody VotingDtos.CastRequest request) {
        return voting.cast(session, request);
    }

    @GetMapping("/receipt/{receiptCode}")
    public VotingDtos.ReceiptResponse receipt(@PathVariable String receiptCode) {
        return voting.receipt(receiptCode);
    }

    @GetMapping("/receipt/{receiptCode}/download")
    public ResponseEntity<String> downloadReceipt(@PathVariable String receiptCode) {
        String text = voting.receiptText(receiptCode);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"civitas-vote-receipt.txt\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(text);
    }

    @GetMapping("/receipts/mine")
    public List<VotingDtos.ReceiptResponse> myReceipts(Authentication auth) {
        return voting.history(auth.getName());
    }
}
