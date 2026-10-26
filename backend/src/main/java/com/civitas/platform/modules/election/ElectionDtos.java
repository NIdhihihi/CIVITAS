package com.civitas.platform.modules.election;

import com.civitas.platform.modules.onboarding.CandidateStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ElectionDtos {
    private ElectionDtos() {}

    public record CreateElectionRequest(
        @NotBlank String tenantId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        String votingStartTime,
        String votingEndTime
    ) {}

    public record UpdateElectionRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        String votingStartTime,
        String votingEndTime
    ) {}

    public record ChangeStatusRequest(
        @NotNull ElectionStatus status
    ) {}

    public record CreatePostRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 500) String description,
        int maxSelections,
        int displayOrder
    ) {}

    public record CreatePanelRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String motto,
        String colorHex
    ) {}

    public record NominateCandidateRequest(
        @NotBlank String postId,
        String panelId,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank String email,
        String phone,
        String statement,
        String photoUrl
    ) {}

    public record UpdateCandidateStatusRequest(
        @NotNull CandidateStatus status
    ) {}

    public record SingleVoterRequest(
        @NotBlank String flatNumber,
        @NotBlank String fullName,
        @NotBlank String email,
        String phoneNumber,
        String aadhaarReference,
        Boolean eligible,
        String disqualificationReason
    ) {}

    public record VoterRollUploadRequest(
        @NotBlank String csvData
    ) {}

    public record VoterRollUploadResult(
        int totalRowsParsed,
        int addedCount,
        int duplicateFlatsCount,
        int duplicateEmailsCount,
        List<String> errors
    ) {}

    public record PostResponse(
        String id,
        String electionId,
        String title,
        String description,
        int maxSelections,
        int displayOrder,
        List<CandidateResponse> candidates
    ) {}

    public record PanelResponse(
        String id,
        String electionId,
        String name,
        String motto,
        String colorHex
    ) {}

    public record CandidateResponse(
        String id,
        String electionId,
        String postId,
        String postTitle,
        String panelId,
        String panelName,
        String fullName,
        String email,
        String phone,
        String statement,
        String photoUrl,
        String status,
        String registeredAt
    ) {}

    public record VoterRollResponse(
        String id,
        String electionId,
        String flatNumber,
        String fullName,
        String email,
        String phoneNumber,
        String aadhaarReference,
        boolean eligible,
        String disqualificationReason,
        boolean hasVoted,
        String votedAt
    ) {}

    public record ElectionResponse(
        String id,
        String tenantId,
        String tenantName,
        String title,
        String description,
        String status,
        String votingStartTime,
        String votingEndTime,
        String createdByEmail,
        String createdAt,
        long totalVoters,
        long eligibleVoters,
        long votesCast
    ) {}

    public record ElectionDetailResponse(
        ElectionResponse election,
        List<PostResponse> posts,
        List<PanelResponse> panels,
        List<VoterRollResponse> voterRoll
    ) {}

    public record PostSelection(
        @NotBlank String postId,
        @NotNull List<String> candidateIds
    ) {}

    public record CastVoteRequest(
        String panelId,
        List<PostSelection> selections,
        boolean dualFactorConfirmed
    ) {}

    public record VoteReceiptResponse(
        String receiptCode,
        String ballotHash,
        String electionId,
        String electionTitle,
        String societyName,
        String castAt,
        String message
    ) {}

    public record ReceiptVerifyResponse(
        boolean verified,
        String receiptCode,
        String ballotHash,
        String electionTitle,
        String societyName,
        String castAt,
        boolean isPanelVote,
        String panelName
    ) {}

    public record VoterBallotResponse(
        String electionId,
        String title,
        String societyName,
        String status,
        boolean isEligible,
        boolean hasVoted,
        String disqualificationReason,
        List<PostResponse> posts,
        List<PanelResponse> panels
    ) {}

    public record AuditLogResponse(
        long sequenceNumber,
        String electionId,
        String tenantId,
        String eventType,
        String actor,
        String actionDetails,
        String ipAddress,
        String deviceFingerprint,
        String timestamp,
        String previousHash,
        String entryHash
    ) {}

    public record ChainIntegrityResponse(
        boolean valid,
        long totalBlocks,
        String latestHash,
        String genesisHash,
        String message,
        Long tamperedAtSequence
    ) {}

    public record CandidateResult(
        String candidateId,
        String candidateName,
        String email,
        String panelId,
        String panelName,
        String panelColorHex,
        long voteCount,
        double votePercentage,
        String outcome,
        int rank,
        boolean isTieBreakerWinner
    ) {}

    public record PostResult(
        String postId,
        String postTitle,
        int maxSelections,
        long totalVotesForPost,
        boolean hasTie,
        String tieNotes,
        String resolvedWinningCandidateId,
        String tieResolutionMethod,
        List<CandidateResult> candidateResults
    ) {}

    public record PanelResult(
        String panelId,
        String panelName,
        String colorHex,
        long straightTicketVotes,
        long totalCandidateVotes
    ) {}

    public record ElectionResultsResponse(
        String electionId,
        String electionTitle,
        String societyName,
        String status,
        long totalEligibleVoters,
        long totalVotesCast,
        double turnoutPercentage,
        long straightTicketPanelVotes,
        long splitTicketVotes,
        boolean hasUnresolvedTies,
        List<PostResult> postResults,
        List<PanelResult> panelResults,
        String certifiedAt,
        String ledgerHeadHash,
        boolean chainIntegrityVerified
    ) {}

    public record ResolveTieRequest(
        @NotBlank String postId,
        @NotBlank String winningCandidateId,
        @NotBlank String resolutionMethod,
        String resolutionNotes
    ) {}
}


