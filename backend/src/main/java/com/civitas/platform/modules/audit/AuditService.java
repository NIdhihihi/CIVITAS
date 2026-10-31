package com.civitas.platform.modules.audit;

import com.civitas.platform.common.CryptoUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    public static final String GENESIS = "0".repeat(64);

    private final AuditEntryRepository ledger;
    private final FraudAlertRepository alerts;
    private final ObjectMapper mapper;
    private final String secret;

    public AuditService(AuditEntryRepository ledger, FraudAlertRepository alerts, ObjectMapper mapper,
                        @Value("${civitas.security.master-secret}") String secret) {
        this.ledger = ledger;
        this.alerts = alerts;
        this.mapper = mapper;
        this.secret = secret;
    }

    @Transactional
    public synchronized AuditEntry record(String action, String electionId, String tenantId,
                                          String resourceType, String resourceId,
                                          Map<String, ?> metadata) {
        return record(action, null, null, electionId, tenantId, resourceType, resourceId, metadata);
    }

    @Transactional
    public synchronized AuditEntry record(String action, String actorType, String actorIdentity,
                                          String electionId, String tenantId,
                                          String resourceType, String resourceId,
                                          Map<String, ?> metadata) {
        Client client = client();
        String resolvedActorType = actorType == null ? currentActorType() : actorType;
        String identity = actorIdentity;
        if (identity == null) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            identity = authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getName())
                    ? authentication.getName() : "anonymous";
        }
        String actorHash = CryptoUtils.hmacSha256(secret, resolvedActorType + ":" + identity);
        String metadataJson = json(metadata);
        Optional<AuditEntry> head = ledger.findTopByOrderBySequenceDesc();
        long sequence = head.map(e -> e.getSequence() + 1).orElse(1L);
        String previousHash = head.map(AuditEntry::getEntryHash).orElse(GENESIS);
        // Truncate to the precision persisted by PostgreSQL/H2 so verification can reproduce the hash.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String canonical = canonical(sequence, electionId, tenantId, action, resolvedActorType, actorHash,
                resourceType, resourceId, client.ipHash(), client.deviceHash(), metadataJson, previousHash, now);
        String entryHash = CryptoUtils.sha256(canonical);
        String signature = CryptoUtils.hmacSha256(secret, entryHash);
        AuditEntry entry = new AuditEntry(tenantId, electionId, action, resolvedActorType, actorHash,
                resourceType, resourceId, client.ipHash(), client.deviceHash(), metadataJson,
                previousHash, entryHash, signature, now);
        entry.setSequence(sequence);
        ledger.saveAndFlush(entry);
        inspectFraud(entry, client);
        return entry;
    }

    /** Never fails the calling operation; used for failed-authentication events. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordQuietly(String action, String actorIdentity, String electionId, Map<String, ?> metadata) {
        try {
            record(action, "ANONYMOUS", actorIdentity, electionId, null, "AUTH", null, metadata);
        } catch (RuntimeException ex) {
            log.error("Could not append audit entry for {}", action, ex);
        }
    }

    @Transactional(readOnly = true)
    public List<AuditDtos.AuditEntryResponse> page(String electionId, boolean privileged, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        if (!privileged && (electionId == null || electionId.isBlank())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Select a specific election before reading a society-scoped audit trail.");
        }
        List<AuditEntry> entries = electionId == null || electionId.isBlank()
                ? ledger.findAllByOrderBySequenceDesc(PageRequest.of(0, safeLimit))
                : ledger.findByElectionIdOrderBySequenceDesc(electionId, PageRequest.of(0, safeLimit));
        return entries.stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public AuditDtos.VerificationResponse verify(String electionId) {
        // Always verify the complete global hash chain, even when the caller asks for one election.
        // Filtering before verification would miss deleted, inserted, or re-linked entries.
        List<AuditEntry> entries = ledger.findAllByOrderBySequenceAsc();
        boolean scoped = electionId != null && !electionId.isBlank();
        String previous = GENESIS;
        long checked = 0;
        long scopedEntries = 0;
        long scopedHead = 0;
        for (AuditEntry entry : entries) {
            if (!previous.equals(entry.getPreviousHash())) {
                return new AuditDtos.VerificationResponse(false, checked, entry.getSequence(),
                        "Previous-hash link is broken at entry " + entry.getSequence() + ".");
            }
            String canonical = canonical(entry.getSequence(), entry.getElectionId(), entry.getTenantId(),
                    entry.getAction(), entry.getActorType(), entry.getActorHash(), entry.getResourceType(),
                    entry.getResourceId(), entry.getIpHash(), entry.getDeviceHash(), entry.getMetadata(),
                    entry.getPreviousHash(), entry.getCreatedAt());
            String expected = CryptoUtils.sha256(canonical);
            if (!expected.equals(entry.getEntryHash())) {
                return new AuditDtos.VerificationResponse(false, checked, entry.getSequence(),
                        "Entry hash mismatch at sequence " + entry.getSequence() + ".");
            }
            String expectedSignature = CryptoUtils.hmacSha256(secret, expected);
            if (!expectedSignature.equals(entry.getSignature())) {
                return new AuditDtos.VerificationResponse(false, checked, entry.getSequence(),
                        "Signature mismatch at sequence " + entry.getSequence() + ".");
            }
            previous = entry.getEntryHash();
            checked++;
            if (!scoped || electionId.equals(entry.getElectionId())) {
                scopedEntries++;
                scopedHead = entry.getSequence();
            }
        }
        long head = scoped ? scopedHead : (checked == 0 ? 0 : entries.get(entries.size() - 1).getSequence());
        String message = scoped
                ? "Audit chain is intact across " + checked + " ledger entries (" + scopedEntries + " for this election)."
                : "Audit chain is intact across " + checked + " ledger entries.";
        return new AuditDtos.VerificationResponse(true, checked, head, message);
    }

    @Transactional(readOnly = true)
    public String exportCsv(String electionId) {
        List<AuditEntry> entries = electionId == null || electionId.isBlank()
                ? ledger.findAllByOrderBySequenceAsc()
                : ledger.findByElectionIdOrderBySequenceAsc(electionId);
        StringBuilder sb = new StringBuilder();
        sb.append("sequence,createdAt,action,actorType,actorHash,electionId,tenantId,resourceType,resourceId,ipHash,deviceHash,previousHash,entryHash,signature,metadata\r\n");
        for (AuditEntry e : entries) {
            sb.append(csv(e.getSequence())).append(',').append(csv(e.getCreatedAt())).append(',')
                    .append(csv(e.getAction())).append(',').append(csv(e.getActorType())).append(',')
                    .append(csv(e.getActorHash())).append(',').append(csv(e.getElectionId())).append(',')
                    .append(csv(e.getTenantId())).append(',').append(csv(e.getResourceType())).append(',')
                    .append(csv(e.getResourceId())).append(',').append(csv(e.getIpHash())).append(',')
                    .append(csv(e.getDeviceHash())).append(',').append(csv(e.getPreviousHash())).append(',')
                    .append(csv(e.getEntryHash())).append(',').append(csv(e.getSignature())).append(',')
                    .append(csv(e.getMetadata())).append("\r\n");
        }
        AuditDtos.VerificationResponse verification = verify(electionId);
        sb.append("\r\n# CIVITAS audit export\r\n");
        sb.append("# entries=").append(entries.size()).append("\r\n");
        sb.append("# chainValid=").append(verification.valid()).append("\r\n");
        sb.append("# chainHead=").append(entries.isEmpty() ? GENESIS : entries.get(entries.size() - 1).getEntryHash()).append("\r\n");
        sb.append("# exportChecksum=").append(CryptoUtils.sha256(sb.toString()));
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public List<AuditDtos.FraudAlertResponse> alerts() {
        return alerts.findAllByOrderByCreatedAtDesc().stream().map(a ->
                new AuditDtos.FraudAlertResponse(a.getId().toString(), a.getType(), a.getSeverity(),
                        a.getDetails(), a.getElectionId(), a.getCreatedAt().toString(),
                        a.isResolved(), a.getResolvedAt() == null ? null : a.getResolvedAt().toString())).toList();
    }

    @Transactional
    public AuditDtos.FraudAlertResponse resolveAlert(String id) {
        FraudAlert alert = alerts.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fraud alert not found."));
        alert.resolve();
        alerts.save(alert);
        return new AuditDtos.FraudAlertResponse(alert.getId().toString(), alert.getType(), alert.getSeverity(),
                alert.getDetails(), alert.getElectionId(), alert.getCreatedAt().toString(), true,
                alert.getResolvedAt().toString());
    }

    public Client client() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attrs == null ? null : attrs.getRequest();
        String ip = "unknown";
        String device = "unknown";
        if (request != null) {
            String forwarded = request.getHeader("X-Forwarded-For");
            ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
            String fingerprint = request.getHeader("X-Device-Fingerprint");
            device = fingerprint != null && !fingerprint.isBlank() ? fingerprint : String.valueOf(request.getHeader("User-Agent"));
        }
        return new Client(ip, device,
                CryptoUtils.hmacSha256(secret, "ip:" + ip),
                CryptoUtils.hmacSha256(secret, "device:" + device));
    }

    private void inspectFraud(AuditEntry entry, Client client) {
        try {
            if ("LOGIN_FAILED".equals(entry.getAction()) || "VOTING_OTP_FAILED".equals(entry.getAction())) {
                Instant window = Instant.now().minus(15, ChronoUnit.MINUTES);
                long failures = ledger.countByActionAndIpHashAndCreatedAtAfter(entry.getAction(), entry.getIpHash(), window);
                if (failures >= 5 && !alerts.existsByTypeAndIpHashAndCreatedAtAfter(entry.getAction() + "_BURST",
                        entry.getIpHash(), Instant.now().minus(1, ChronoUnit.HOURS))) {
                    alerts.save(new FraudAlert(entry.getAction() + "_BURST", "HIGH",
                            failures + " failed attempts from the same network in 15 minutes.",
                            entry.getElectionId(), entry.getIpHash(), entry.getDeviceHash()));
                }
            }
            if ("LOGIN_SUCCESS".equals(entry.getAction())) {
                Optional<AuditEntry> previous = ledger.findTopByActorHashAndActionAndIpHashNotOrderByCreatedAtDesc(
                        entry.getActorHash(), "LOGIN_SUCCESS", entry.getIpHash());
                if (previous.isPresent() && previous.get().getCreatedAt().isAfter(Instant.now().minus(10, ChronoUnit.MINUTES))
                        && !alerts.existsByTypeAndIpHashAndCreatedAtAfter("IMPOSSIBLE_TRAVEL", entry.getIpHash(),
                        Instant.now().minus(1, ChronoUnit.HOURS))) {
                    alerts.save(new FraudAlert("IMPOSSIBLE_TRAVEL", "HIGH",
                            "A successful sign-in from a different network within ten minutes of the previous sign-in.",
                            null, entry.getIpHash(), entry.getDeviceHash()));
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Fraud inspection failed for audit entry {}", entry.getSequence(), ex);
        }
    }

    private String currentActorType() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return "ANONYMOUS";
        }
        return authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .filter(r -> r.contains("ADMIN") || r.equals("AUDITOR"))
                .findFirst().orElse("USER");
    }

    private AuditDtos.AuditEntryResponse view(AuditEntry e) {
        return new AuditDtos.AuditEntryResponse(e.getSequence(), e.getCreatedAt().toString(), e.getAction(),
                e.getActorType(), mask(e.getActorHash()), e.getElectionId(), e.getTenantId(),
                e.getResourceType(), e.getResourceId(), e.getIpHash(), e.getDeviceHash(),
                e.getPreviousHash(), e.getEntryHash(), e.getSignature(), e.getMetadata());
    }

    private String mask(String hash) {
        return hash == null ? null : hash.substring(0, Math.min(12, hash.length())) + "…";
    }

    private String canonical(long sequence, String electionId, String tenantId, String action,
                             String actorType, String actorHash, String resourceType, String resourceId,
                             String ipHash, String deviceHash, String metadata, String previousHash, Instant createdAt) {
        return sequence + "|" + n(electionId) + "|" + n(tenantId) + "|" + action + "|" + actorType + "|"
                + actorHash + "|" + n(resourceType) + "|" + n(resourceId) + "|" + ipHash + "|" + deviceHash
                + "|" + metadata + "|" + previousHash + "|" + createdAt;
    }

    private String json(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            String value = mapper.writeValueAsString(new LinkedHashMap<>(metadata));
            return value.length() > 3900 ? value.substring(0, 3900) : value;
        } catch (JsonProcessingException e) {
            return "{\"note\":\"metadata serialization failed\"}";
        }
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString().replace("\"", "\"\"");
        return "\"" + text + "\"";
    }

    private String n(String value) {
        return value == null ? "" : value;
    }

    public record Client(String ip, String device, String ipHash, String deviceHash) {
    }
}
