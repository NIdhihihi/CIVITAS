package com.civitas.platform.modules.results;

import com.civitas.platform.modules.audit.AuditService;
import com.civitas.platform.modules.elections.*;
import com.civitas.platform.modules.voting.BallotCryptoService;
import com.civitas.platform.modules.voting.BallotOptionService;
import com.civitas.platform.modules.voting.Vote;
import com.civitas.platform.modules.voting.VoteRepository;
import com.civitas.platform.modules.voting.VoterParticipationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class TallyService {
    private static final Logger log = LoggerFactory.getLogger(TallyService.class);
    private final ElectionRepository elections;
    private final ElectionPostRepository posts;
    private final ElectionCandidateRepository candidates;
    private final PanelRepository panels;
    private final VoteRepository votes;
    private final VoterParticipationRepository participation;
    private final BallotOptionService options;
    private final BallotCryptoService crypto;
    private final ElectionTallyRepository tallies;
    private final ElectionVoterRepository voters;
    private final AuditService audit;
    private final String masterSecret;

    public TallyService(ElectionRepository elections, ElectionPostRepository posts,
                        ElectionCandidateRepository candidates, PanelRepository panels,
                        VoteRepository votes, VoterParticipationRepository participation,
                        BallotOptionService options, BallotCryptoService crypto,
                        ElectionTallyRepository tallies, ElectionVoterRepository voters, AuditService audit,
                        @org.springframework.beans.factory.annotation.Value("${civitas.security.master-secret}") String masterSecret) {
        this.elections = elections;
        this.posts = posts;
        this.candidates = candidates;
        this.panels = panels;
        this.votes = votes;
        this.participation = participation;
        this.options = options;
        this.crypto = crypto;
        this.tallies = tallies;
        this.voters = voters;
        this.audit = audit;
        this.masterSecret = masterSecret;
    }

    @Transactional
    public synchronized ResultDtos.ResultsResponse tally(UUID electionId) {
        Election election = elections.findById(electionId).orElseThrow();
        if (election.getStatus() == ElectionStatus.COUNTED && tallies.countByElectionId(electionId) > 0) {
            return results(electionId);
        }
        if (election.getStatus() != ElectionStatus.CLOSED && election.getStatus() != ElectionStatus.COUNTED) {
            throw new IllegalStateException("Election must be closed before counting.");
        }
        tallies.deleteByElectionId(electionId);
        List<ElectionPost> electionPosts = posts.findByElectionIdOrderBySortOrderAscCreatedAtAsc(electionId);
        Map<UUID, List<BallotOptionService.Option>> optionsByPost = new HashMap<>();
        Map<UUID, ElectionPost> postById = new HashMap<>();
        for (ElectionPost post : electionPosts) {
            optionsByPost.put(post.getId(), options.options(post));
            postById.put(post.getId(), post);
        }
        Map<String, ElectionTally> counts = new LinkedHashMap<>();
        int rejected = 0;
        for (Vote vote : votes.findByElectionId(electionId)) {
            try {
                BallotCryptoService.OpenedBallot opened = crypto.open(vote);
                List<BallotOptionService.Option> postOptions = optionsByPost.get(vote.getPost().getId());
                if (!opened.proofValid() || postOptions == null || opened.index() < 1
                        || opened.index() > postOptions.size()) {
                    rejected++;
                    continue;
                }
                BallotOptionService.Option option = postOptions.get(opened.index() - 1);
                if (!option.type().equals(opened.type()) || !option.id().equals(opened.id())) {
                    rejected++;
                    continue;
                }
                ElectionPost post = postById.get(vote.getPost().getId());
                if ("CANDIDATE".equals(opened.type())) {
                    ElectionCandidate candidate = candidates.findById(UUID.fromString(opened.id())).orElse(null);
                    if (candidate == null) {
                        rejected++;
                        continue;
                    }
                    increment(counts, election, post, "CANDIDATE", candidate.getId().toString(),
                            candidate.getDisplayName(), candidate.getPhotoUrl());
                } else {
                    Panel panel = panels.findById(UUID.fromString(opened.id())).orElse(null);
                    if (panel == null) {
                        rejected++;
                        continue;
                    }
                    increment(counts, election, post, "PANEL", panel.getId().toString(), panel.getName(), panel.getPhotoUrl());
                    ElectionCandidate member = candidates.findByPanelId(panel.getId()).stream()
                            .filter(c -> c.getPost().getId().equals(post.getId())
                                    && c.getStatus() == CandidateStatus.APPROVED)
                            .findFirst().orElse(null);
                    if (member != null) {
                        increment(counts, election, post, "CANDIDATE", member.getId().toString(),
                                member.getDisplayName(), member.getPhotoUrl());
                    }
                }
            } catch (RuntimeException ex) {
                rejected++;
                log.warn("Skipping unreadable ballot {} during tally", vote.getId(), ex);
            }
        }
        tallies.saveAll(counts.values());
        election.setStatus(ElectionStatus.COUNTED);
        election.setCountedAt(Instant.now());
        elections.save(election);
        audit.record("ELECTION_COUNTED", electionId.toString(), election.getTenant().getId().toString(),
                "ELECTION", electionId.toString(), Map.of("acceptedBallots", votes.countByElectionId(electionId),
                        "rejectedEnvelopes", rejected, "participationRecords", participation.countByElectionId(electionId)));
        return results(electionId);
    }

    @Transactional(readOnly = true)
    public ResultDtos.ResultsResponse results(UUID electionId) {
        Election election = elections.findById(electionId).orElseThrow();
        List<ElectionPost> electionPosts = posts.findByElectionIdOrderBySortOrderAscCreatedAtAsc(electionId);
        List<ElectionCandidate> allCandidates = candidates.findByPostElectionIdAndStatusOrderByPostSortOrderAscSortOrderAscCreatedAtAsc(electionId, CandidateStatus.APPROVED);
        List<Panel> allPanels = panels.findByElectionIdOrderByCreatedAtAsc(electionId);
        Map<String, Long> tallyMap = new HashMap<>();
        for (ElectionTally tally : tallies.findByElectionIdOrderByPostSortOrderAscVotesDesc(electionId)) {
            tallyMap.merge(tally.getPost().getId() + ":" + tally.getContestType() + ":" + tally.getContestId(), tally.getVotes(), Long::sum);
        }
        List<ResultDtos.PostResult> postResults = new ArrayList<>();
        long totalBallots = participation.countByElectionId(electionId);
        for (ElectionPost post : electionPosts) {
            List<ResultDtos.ContestantResult> candidateResults = new ArrayList<>();
            for (ElectionCandidate candidate : allCandidates.stream().filter(c -> c.getPost().getId().equals(post.getId())).toList()) {
                long voteCount = tallyMap.getOrDefault(post.getId() + ":CANDIDATE:" + candidate.getId(), 0L);
                candidateResults.add(new ResultDtos.ContestantResult(candidate.getId().toString(),
                        candidate.getDisplayName(), candidate.getPhotoUrl(), voteCount, 0));
            }
            List<ResultDtos.ContestantResult> panelResults = new ArrayList<>();
            for (Panel panel : allPanels) {
                boolean present = allCandidates.stream().anyMatch(c -> c.getPost().getId().equals(post.getId())
                        && c.getPanel() != null && c.getPanel().getId().equals(panel.getId()));
                if (present) {
                    long voteCount = tallyMap.getOrDefault(post.getId() + ":PANEL:" + panel.getId(), 0L);
                    panelResults.add(new ResultDtos.ContestantResult(panel.getId().toString(), panel.getName(),
                            panel.getPhotoUrl(), voteCount, 0));
                }
            }
            long postTotal = Math.max(candidateResults.stream().mapToLong(ResultDtos.ContestantResult::votes).sum(),
                    panelResults.stream().mapToLong(ResultDtos.ContestantResult::votes).sum());
            candidateResults.sort(Comparator.comparingLong(ResultDtos.ContestantResult::votes).reversed()
                    .thenComparing(ResultDtos.ContestantResult::name));
            panelResults.sort(Comparator.comparingLong(ResultDtos.ContestantResult::votes).reversed()
                    .thenComparing(ResultDtos.ContestantResult::name));
            List<ResultDtos.ContestantResult> withPercentages = new ArrayList<>();
            for (ResultDtos.ContestantResult r : candidateResults) {
                withPercentages.add(new ResultDtos.ContestantResult(r.id(), r.name(), r.photoUrl(), r.votes(),
                        postTotal == 0 ? 0 : Math.round(r.votes() * 1000.0 / postTotal) / 10.0));
            }
            List<ResultDtos.ContestantResult> panelsWithPercentages = new ArrayList<>();
            for (ResultDtos.ContestantResult r : panelResults) {
                panelsWithPercentages.add(new ResultDtos.ContestantResult(r.id(), r.name(), r.photoUrl(), r.votes(),
                        postTotal == 0 ? 0 : Math.round(r.votes() * 1000.0 / postTotal) / 10.0));
            }
            postResults.add(new ResultDtos.PostResult(post.getId().toString(), post.getTitle(), postTotal,
                    withPercentages, panelsWithPercentages));
        }
        String canonical = canonicalResult(election, postResults);
        String resultHash = com.civitas.platform.common.CryptoUtils.sha256(canonical);
        String signature = com.civitas.platform.common.CryptoUtils.hmacSha256(masterSecret, resultHash);
        boolean chainValid = audit.verify(electionId.toString()).valid();
        return new ResultDtos.ResultsResponse(electionId.toString(), election.getTitle(),
                election.getTenant().getName(), election.getTenant().getId().toString(), election.getStatus().name(),
                election.getCountedAt() == null ? null : election.getCountedAt().toString(), totalBallots,
                voters.countByElectionId(electionId), postResults, resultHash, signature, chainValid);
    }

    private void increment(Map<String, ElectionTally> counts, Election election, ElectionPost post,
                           String type, String id, String name, String photo) {
        String key = post.getId() + ":" + type + ":" + id;
        ElectionTally tally = counts.get(key);
        if (tally == null) {
            tally = new ElectionTally(election, post, type, UUID.fromString(id), name, photo, 0);
            counts.put(key, tally);
        }
        tally.addVote();
    }

    private String canonicalResult(Election election, List<ResultDtos.PostResult> posts) {
        StringBuilder sb = new StringBuilder();
        sb.append(election.getId()).append('|').append(election.getStatus()).append('|')
                .append(election.getCountedAt()).append('\n');
        for (ResultDtos.PostResult post : posts) {
            sb.append(post.postId()).append('|').append(post.title()).append('|').append(post.totalVotes()).append('\n');
            for (ResultDtos.ContestantResult c : post.candidates()) {
                sb.append("C|").append(c.id()).append('|').append(c.name()).append('|').append(c.votes()).append('\n');
            }
            for (ResultDtos.ContestantResult p : post.panels()) {
                sb.append("P|").append(p.id()).append('|').append(p.name()).append('|').append(p.votes()).append('\n');
            }
        }
        return sb.toString();
    }

}
