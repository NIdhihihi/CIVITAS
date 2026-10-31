package com.civitas.platform.modules.audit;

import com.civitas.platform.common.CryptoUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.civitas.platform.modules.elections.ElectionService;
import java.util.UUID;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService audit;
    private final ElectionService elections;

    public AuditController(AuditService audit, ElectionService elections) {
        this.audit = audit;
        this.elections = elections;
    }

    @GetMapping
    public List<AuditDtos.AuditEntryResponse> page(Authentication auth,
                                                  @RequestParam(required = false) String electionId,
                                                  @RequestParam(defaultValue = "200") int limit) {
        requireAuditAccess(auth, electionId);
        return audit.page(electionId, isPlatformOrAuditor(auth), limit);
    }

    @GetMapping("/verify")
    public AuditDtos.VerificationResponse verify(Authentication auth,
                                                @RequestParam(required = false) String electionId) {
        requireAuditAccess(auth, electionId);
        return audit.verify(electionId);
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(Authentication auth,
                                         @RequestParam(required = false) String electionId) {
        requireAuditAccess(auth, electionId);
        String csv = audit.exportCsv(electionId);
        audit.record("AUDIT_EXPORT", electionId, null, "AUDIT_LEDGER", null,
                Map.of("format", "CSV", "electionId", electionId == null ? "all" : electionId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"civitas-audit-ledger.csv\"")
                .header("X-CIVITAS-SHA256", CryptoUtils.sha256(csv))
                .contentType(MediaType.TEXT_PLAIN)
                .body(csv);
    }

    @GetMapping("/alerts")
    public List<AuditDtos.FraudAlertResponse> alerts(Authentication auth) {
        requirePlatformOrAuditor(auth);
        return audit.alerts();
    }

    @PatchMapping("/alerts/{id}/resolve")
    public AuditDtos.FraudAlertResponse resolve(Authentication auth, @PathVariable String id) {
        requirePlatformOrAuditor(auth);
        AuditDtos.FraudAlertResponse response = audit.resolveAlert(id);
        audit.record("FRAUD_ALERT_RESOLVED", null, null, "FRAUD_ALERT", id, Map.of());
        return response;
    }

    private void requireAuditAccess(Authentication auth, String electionId) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to read the audit ledger.");
        }
        boolean allowed = auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_PLATFORM_ADMIN")
                        || a.getAuthority().equals("ROLE_AUDITOR")
                        || a.getAuthority().equals("ROLE_TENANT_ADMIN"));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Audit access requires an administrator or auditor role.");
        }
        boolean global = auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_PLATFORM_ADMIN") || a.getAuthority().equals("ROLE_AUDITOR"));
        if (!global && (electionId == null || electionId.isBlank())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Society administrators must select an election to view its audit trail.");
        }
        if (!global) {
            try {
                elections.requireAdminOrAuditor(auth.getName(), elections.find(UUID.fromString(electionId)));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "electionId must be a valid UUID.");
            }
        }
    }

    private boolean isPlatformOrAuditor(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_PLATFORM_ADMIN") || a.getAuthority().equals("ROLE_AUDITOR"));
    }

    private void requirePlatformOrAuditor(Authentication auth) {
        if (auth == null || !isPlatformOrAuditor(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Fraud alerts are restricted to platform administrators and auditors.");
        }
    }
}
