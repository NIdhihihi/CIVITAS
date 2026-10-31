package com.civitas.platform.modules.audit;

public final class AuditDtos {
    private AuditDtos() {
    }

    public record AuditEntryResponse(long sequence, String createdAt, String action, String actorType,
                                     String actorHash, String electionId, String tenantId, String resourceType,
                                     String resourceId, String ipHash, String deviceHash, String previousHash,
                                     String entryHash, String signature, String metadata) {
    }

    public record VerificationResponse(boolean valid, long entriesChecked, long headSequence, String message) {
    }

    public record FraudAlertResponse(String id, String type, String severity, String details, String electionId,
                                     String createdAt, boolean resolved, String resolvedAt) {
    }
}
