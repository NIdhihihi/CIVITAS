package com.civitas.platform.modules.results;

import com.civitas.platform.modules.elections.Election;
import com.civitas.platform.modules.elections.ElectionService;
import com.civitas.platform.modules.elections.ElectionStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

@RestController
public class ResultController {
    private final TallyService tally;
    private final ExportService exports;
    private final ElectionService elections;

    public ResultController(TallyService tally, ExportService exports, ElectionService elections) {
        this.tally = tally;
        this.exports = exports;
        this.elections = elections;
    }

    @GetMapping("/api/elections/{electionId}/results")
    public ResultDtos.ResultsResponse results(Authentication auth, @PathVariable UUID electionId) {
        Election election = elections.find(electionId);
        elections.requireAdminOrAuditor(auth.getName(), election);
        return ensureResults(election);
    }

    @PostMapping("/api/elections/{electionId}/count")
    public ResultDtos.ResultsResponse count(Authentication auth, @PathVariable UUID electionId) {
        Election election = elections.find(electionId);
        elections.requireAdmin(auth.getName(), election);
        if (election.getStatus() == ElectionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Close the election before counting.");
        }
        try {
            return tally.tally(electionId);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    @PostMapping("/api/elections/{electionId}/results/export")
    public ResponseEntity<byte[]> export(Authentication auth, @PathVariable UUID electionId,
                                         @Valid @RequestBody ExportRequest request) {
        Election election = elections.find(electionId);
        elections.requireAdminOrAuditor(auth.getName(), election);
        ResultDtos.ExportFile file = exports.export(electionId, request.format(), request.password());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .header("X-CIVITAS-SHA256", file.sha256())
                .header("X-CIVITAS-SIGNATURE", file.signature())
                .header("Access-Control-Expose-Headers", "Content-Disposition,X-CIVITAS-SHA256,X-CIVITAS-SIGNATURE")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.bytes());
    }

    public record ExportRequest(@NotBlank String format, @NotBlank @Size(min = 8, max = 200) String password) {}

    @GetMapping("/api/public/results/{electionId}")
    public ResultDtos.ResultsResponse publicResults(@PathVariable UUID electionId) {
        Election election = elections.find(electionId);
        if (election.getStatus() != ElectionStatus.COUNTED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Results are not published yet.");
        }
        return tally.results(electionId);
    }

    private ResultDtos.ResultsResponse ensureResults(Election election) {
        if (election.getStatus() == ElectionStatus.CLOSED) {
            try {
                return tally.tally(election.getId());
            } catch (IllegalStateException ex) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
            }
        }
        if (election.getStatus() != ElectionStatus.COUNTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Results are available after polling closes.");
        }
        return tally.results(election.getId());
    }
}
