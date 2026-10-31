package com.civitas.platform.modules.elections;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Unauthenticated entry points used by universal voting links and the public results page.
 * Everything here exposes only information that is safe for an anonymous visitor; identity,
 * eligibility, and duplicate-vote checks still happen in the voting flow.
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {
    private final ElectionService elections;

    public PublicController(ElectionService elections) {
        this.elections = elections;
    }

    @GetMapping("/elections")
    public List<ElectionDtos.PublicElectionResponse> activeElections() {
        return elections.publicActive();
    }

    @GetMapping("/elections/{electionId}")
    public ElectionDtos.PublicElectionDetailResponse election(@PathVariable UUID electionId) {
        return elections.publicDetail(electionId);
    }
}
