package com.civitas.platform.modules.voting;

import com.civitas.platform.common.CryptoUtils;
import com.civitas.platform.modules.audit.AuditService;
import com.civitas.platform.modules.auth.EmailService;
import com.civitas.platform.modules.elections.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class VotingService {
    private static final Logger log = LoggerFactory.getLogger(VotingService.class);
    private final ElectionRepository elections;
    private final ElectionPostRepository posts;
    private final ElectionVoterRepository voters;
    private final VotingOtpRepository otps;
    private final VotingSessionRepository sessions;
    private final VoteRepository votes;
    private final VoterParticipationRepository participation;
    private final BallotService ballots;
    private final BallotOptionService options;
    private final BallotCryptoService crypto;
    private final EmailService email;
    private final AuditService audit;
    private final String masterSecret;
    private final int otpMinutes;
    private final int sessionMinutes;
    private final int confirmationMinutes;
    private final boolean exposeOtp;

    public VotingService(ElectionRepository elections, ElectionPostRepository posts,
                         ElectionVoterRepository voters, VotingOtpRepository otps,
                         VotingSessionRepository sessions, VoteRepository votes,
                         VoterParticipationRepository participation, BallotService ballots,
                         BallotOptionService options, BallotCryptoService crypto, EmailService email,
                         AuditService audit,
                         @Value("${civitas.security.master-secret}") String masterSecret,
                         @Value("${civitas.otp.expiration-minutes:5}") int otpMinutes,
                         @Value("${civitas.voting.session-minutes:10}") int sessionMinutes,
                         @Value("${civitas.voting.confirmation-minutes:5}") int confirmationMinutes,
                         @Value("${civitas.otp.expose-in-development:true}") boolean exposeOtp) {
        this.elections = elections;
        this.posts = posts;
        this.voters = voters;
        this.otps = otps;
        this.sessions = sessions;
        this.votes = votes;
        this.participation = participation;
        this.ballots = ballots;
        this.options = options;
        this.crypto = crypto;
        this.email = email;
        this.audit = audit;
        this.masterSecret = masterSecret;
        this.otpMinutes = otpMinutes;
        this.sessionMinutes = sessionMinutes;
        this.confirmationMinutes = confirmationMinutes;
        this.exposeOtp = exposeOtp;
    }

    @Transactional(readOnly = true)
    public List<VotingDtos.EligibleElectionResponse> eligible(String email) {
        List<VotingDtos.EligibleElectionResponse> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (ElectionVoter voter : voters.findByEmailIgnoreCaseOrderByCreatedAtDesc(email)) {
            Election election = voter.getElection();
            if (!seen.add(election.getId()) || election.getStatus() == ElectionStatus.CANCELLED
                    || election.getStatus() == ElectionStatus.DRAFT) {
                continue;
            }
            result.add(new VotingDtos.EligibleElectionResponse(election.getId().toString(), election.getTitle(),
                    election.getTenant().getName(), election.getDescription(), election.getStartsAt(),
                    election.getEndsAt(), election.getStatus().name(), voter.isVoted(),
                    posts.countByElectionId(election.getId())));
        }
        return result;
    }

    @Transactional
    public VotingDtos.OtpChallengeResponse requestOtp(VotingDtos.RequestOtpRequest request) {
        Election election = activeElection(request.electionId());
        if (request.method() == VotingAuthMethod.BIOMETRIC) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use the biometric authentication endpoint.");
        }
        if (request.method() != VotingAuthMethod.EMAIL_OTP) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "Aadhaar verification is not connected to an authorized identity provider in this demo. Use email OTP.");
        }
        if (request.identifier() == null || request.identifier().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the identifier registered on the voter roll.");
        }
        ElectionVoter voter = findByEmail(election, request.identifier());
        if (voter.getEmail() == null || voter.getEmail().isBlank())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This voter has no email address for OTP delivery.");
        ensureCanVote(voter);
        otps.deleteByElectionIdAndVoterId(election.getId(), voter.getId());
        String code = CryptoUtils.randomDigits(6);
        VotingOtp otp = otps.save(new VotingOtp(election, voter, request.method(),
                hash("vote-otp:" + code), Instant.now().plusSeconds(otpMinutes * 60L)));
        try {
            email.sendVotingOtp(voter.getEmail(), code, election.getTitle(), otpMinutes);
        } catch (RuntimeException ex) {
            otps.delete(otp);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Email delivery failed; request a new verification code.", ex);
        }
        audit.record("VOTER_AUTH_REQUESTED", "VOTER", "voter:" + voter.getId(), election.getId().toString(),
                election.getTenant().getId().toString(), "ELECTION", election.getId().toString(),
                Map.of("method", request.method().name()));
        return new VotingDtos.OtpChallengeResponse(otp.getId().toString(), election.getId().toString(),
                request.method().name(), mask(voter), otp.getExpiresAt().toString(),
                exposeOtp ? code : null);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public VotingDtos.SessionResponse verifyOtp(VotingDtos.VerifyOtpRequest request) {
        VotingOtp otp = otps.findById(request.challengeId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "The verification challenge is not valid."));
        if (!otp.getElection().getId().equals(request.electionId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The verification challenge is not valid for this election.");
        }
        if (otp.getUsedAt() != null || !Instant.now().isBefore(otp.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The verification code has expired; request a new one.");
        }
        if (otp.getAttempts() >= 5) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many incorrect attempts; request a new code.");
        }
        if (!hash("vote-otp:" + request.code().trim()).equals(otp.getCodeHash())) {
            otp.incrementAttempts();
            otps.save(otp);
            audit.recordQuietly("VOTING_OTP_FAILED", "voter:" + otp.getVoter().getId(),
                    otp.getElection().getId().toString(), Map.of("challengeId", otp.getId().toString()));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That verification code is incorrect.");
        }
        otp.markUsed();
        otps.save(otp);
        ensureCanVote(otp.getVoter());
        return createSession(otp.getElection(), otp.getVoter(), otp.getMethod(), otp.getCreatedAt());
    }

    @Transactional
    public VotingDtos.SessionResponse biometric(String email, UUID electionId) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in before using device biometrics.");
        }
        Election election = activeElection(electionId);
        ElectionVoter voter = findByEmail(election, email);
        ensureCanVote(voter);
        return createSession(election, voter, VotingAuthMethod.BIOMETRIC, Instant.now());
    }

    @Transactional(readOnly = true)
    public VotingDtos.BallotSessionResponse ballot(String token) {
        VotingSession session = session(token);
        Election election = session.getElection();
        if (election.getStatus() != ElectionStatus.ACTIVE || !Instant.now().isBefore(election.getEndsAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The polling window is closed.");
        }
        ensureCanVote(session.getVoter());
        return new VotingDtos.BallotSessionResponse(election.getId().toString(), election.getTitle(),
                session.getVoter().getFullName(), ballots.assemble(election, session.getVoter()));
    }

    @Transactional
    public VotingDtos.ConfirmationResponse confirm(String token, VotingDtos.ConfirmRequest request) {
        VotingSession session = session(token);
        activeElection(session.getElection().getId());
        ensureCanVote(session.getVoter());
        if (session.getVoter().getEmail() == null || session.getVoter().getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A voter email is required for final vote confirmation in this demo.");
        }
        if (session.getConfirmationHash() != null
                && session.getConfirmationExpiresAt() != null
                && Instant.now().isBefore(session.getConfirmationExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A confirmation code is already active. Use it or wait for it to expire before requesting another.");
        }
        validateSelections(session, request.selections());
        String code = CryptoUtils.randomDigits(6);
        String destination;
        try {
            destination = sendConfirmationCode(session.getVoter(), code);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Email delivery failed; request a new confirmation code.", ex);
        }
        session.setSelectionHash(selectionHash(request.selections()));
        session.setConfirmation(hash("vote-confirm:" + code), Instant.now().plusSeconds(confirmationMinutes * 60L));
        sessions.save(session);
        audit.record("VOTE_CONFIRMATION_REQUESTED", "VOTER", "voter:" + session.getVoter().getId(),
                session.getElection().getId().toString(), session.getElection().getTenant().getId().toString(),
                "ELECTION", session.getElection().getId().toString(),
                Map.of("postCount", request.selections().size()));
        return new VotingDtos.ConfirmationResponse(true, destination,
                session.getConfirmationExpiresAt().toString(), exposeOtp ? code : null);
    }

    @Transactional
    public void cancelConfirmation(String token) {
        VotingSession session = session(token);
        activeElection(session.getElection().getId());
        ensureCanVote(session.getVoter());
        if (session.getConfirmationHash() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is no active confirmation to cancel.");
        }
        session.cancelConfirmation();
        sessions.save(session);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public VotingDtos.ReceiptResponse cast(String token, VotingDtos.CastRequest request) {
        VotingSession session = session(token);
        if (session.getConsumedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This voting session has already been used.");
        }
        Election election = activeElection(session.getElection().getId());
        ElectionVoter voter = session.getVoter();
        ensureCanVote(voter);
        if (session.getConfirmationHash() == null || session.getConfirmationExpiresAt() == null
                || !Instant.now().isBefore(session.getConfirmationExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Confirm your selections again before casting the vote.");
        }
        if (session.getConfirmationAttempts() >= 5) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many incorrect confirmation codes; confirm again.");
        }
        if (!selectionHash(request.selections()).equals(session.getSelectionHash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The ballot changed after confirmation. Review your selections again.");
        }
        if (!hash("vote-confirm:" + request.confirmationCode().trim()).equals(session.getConfirmationHash())) {
            session.incrementConfirmationAttempts();
            sessions.save(session);
            audit.recordQuietly("VOTE_CONFIRMATION_FAILED", "voter:" + voter.getId(),
                    election.getId().toString(), Map.of("sessionId", session.getId().toString()));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The confirmation code is incorrect.");
        }
        Map<ElectionPost, BallotOptionService.Option> validated = validateSelections(session, request.selections());
        List<String> nullifiers = new ArrayList<>();
        try {
            for (Map.Entry<ElectionPost, BallotOptionService.Option> entry : validated.entrySet()) {
                ElectionPost post = entry.getKey();
                BallotOptionService.Option option = entry.getValue();
                String nullifier = nullifier(election.getId().toString(), post.getId().toString(), voter.getId());
                nullifiers.add(nullifier);
                BallotCryptoService.SealedBallot sealed = crypto.seal(election.getId().toString(),
                        post.getId().toString(), nullifier, option.index(), option.type(), option.id());
                votes.save(new Vote(election, post, nullifier, sealed.ciphertext(), sealed.iv(),
                        sealed.commitment(), sealed.announcement(), sealed.s1(), sealed.s2()));
            }
            String nullifierRoot = CryptoUtils.sha256(String.join("|", nullifiers));
            UUID receiptId = UUID.randomUUID();
            String receiptCode = CryptoUtils.randomToken(24);
            Instant castAt = Instant.now();
            String receiptHash = CryptoUtils.sha256(election.getId() + "|" + receiptId + "|" + castAt
                    + "|" + validated.size() + "|" + nullifierRoot);
            String signature = CryptoUtils.hmacSha256(masterSecret, receiptHash);
            VoterParticipation record = new VoterParticipation(election, voter, receiptId,
                    CryptoUtils.sha256(receiptCode), receiptHash, signature, nullifierRoot, validated.size());
            participation.saveAndFlush(record);
            voter.markVoted(castAt);
            voters.save(voter);
            session.consume();
            sessions.save(session);
            audit.record("VOTE_CAST", "VOTER", "voter:" + voter.getId(), election.getId().toString(),
                    election.getTenant().getId().toString(), "ELECTION", election.getId().toString(),
                    Map.of("posts", validated.size(), "receiptHash", receiptHash));
            return receiptView(record, receiptCode);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A vote has already been recorded for one or more of these posts.");
        }
    }

    @Transactional(readOnly = true)
    public VotingDtos.ReceiptResponse receipt(String receiptCodeOrIdOrHash) {
        if (receiptCodeOrIdOrHash == null || receiptCodeOrIdOrHash.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found.");
        }
        String clean = receiptCodeOrIdOrHash.trim();
        Optional<VoterParticipation> record = participation.findByReceiptCodeHash(CryptoUtils.sha256(clean));
        VoterParticipation participationRecord = record.orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found."));
        return receiptView(participationRecord, null);
    }

    @Transactional(readOnly = true)
    public List<VotingDtos.ReceiptResponse> history(String email) {
        List<VotingDtos.ReceiptResponse> result = new ArrayList<>();
        for (ElectionVoter voter : voters.findByEmailIgnoreCaseOrderByCreatedAtDesc(email)) {
            for (VoterParticipation record : participation.findByVoterIdOrderByCastAtDesc(voter.getId())) {
                result.add(receiptView(record, null));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public String receiptText(String receiptCode) {
        VotingDtos.ReceiptResponse receipt = receipt(receiptCode);
        return "CIVITAS CRYPTOGRAPHIC VOTE RECEIPT\n"
                + "================================\n"
                + "Receipt ID: " + receipt.receiptId() + "\n"
                + "Election: " + receipt.electionTitle() + "\n"
                + "Cast at: " + receipt.castAt() + "\n"
                + "Posts voted: " + receipt.postsVoted() + "\n"
                + "Receipt hash (SHA-256): " + receipt.receiptHash() + "\n"
                + "Signature (HMAC-SHA-256): " + receipt.signature() + "\n\n"
                + "This receipt proves that a ballot was accepted. It deliberately does not reveal\n"
                + "which candidate or panel you selected. Keep it private. Anyone holding the\n"
                + "download link can verify the receipt metadata.\n";
    }

    public Election activeElection(UUID electionId) {
        Election election = elections.findById(electionId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Election not found."));
        Instant now = Instant.now();
        if (election.getStatus() != ElectionStatus.ACTIVE || now.isBefore(election.getStartsAt())
                || !now.isBefore(election.getEndsAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The polling window is not open.");
        }
        return election;
    }

    private ElectionVoter findByEmail(Election election, String email) {
        List<ElectionVoter> matches = voters.findByElectionIdAndEmailIgnoreCase(election.getId(), email.trim());
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That identity is not on this election's voter roll.");
        }
        if (matches.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This email appears more than once on the voter roll. Ask the election administrator to correct it.");
        }
        return matches.get(0);
    }

    private ElectionVoter findByAadhaar(Election election, String aadhaar) {
        String digits = aadhaar.replaceAll("\\D", "");
        if (digits.length() != 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the full 12-digit Aadhaar identifier.");
        }
        String hash = CryptoUtils.hmacSha256(masterSecret, "aadhaar:" + digits);
        Optional<ElectionVoter> match = voters.findByElectionIdAndAadhaarHash(election.getId(), hash).stream().findFirst();
        if (match.isPresent()) {
            return match.get();
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That identity is not on this election's voter roll.");
    }

    private void ensureCanVote(ElectionVoter voter) {
        if (voter.isVoted() || participation.existsByElectionIdAndVoterId(voter.getElection().getId(), voter.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A vote has already been recorded for this election. A second vote cannot be cast.");
        }
    }

    private VotingDtos.SessionResponse createSession(Election election, ElectionVoter voter,
                                                     VotingAuthMethod method, Instant startedAt) {
        ensureCanVote(voter);
        if (sessions.existsByElectionIdAndVoterIdAndConsumedAtIsNullAndExpiresAtAfter(
                election.getId(), voter.getId(), Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A voting session is already active for this voter. Continue with that session or wait for it to expire.");
        }
        String token = "cvs_" + CryptoUtils.randomToken(32);
        VotingSession session = new VotingSession(election, voter, hash("session:" + token), method,
                Instant.now().plusSeconds(sessionMinutes * 60L));
        session.authenticate();
        sessions.save(session);
        audit.record("VOTER_AUTHENTICATED", "VOTER", "voter:" + voter.getId(), election.getId().toString(),
                election.getTenant().getId().toString(), "ELECTION", election.getId().toString(),
                Map.of("method", method.name()));
        return new VotingDtos.SessionResponse(token, election.getId().toString(), election.getTitle(),
                voter.getFullName(), method.name(), session.getAuthenticatedAt().toString(),
                session.getExpiresAt().toString(), Math.max(0, Duration.between(startedAt, Instant.now()).toSeconds()));
    }

    private VotingSession session(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A voting session token is required.");
        }
        VotingSession session = sessions.findByTokenHash(hash("session:" + token.trim())).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The voting session is not valid."));
        if (!Instant.now().isBefore(session.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The voting session has expired. Authenticate again.");
        }
        if (session.getConsumedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This voting session has already been used.");
        }
        return session;
    }

    private Map<ElectionPost, BallotOptionService.Option> validateSelections(VotingSession session,
                                                                             List<VotingDtos.VoteSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one candidate or panel.");
        }
        Set<UUID> eligible = ballots.eligiblePostIds(session.getVoter());
        Map<ElectionPost, BallotOptionService.Option> result = new LinkedHashMap<>();
        for (VotingDtos.VoteSelection selection : selections) {
            if (selection == null || selection.postId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid selection.");
            }
            if (result.keySet().stream().anyMatch(p -> p.getId().equals(selection.postId()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A post can only receive one selection.");
            }
            if (!eligible.contains(selection.postId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not eligible to vote for this post.");
            }
            ElectionPost post = posts.findByIdAndElectionId(selection.postId(), session.getElection().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Post not found in this election."));
            boolean candidate = selection.candidateId() != null;
            boolean panel = selection.panelId() != null;
            if (candidate == panel) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose either a candidate or a panel for each post.");
            }
            if (candidate && post.getVotingMode() == VotingMode.PANEL) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This post is configured for panel voting only.");
            }
            if (panel && post.getVotingMode() == VotingMode.CANDIDATE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This post is configured for individual candidate voting only.");
            }
            String type = candidate ? "CANDIDATE" : "PANEL";
            String id = candidate ? selection.candidateId().toString()
                    : selection.panelId() == null ? "" : selection.panelId().toString();
            BallotOptionService.Option option = options.options(post).stream()
                    .filter(o -> o.type().equals(type) && o.id().equals(id)).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "The selected option is not on the ballot for " + post.getTitle() + "."));
            result.put(post, option);
        }
        return result;
    }

    private String selectionHash(List<VotingDtos.VoteSelection> selections) {
        List<String> lines = new ArrayList<>();
        for (VotingDtos.VoteSelection selection : selections) {
            if (selection == null || selection.postId() == null) continue;
            String type = selection.candidateId() != null ? "C" : "P";
            String id = selection.candidateId() != null ? selection.candidateId().toString()
                    : selection.panelId() == null ? "" : selection.panelId().toString();
            lines.add(selection.postId() + ":" + type + ":" + id);
        }
        lines.sort(String::compareTo);
        return CryptoUtils.sha256(String.join("|", lines));
    }

    private String sendConfirmationCode(ElectionVoter voter, String code) {
        if (voter.getEmail() == null || voter.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A verified email is required for final vote confirmation in this demo.");
        }
        email.sendVotingOtp(voter.getEmail(), code, voter.getElection().getTitle() + " (final confirmation)", confirmationMinutes);
        return mask(voter);
    }

    private String nullifier(String electionId, String postId, UUID voterId) {
        return CryptoUtils.hmacSha256(masterSecret, "nullifier:" + electionId + ":" + postId + ":" + voterId);
    }

    private String hash(String value) {
        return CryptoUtils.hmacSha256(masterSecret, value);
    }

    private String mask(ElectionVoter voter) {
        if (voter.getEmail() != null && !voter.getEmail().isBlank()) {
            String emailValue = voter.getEmail();
            int at = emailValue.indexOf('@');
            if (at > 1) {
                return emailValue.charAt(0) + "***" + emailValue.substring(at);
            }
            return "***";
        }
        if (voter.getPhone() != null && voter.getPhone().length() >= 4) {
            return "******" + voter.getPhone().substring(voter.getPhone().length() - 4);
        }
        return "******" + (voter.getAadhaarLast4() == null ? "" : voter.getAadhaarLast4());
    }

    private VotingDtos.ReceiptResponse receiptView(VoterParticipation record, String receiptCode) {
        boolean valid = CryptoUtils.hmacSha256(masterSecret, record.getReceiptHash())
                .equals(record.getReceiptSignature());
        return new VotingDtos.ReceiptResponse(record.getReceiptId().toString(), receiptCode,
                record.getElection().getId().toString(), record.getElection().getTitle(),
                record.getCastAt().toString(), record.getPostCount(), record.getReceiptHash(),
                record.getReceiptSignature(), valid ? "Cryptographic signature verified" : "Signature invalid");
    }
}
