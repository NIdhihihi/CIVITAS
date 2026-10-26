package com.civitas.platform.modules.election;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/elections")
public class ElectionController {
    private final ElectionService service;

    public ElectionController(ElectionService service) {
        this.service = service;
    }

    @PostMapping
    public ElectionDtos.ElectionResponse createElection(
            @Valid @RequestBody ElectionDtos.CreateElectionRequest req,
            Authentication auth) {
        return service.createElection(auth.getName(), req);
    }

    @GetMapping("/tenant/{tenantId}")
    public List<ElectionDtos.ElectionResponse> listByTenant(@PathVariable UUID tenantId) {
        return service.listByTenant(tenantId);
    }

    @GetMapping("/{id}")
    public ElectionDtos.ElectionDetailResponse getElectionDetail(@PathVariable UUID id) {
        return service.getElectionDetail(id);
    }

    @PutMapping("/{id}")
    public ElectionDtos.ElectionResponse updateElection(
            @PathVariable UUID id,
            @Valid @RequestBody ElectionDtos.UpdateElectionRequest req) {
        return service.updateElection(id, req);
    }

    @PatchMapping("/{id}/status")
    public ElectionDtos.ElectionResponse changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ElectionDtos.ChangeStatusRequest req) {
        return service.changeStatus(id, req.status());
    }

    @PostMapping("/{id}/posts")
    public ElectionDtos.PostResponse addPost(
            @PathVariable UUID id,
            @Valid @RequestBody ElectionDtos.CreatePostRequest req) {
        return service.addPost(id, req);
    }

    @PostMapping("/{id}/panels")
    public ElectionDtos.PanelResponse addPanel(
            @PathVariable UUID id,
            @Valid @RequestBody ElectionDtos.CreatePanelRequest req) {
        return service.addPanel(id, req);
    }

    @PostMapping("/{id}/candidates")
    public ElectionDtos.CandidateResponse nominateCandidate(
            @PathVariable UUID id,
            @Valid @RequestBody ElectionDtos.NominateCandidateRequest req) {
        return service.nominateCandidate(id, req);
    }

    @PatchMapping("/candidates/{candidateId}/status")
    public ElectionDtos.CandidateResponse updateCandidateStatus(
            @PathVariable UUID candidateId,
            @Valid @RequestBody ElectionDtos.UpdateCandidateStatusRequest req) {
        return service.updateCandidateStatus(candidateId, req.status());
    }

    @PostMapping("/{id}/voter-roll/upload")
    public ElectionDtos.VoterRollUploadResult uploadVoterRoll(
            @PathVariable UUID id,
            @Valid @RequestBody ElectionDtos.VoterRollUploadRequest req) {
        return service.uploadVoterRollCsv(id, req.csvData());
    }

    @PostMapping("/{id}/voter-roll")
    public ElectionDtos.VoterRollResponse addSingleVoter(
            @PathVariable UUID id,
            @Valid @RequestBody ElectionDtos.SingleVoterRequest req) {
        return service.addSingleVoter(id, req);
    }

    @GetMapping("/{id}/voter-roll")
    public List<ElectionDtos.VoterRollResponse> getVoterRoll(@PathVariable UUID id) {
        return service.getVoterRoll(id);
    }

    // --- Voting Endpoints (REQ-VOTE-1 to REQ-VOTE-8, REQ-5, REQ-6) ---

    @GetMapping("/{id}/ballot")
    public ElectionDtos.VoterBallotResponse getBallot(@PathVariable UUID id, Authentication auth) {
        return service.getBallotForVoter(auth.getName(), id);
    }

    @PostMapping("/{id}/vote")
    public ElectionDtos.VoteReceiptResponse castVote(
            @PathVariable UUID id,
            @Valid @RequestBody ElectionDtos.CastVoteRequest req,
            Authentication auth) {
        return service.castVote(auth.getName(), id, req);
    }

    @GetMapping("/receipts/{receiptCode}")
    public ElectionDtos.ReceiptVerifyResponse verifyReceipt(@PathVariable String receiptCode) {
        return service.verifyReceipt(receiptCode);
    }
}

