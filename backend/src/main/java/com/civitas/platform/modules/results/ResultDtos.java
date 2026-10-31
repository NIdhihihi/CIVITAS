package com.civitas.platform.modules.results;

import java.util.List;

public final class ResultDtos {
    private ResultDtos() {
    }

    public record ContestantResult(String id, String name, String photoUrl, long votes, double percentage) {
    }

    public record PostResult(String postId, String title, long totalVotes,
                             List<ContestantResult> candidates, List<ContestantResult> panels) {
    }

    public record ResultsResponse(String electionId, String title, String society, String tenantId, String status,
                                  String countedAt, long totalBallots, long eligibleVoters, List<PostResult> posts,
                                  String resultHash, String resultSignature, boolean auditChainValid) {
    }

    public record ExportFile(String filename, String contentType, byte[] bytes, String sha256, String signature) {
    }
}
