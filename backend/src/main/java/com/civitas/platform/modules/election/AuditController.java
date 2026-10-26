package com.civitas.platform.modules.election;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/logs")
    public List<ElectionDtos.AuditLogResponse> getAllLogs() {
        return auditService.getAllLogs();
    }

    @GetMapping("/election/{electionId}")
    public List<ElectionDtos.AuditLogResponse> getElectionLogs(@PathVariable UUID electionId) {
        return auditService.getElectionLogs(electionId);
    }

    @GetMapping("/verify")
    public ElectionDtos.ChainIntegrityResponse verifyIntegrity() {
        return auditService.verifyChainIntegrity();
    }
}
