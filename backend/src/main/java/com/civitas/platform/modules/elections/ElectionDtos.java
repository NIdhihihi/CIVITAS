package com.civitas.platform.modules.elections;

import com.civitas.platform.modules.notifications.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ElectionDtos {
    private ElectionDtos() {
    }

    public record CreateElectionRequest(
            @NotNull UUID tenantId,
            @NotBlank @Size(max = 180) String title,
            @Size(max = 1600) String description,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            Boolean notifyVoters) {
    }

    public record UpdateElectionRequest(
            @Size(max = 180) String title,
            @Size(max = 1600) String description,
            Instant startsAt,
            Instant endsAt,
            Boolean notifyVoters) {
    }

    public record StatusRequest(@NotNull ElectionStatus status) {
    }

    public record CreatePostRequest(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 600) String description,
            VotingMode votingMode,
            Integer sortOrder) {
    }

    public record CreatePanelRequest(
            @NotBlank @Size(max = 140) String name,
            @Size(max = 800) String description,
            @Size(max = 600) String photoUrl,
            String photoBase64) {
    }

    public record CreateCandidateRequest(
            @NotNull UUID postId,
            @NotBlank @Size(max = 140) String name,
            @Size(max = 1200) String statement,
            @Size(max = 600) String photoUrl,
            String photoBase64,
            UUID panelId,
            UUID memberId,
            Integer sortOrder) {
    }

    public record UpdateCandidateRequest(
            @Size(max = 140) String name,
            @Size(max = 1200) String statement,
            @Size(max = 600) String photoUrl,
            String photoBase64,
            UUID panelId,
            Boolean removePanel,
            CandidateStatus status,
            Integer sortOrder) {
    }

    public record VoterRollRequest(@NotBlank String csv, Boolean replace) {
    }

    public record VoterImportError(int row, String field, String message) {
    }

    public record VoterRollImportResponse(int imported, int skipped, List<VoterImportError> errors, long totalVoters) {
    }

    public record VoterResponse(String id, String fullName, String email, String phone, String aadhaarLast4,
                                String eligiblePostIds, boolean voted, String votedAt, String createdAt) {
    }

    public record CommunicationRequest(
            @NotNull NotificationChannel channel,
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 2000) String template,
            Instant scheduledAt) {
    }

    public record PublicElectionResponse(String id, String title, String society, String description,
                                         Instant startsAt, Instant endsAt) {
    }

    public record PublicElectionDetailResponse(String id, String title, String society, String description,
                                               Instant startsAt, Instant endsAt, String status,
                                               long eligibleVoters, List<BallotPost> posts,
                                               List<BallotPanel> panels) {
    }

    public record CommunicationResponse(String id, String channel, String subject, String template, String status,
                                        Instant scheduledAt, Instant sentAt, int sentCount, int failedCount,
                                        boolean automated) {
    }

    public record ElectionResponse(String id, String tenantId, String tenantName, String tenantCode,
                                   String title, String description, Instant startsAt, Instant endsAt,
                                   String status, boolean notifyVoters, Instant createdAt, Instant countedAt,
                                   long posts, long candidates, long voters, long votesCast) {
    }

    public record PostResponse(String id, String title, String description, String votingMode, int sortOrder,
                               long candidateCount) {
    }

    public record CandidateResponse(String id, String postId, String postTitle, String panelId, String panelName,
                                    String name, String statement, String photoUrl, String status, int sortOrder,
                                    String memberEmail) {
    }

    public record PanelResponse(String id, String name, String description, String photoUrl,
                                List<CandidateResponse> candidates) {
    }

    public record BallotCandidate(String id, String name, String statement, String photoUrl,
                                  String panelId, String panelName) {
    }

    public record BallotPost(String id, String title, String description, String votingMode,
                             List<BallotCandidate> candidates) {
    }

    public record BallotPanelMember(String postId, String postTitle, String candidateId, String candidateName,
                                    String photoUrl) {
    }

    public record BallotPanel(String id, String name, String description, String photoUrl,
                              List<BallotPanelMember> members) {
    }

    public record BallotResponse(String electionId, String title, String description, Instant startsAt,
                                 Instant endsAt, String status, long eligibleVoters, List<BallotPost> posts,
                                 List<BallotPanel> panels) {
    }
}
