package com.civitas.platform.modules.election;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    public static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    private final AuditLogRepository auditLogs;

    public AuditService(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    /**
     * Appends a new immutable block to the cryptographic audit trail (REQ-4, REQ-7).
     * Synchronized to prevent sequence collisions and ensure strict linear hash chaining.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized AuditLogEntry logEvent(UUID electionId, UUID tenantId, String eventType,
                                               String actor, String actionDetails,
                                               String ipAddress, String deviceFingerprint) {
        Optional<AuditLogEntry> latestOpt = auditLogs.findTopByOrderBySequenceNumberDesc();
        long nextSeq = latestOpt.map(e -> e.getSequenceNumber() + 1).orElse(1L);
        String previousHash = latestOpt.map(AuditLogEntry::getEntryHash).orElse(GENESIS_HASH);

        Instant now = Instant.now();
        String safeActor = (actor != null && !actor.isBlank()) ? actor.trim() : "anonymous";
        String safeIp = (ipAddress != null && !ipAddress.isBlank()) ? ipAddress.trim() : "127.0.0.1";
        String safeDevice = (deviceFingerprint != null && !deviceFingerprint.isBlank()) ? deviceFingerprint.trim() : "Platform Client";
        String safeDetails = actionDetails != null ? actionDetails.trim() : "";

        String rawContent = nextSeq + ":" + previousHash + ":" + now.toEpochMilli() + ":"
                + eventType + ":" + safeActor + ":" + safeDetails + ":" + safeIp;
        String entryHash = sha256(rawContent);

        AuditLogEntry entry = new AuditLogEntry(
                nextSeq,
                electionId,
                tenantId,
                eventType,
                safeActor,
                safeDetails,
                safeIp,
                safeDevice,
                now,
                previousHash,
                entryHash
        );

        AuditLogEntry saved = auditLogs.saveAndFlush(entry);
        log.info("Audit entry #{}: {} by {} [Hash: {}...]", nextSeq, eventType, safeActor, entryHash.substring(0, 10));

        // Anomaly / Fraud Detection (SRS Section 4.3):
        // Detect repeated rapid failed logins (e.g. 5+ failed attempts in 10 minutes)
        if ("LOGIN_FAILED".equalsIgnoreCase(eventType)) {
            checkFailedLoginSpikes(safeActor, safeIp, safeDevice, electionId, tenantId);
        }

        return saved;
    }

    private void checkFailedLoginSpikes(String actor, String ip, String device, UUID electionId, UUID tenantId) {
        Instant tenMinutesAgo = Instant.now().minus(10, ChronoUnit.MINUTES);
        long recentFails = auditLogs.countByEventTypeAndActorAndTimestampAfter("LOGIN_FAILED", actor, tenMinutesAgo);
        if (recentFails >= 5) {
            log.warn("SUSPICIOUS ACTIVITY: {} failed logins detected for actor {}", recentFails, actor);
            // Append fraud flag entry
            Optional<AuditLogEntry> latest = auditLogs.findTopByOrderBySequenceNumberDesc();
            long flagSeq = latest.map(e -> e.getSequenceNumber() + 1).orElse(2L);
            String prev = latest.map(AuditLogEntry::getEntryHash).orElse(GENESIS_HASH);
            Instant now = Instant.now();
            String details = "ALERT: " + recentFails + " rapid failed login attempts detected in 10 minutes from IP: " + ip;
            String raw = flagSeq + ":" + prev + ":" + now.toEpochMilli() + ":SUSPICIOUS_ACTIVITY_FLAGGED:" + actor + ":" + details + ":" + ip;
            AuditLogEntry flagEntry = new AuditLogEntry(
                    flagSeq, electionId, tenantId, "SUSPICIOUS_ACTIVITY_FLAGGED",
                    actor, details, ip, device, now, prev, sha256(raw)
            );
            auditLogs.saveAndFlush(flagEntry);
        }
    }

    /**
     * Verifies the mathematical integrity of the entire cryptographic hash chain.
     */
    @Transactional(readOnly = true)
    public ElectionDtos.ChainIntegrityResponse verifyChainIntegrity() {
        List<AuditLogEntry> chain = auditLogs.findAllByOrderBySequenceNumberAsc();
        if (chain.isEmpty()) {
            return new ElectionDtos.ChainIntegrityResponse(
                    true, 0, GENESIS_HASH, GENESIS_HASH, "Audit ledger is currently empty. Genesis block ready.", null
            );
        }

        String expectedPreviousHash = GENESIS_HASH;
        for (AuditLogEntry entry : chain) {
            // 1. Verify previous hash pointer
            if (!entry.getPreviousHash().equals(expectedPreviousHash)) {
                return new ElectionDtos.ChainIntegrityResponse(
                        false,
                        chain.size(),
                        chain.get(chain.size() - 1).getEntryHash(),
                        GENESIS_HASH,
                        "Tampering detected at block #" + entry.getSequenceNumber() + ": previousHash pointer mismatch.",
                        entry.getSequenceNumber()
                );
            }

            // 2. Recompute and verify current block hash
            String rawContent = entry.getSequenceNumber() + ":" + entry.getPreviousHash() + ":"
                    + entry.getTimestamp().toEpochMilli() + ":" + entry.getEventType() + ":"
                    + entry.getActor() + ":" + (entry.getActionDetails() != null ? entry.getActionDetails() : "")
                    + ":" + entry.getIpAddress();
            String recomputedHash = sha256(rawContent);

            if (!recomputedHash.equals(entry.getEntryHash())) {
                return new ElectionDtos.ChainIntegrityResponse(
                        false,
                        chain.size(),
                        chain.get(chain.size() - 1).getEntryHash(),
                        GENESIS_HASH,
                        "Tampering detected at block #" + entry.getSequenceNumber() + ": content hash recalculated differs from stored block hash.",
                        entry.getSequenceNumber()
                );
            }

            expectedPreviousHash = entry.getEntryHash();
        }

        String latestHash = chain.get(chain.size() - 1).getEntryHash();
        return new ElectionDtos.ChainIntegrityResponse(
                true,
                chain.size(),
                latestHash,
                GENESIS_HASH,
                "Cryptographic audit ledger verified. All " + chain.size() + " blocks are cryptographically sound and untampered.",
                null
        );
    }

    @Transactional(readOnly = true)
    public List<ElectionDtos.AuditLogResponse> getAllLogs() {
        return auditLogs.findAllByOrderBySequenceNumberDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ElectionDtos.AuditLogResponse> getElectionLogs(UUID electionId) {
        return auditLogs.findByElectionIdOrderBySequenceNumberAsc(electionId).stream()
                .map(this::toResponse)
                .toList();
    }

    public ElectionDtos.AuditLogResponse toResponse(AuditLogEntry e) {
        return new ElectionDtos.AuditLogResponse(
                e.getSequenceNumber(),
                e.getElectionId() != null ? e.getElectionId().toString() : null,
                e.getTenantId() != null ? e.getTenantId().toString() : null,
                e.getEventType(),
                e.getActor(),
                e.getActionDetails(),
                e.getIpAddress(),
                e.getDeviceFingerprint(),
                e.getTimestamp().toString(),
                e.getPreviousHash(),
                e.getEntryHash()
        );
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm error", e);
        }
    }
}
