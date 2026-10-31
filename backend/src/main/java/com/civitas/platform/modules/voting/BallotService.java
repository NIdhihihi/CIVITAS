package com.civitas.platform.modules.voting;

import com.civitas.platform.modules.elections.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BallotService {
    private final ElectionPostRepository posts;
    private final ElectionCandidateRepository candidates;
    private final PanelRepository panels;
    private final ElectionVoterRepository voters;

    public BallotService(ElectionPostRepository posts, ElectionCandidateRepository candidates,
                         PanelRepository panels, ElectionVoterRepository voters) {
        this.posts = posts;
        this.candidates = candidates;
        this.panels = panels;
        this.voters = voters;
    }

    public ElectionDtos.BallotResponse assemble(Election election, ElectionVoter voter) {
        Set<UUID> eligible = voter == null ? null : eligiblePostIds(voter);
        List<ElectionPost> allPosts = posts.findByElectionIdOrderBySortOrderAscCreatedAtAsc(election.getId());
        List<ElectionDtos.BallotPost> visiblePosts = new ArrayList<>();
        Set<UUID> visibleIds = new HashSet<>();
        for (ElectionPost post : allPosts) {
            if (voter != null && !eligible.contains(post.getId())) {
                continue;
            }
            List<ElectionDtos.BallotCandidate> ballotCandidates = candidates
                    .findByPostIdOrderBySortOrderAscCreatedAtAsc(post.getId()).stream()
                    .filter(c -> c.getStatus() == CandidateStatus.APPROVED)
                    .map(c -> new ElectionDtos.BallotCandidate(c.getId().toString(), c.getDisplayName(),
                            c.getStatement(), c.getPhotoUrl(),
                            c.getPanel() == null ? null : c.getPanel().getId().toString(),
                            c.getPanel() == null ? null : c.getPanel().getName()))
                    .toList();
            visibleIds.add(post.getId());
            visiblePosts.add(new ElectionDtos.BallotPost(post.getId().toString(), post.getTitle(),
                    post.getDescription(), post.getVotingMode().name(), ballotCandidates));
        }
        List<ElectionDtos.BallotPanel> ballotPanels = new ArrayList<>();
        for (Panel panel : panels.findByElectionIdOrderByCreatedAtAsc(election.getId())) {
            List<ElectionDtos.BallotPanelMember> members = new ArrayList<>();
            for (ElectionCandidate candidate : candidates.findByPanelId(panel.getId())) {
                if (candidate.getStatus() != CandidateStatus.APPROVED) {
                    continue;
                }
                ElectionPost post = candidate.getPost();
                if (!visibleIds.contains(post.getId())) {
                    continue;
                }
                members.add(new ElectionDtos.BallotPanelMember(post.getId().toString(), post.getTitle(),
                        candidate.getId().toString(), candidate.getDisplayName(), candidate.getPhotoUrl()));
            }
            members.sort(Comparator.comparing(ElectionDtos.BallotPanelMember::postTitle));
            if (!members.isEmpty()) {
                ballotPanels.add(new ElectionDtos.BallotPanel(panel.getId().toString(), panel.getName(),
                        panel.getDescription(), panel.getPhotoUrl(), members));
            }
        }
        return new ElectionDtos.BallotResponse(election.getId().toString(), election.getTitle(),
                election.getDescription(), election.getStartsAt(), election.getEndsAt(), election.getStatus().name(),
                voters.countByElectionId(election.getId()), visiblePosts, ballotPanels);
    }

    public Set<UUID> eligiblePostIds(ElectionVoter voter) {
        Set<UUID> ids = new HashSet<>();
        if (voter.getEligiblePostIds() == null || voter.getEligiblePostIds().isBlank()) {
            posts.findByElectionIdOrderBySortOrderAscCreatedAtAsc(voter.getElection().getId())
                    .forEach(p -> ids.add(p.getId()));
            return ids;
        }
        for (String raw : voter.getEligiblePostIds().split(",")) {
            try {
                ids.add(UUID.fromString(raw.trim()));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed eligibility rows; validation happens on import.
            }
        }
        return ids;
    }
}
