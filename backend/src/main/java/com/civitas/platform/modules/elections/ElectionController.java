package com.civitas.platform.modules.elections;

import com.civitas.platform.modules.notifications.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/elections")
public class ElectionController {
    private final ElectionService elections;
    private final NotificationService notifications;

    public ElectionController(ElectionService elections, NotificationService notifications) {
        this.elections = elections;
        this.notifications = notifications;
    }

    @GetMapping("/mine")
    public List<ElectionDtos.ElectionResponse> mine(Authentication auth) {
        return elections.mine(auth.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ElectionDtos.ElectionResponse create(Authentication auth,
                                                @Valid @RequestBody ElectionDtos.CreateElectionRequest request) {
        return elections.create(auth.getName(), request);
    }

    @GetMapping("/{electionId}")
    public ElectionDtos.ElectionResponse get(Authentication auth, @PathVariable UUID electionId) {
        return elections.get(auth.getName(), electionId);
    }

    @PatchMapping("/{electionId}")
    public ElectionDtos.ElectionResponse update(Authentication auth, @PathVariable UUID electionId,
                                                @Valid @RequestBody ElectionDtos.UpdateElectionRequest request) {
        return elections.update(auth.getName(), electionId, request);
    }

    @PatchMapping("/{electionId}/status")
    public ElectionDtos.ElectionResponse status(Authentication auth, @PathVariable UUID electionId,
                                                @Valid @RequestBody ElectionDtos.StatusRequest request) {
        return elections.transition(auth.getName(), electionId, request.status());
    }

    @PostMapping("/{electionId}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ElectionDtos.PostResponse addPost(Authentication auth, @PathVariable UUID electionId,
                                             @Valid @RequestBody ElectionDtos.CreatePostRequest request) {
        return elections.addPost(auth.getName(), electionId, request);
    }

    @GetMapping("/{electionId}/posts")
    public List<ElectionDtos.PostResponse> posts(Authentication auth, @PathVariable UUID electionId) {
        return elections.listPosts(auth.getName(), electionId);
    }

    @DeleteMapping("/{electionId}/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(Authentication auth, @PathVariable UUID electionId, @PathVariable UUID postId) {
        elections.deletePost(auth.getName(), electionId, postId);
    }

    @PostMapping("/{electionId}/panels")
    @ResponseStatus(HttpStatus.CREATED)
    public ElectionDtos.PanelResponse addPanel(Authentication auth, @PathVariable UUID electionId,
                                               @Valid @RequestBody ElectionDtos.CreatePanelRequest request) {
        return elections.addPanel(auth.getName(), electionId, request);
    }

    @GetMapping("/{electionId}/panels")
    public List<ElectionDtos.PanelResponse> panels(Authentication auth, @PathVariable UUID electionId) {
        return elections.listPanels(auth.getName(), electionId);
    }

    @DeleteMapping("/{electionId}/panels/{panelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePanel(Authentication auth, @PathVariable UUID electionId, @PathVariable UUID panelId) {
        elections.deletePanel(auth.getName(), electionId, panelId);
    }

    @PostMapping("/{electionId}/candidates")
    @ResponseStatus(HttpStatus.CREATED)
    public ElectionDtos.CandidateResponse addCandidate(Authentication auth, @PathVariable UUID electionId,
                                                       @Valid @RequestBody ElectionDtos.CreateCandidateRequest request) {
        return elections.addCandidate(auth.getName(), electionId, request);
    }

    @GetMapping("/{electionId}/candidates")
    public List<ElectionDtos.CandidateResponse> candidates(Authentication auth, @PathVariable UUID electionId) {
        return elections.listCandidates(auth.getName(), electionId);
    }

    @PatchMapping("/{electionId}/candidates/{candidateId}")
    public ElectionDtos.CandidateResponse updateCandidate(Authentication auth, @PathVariable UUID electionId,
                                                          @PathVariable UUID candidateId,
                                                          @Valid @RequestBody ElectionDtos.UpdateCandidateRequest request) {
        return elections.updateCandidate(auth.getName(), electionId, candidateId, request);
    }

    @DeleteMapping("/{electionId}/candidates/{candidateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCandidate(Authentication auth, @PathVariable UUID electionId, @PathVariable UUID candidateId) {
        elections.deleteCandidate(auth.getName(), electionId, candidateId);
    }

    @PostMapping("/{electionId}/voter-roll")
    public ElectionDtos.VoterRollImportResponse importVoterRoll(Authentication auth, @PathVariable UUID electionId,
                                                                @Valid @RequestBody ElectionDtos.VoterRollRequest request) {
        return elections.importVoterRoll(auth.getName(), electionId, request);
    }

    @GetMapping("/{electionId}/voter-roll")
    public List<ElectionDtos.VoterResponse> voterRoll(Authentication auth, @PathVariable UUID electionId) {
        return elections.listVoters(auth.getName(), electionId);
    }

    @DeleteMapping("/{electionId}/voter-roll/{voterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVoter(Authentication auth, @PathVariable UUID electionId, @PathVariable UUID voterId) {
        elections.deleteVoter(auth.getName(), electionId, voterId);
    }

    @GetMapping("/{electionId}/ballot-preview")
    public ElectionDtos.BallotResponse preview(Authentication auth, @PathVariable UUID electionId) {
        return elections.previewBallot(auth.getName(), electionId);
    }

    @PostMapping("/{electionId}/communications")
    @ResponseStatus(HttpStatus.CREATED)
    public ElectionDtos.CommunicationResponse scheduleCommunication(Authentication auth, @PathVariable UUID electionId,
                                                                    @Valid @RequestBody ElectionDtos.CommunicationRequest request) {
        Election election = elections.find(electionId);
        elections.requireAdmin(auth.getName(), election);
        return notifications.schedule(auth.getName(), election, request);
    }

    @GetMapping("/{electionId}/communications")
    public List<ElectionDtos.CommunicationResponse> communications(Authentication auth, @PathVariable UUID electionId) {
        Election election = elections.find(electionId);
        elections.requireAdminOrAuditor(auth.getName(), election);
        return notifications.list(electionId);
    }
}
