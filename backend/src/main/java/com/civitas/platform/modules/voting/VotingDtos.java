package com.civitas.platform.modules.voting;

import com.civitas.platform.modules.elections.ElectionDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class VotingDtos {
    private VotingDtos() {
    }

    public record RequestOtpRequest(@NotNull UUID electionId, @NotNull VotingAuthMethod method, String identifier) {
    }

    public record VerifyOtpRequest(@NotNull UUID electionId, @NotNull UUID challengeId, @NotBlank String code) {
    }

    public record BiometricRequest(@NotNull UUID electionId) {
    }

    public record VoteSelection(@NotNull UUID postId, UUID candidateId, UUID panelId) {
    }

    public record ConfirmRequest(@NotEmpty List<VoteSelection> selections) {
    }

    public record CastRequest(@NotEmpty List<VoteSelection> selections, @NotBlank String confirmationCode) {
    }

    public record EligibleElectionResponse(String electionId, String title, String society, String description,
                                           Instant startsAt, Instant endsAt, String status, boolean voted,
                                           long posts) {
    }

    public record OtpChallengeResponse(String challengeId, String electionId, String method, String maskedDestination,
                                       String expiresAt, String developmentCode) {
    }

    public record SessionResponse(String token, String electionId, String electionTitle, String voterName,
                                  String method, String authenticatedAt, String expiresAt, long elapsedSeconds) {
    }

    public record ConfirmationResponse(boolean confirmationRequired, String maskedDestination, String expiresAt,
                                       String developmentCode) {
    }

    public record ReceiptResponse(String receiptId, String receiptCode, String electionId, String electionTitle,
                                  String castAt, int postsVoted, String receiptHash, String signature,
                                  String verification) {
    }

    public record BallotSessionResponse(String electionId, String electionTitle, String voterName,
                                        ElectionDtos.BallotResponse ballot) {
    }
}
