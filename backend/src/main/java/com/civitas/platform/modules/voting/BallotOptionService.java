package com.civitas.platform.modules.voting;

import com.civitas.platform.modules.elections.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class BallotOptionService {
    private final ElectionCandidateRepository candidates;
    private final PanelRepository panels;

    public BallotOptionService(ElectionCandidateRepository candidates, PanelRepository panels) {
        this.candidates = candidates;
        this.panels = panels;
    }

    public List<Option> options(ElectionPost post) {
        List<Option> options = new ArrayList<>();
        int index = 1;
        for (ElectionCandidate candidate : candidates.findByPostIdOrderBySortOrderAscCreatedAtAsc(post.getId())) {
            if (candidate.getStatus() == CandidateStatus.APPROVED) {
                options.add(new Option("CANDIDATE", candidate.getId().toString(), index++, candidate.getId().toString()));
            }
        }
        for (Panel panel : panels.findByElectionIdOrderByCreatedAtAsc(post.getElection().getId())) {
            ElectionCandidate member = candidates.findByPanelId(panel.getId()).stream()
                    .filter(c -> c.getPost().getId().equals(post.getId()) && c.getStatus() == CandidateStatus.APPROVED)
                    .findFirst().orElse(null);
            if (member != null) {
                options.add(new Option("PANEL", panel.getId().toString(), index++, member.getId().toString()));
            }
        }
        return options;
    }

    public record Option(String type, String id, int index, String candidateId) {
    }
}
