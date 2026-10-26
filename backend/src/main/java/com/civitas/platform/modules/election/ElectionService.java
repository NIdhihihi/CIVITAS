package com.civitas.platform.modules.election;

import com.civitas.platform.modules.members.Member;
import com.civitas.platform.modules.members.MemberRepository;
import com.civitas.platform.modules.onboarding.CandidateStatus;
import com.civitas.platform.modules.tenants.Tenant;
import com.civitas.platform.modules.tenants.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.Instant;
import java.util.*;

@Service
public class ElectionService {
    private final ElectionRepository elections;
    private final PostRepository posts;
    private final PanelRepository panels;
    private final ElectionCandidateRepository candidates;
    private final VoterRollRepository voterRoll;
    private final BallotRepository ballots;
    private final BallotItemRepository ballotItems;
    private final TenantRepository tenants;
    private final MemberRepository members;

    public ElectionService(ElectionRepository elections,
                           PostRepository posts,
                           PanelRepository panels,
                           ElectionCandidateRepository candidates,
                           VoterRollRepository voterRoll,
                           BallotRepository ballots,
                           BallotItemRepository ballotItems,
                           TenantRepository tenants,
                           MemberRepository members) {
        this.elections = elections;
        this.posts = posts;
        this.panels = panels;
        this.candidates = candidates;
        this.voterRoll = voterRoll;
        this.ballots = ballots;
        this.ballotItems = ballotItems;
        this.tenants = tenants;
        this.members = members;
    }

    @Transactional
    public ElectionDtos.ElectionResponse createElection(String creatorEmail, ElectionDtos.CreateElectionRequest req) {
        UUID tenantId = parseUuid(req.tenantId(), "tenantId");
        Tenant tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found."));
        Member creator = members.findByEmailIgnoreCase(creatorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        Instant start = parseInstant(req.votingStartTime());
        Instant end = parseInstant(req.votingEndTime());
        if (start != null && end != null && end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voting end time must be after start time.");
        }

        Election election = new Election(tenant, req.title(), req.description(), start, end, creator);
        Election saved = elections.save(election);
        return toElectionResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ElectionDtos.ElectionResponse> listByTenant(UUID tenantId) {
        return elections.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toElectionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ElectionDtos.ElectionDetailResponse getElectionDetail(UUID electionId) {
        Election election = findElection(electionId);
        List<Post> postList = posts.findByElectionIdOrderByDisplayOrderAsc(electionId);
        List<Panel> panelList = panels.findByElectionIdOrderByNameAsc(electionId);
        List<VoterRollMember> roll = voterRoll.findByElectionIdOrderByFlatNumberAsc(electionId);

        Map<UUID, List<ElectionCandidate>> candidateMap = new HashMap<>();
        for (ElectionCandidate c : candidates.findByElectionIdOrderByRegisteredAtAsc(electionId)) {
            candidateMap.computeIfAbsent(c.getPost().getId(), k -> new ArrayList<>()).add(c);
        }

        List<ElectionDtos.PostResponse> postResponses = postList.stream().map(p -> {
            List<ElectionDtos.CandidateResponse> cDtos = candidateMap.getOrDefault(p.getId(), Collections.emptyList())
                    .stream().map(this::toCandidateResponse).toList();
            return new ElectionDtos.PostResponse(
                    p.getId().toString(),
                    election.getId().toString(),
                    p.getTitle(),
                    p.getDescription(),
                    p.getMaxSelections(),
                    p.getDisplayOrder(),
                    cDtos
            );
        }).toList();

        List<ElectionDtos.PanelResponse> panelResponses = panelList.stream()
                .map(this::toPanelResponse).toList();

        List<ElectionDtos.VoterRollResponse> rollResponses = roll.stream()
                .map(this::toVoterRollResponse).toList();

        return new ElectionDtos.ElectionDetailResponse(
                toElectionResponse(election),
                postResponses,
                panelResponses,
                rollResponses
        );
    }

    @Transactional
    public ElectionDtos.ElectionResponse updateElection(UUID electionId, ElectionDtos.UpdateElectionRequest req) {
        Election election = findElection(electionId);
        if (!election.isModifiable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Election cannot be modified in state: " + election.getStatus());
        }
        Instant start = parseInstant(req.votingStartTime());
        Instant end = parseInstant(req.votingEndTime());
        if (start != null && end != null && end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voting end time must be after start time.");
        }
        election.setTitle(req.title());
        election.setDescription(req.description());
        election.setVotingStartTime(start);
        election.setVotingEndTime(end);
        return toElectionResponse(elections.save(election));
    }

    @Transactional
    public ElectionDtos.ElectionResponse changeStatus(UUID electionId, ElectionStatus newStatus) {
        Election election = findElection(electionId);
        ElectionStatus current = election.getStatus();

        if (current == newStatus) return toElectionResponse(election);

        if (newStatus == ElectionStatus.CONFIGURED) {
            long postCount = posts.countByElectionId(electionId);
            if (postCount == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot configure election: at least one post must be added.");
            }
        } else if (newStatus == ElectionStatus.PUBLISHED) {
            long approvedCount = candidates.countByElectionIdAndStatus(electionId, CandidateStatus.APPROVED);
            if (approvedCount == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot publish election: at least one approved candidate is required.");
            }
            long eligibleVoters = voterRoll.countByElectionIdAndEligibleTrue(electionId);
            if (eligibleVoters == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot publish election: voter roll has no eligible voters.");
            }
        } else if (newStatus == ElectionStatus.VOTING_OPEN) {
            if (current != ElectionStatus.PUBLISHED && current != ElectionStatus.CONFIGURED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Election must be PUBLISHED or CONFIGURED before opening voting.");
            }
            if (election.getVotingStartTime() == null) {
                election.setVotingStartTime(Instant.now());
            }
        } else if (newStatus == ElectionStatus.VOTING_CLOSED) {
            if (current != ElectionStatus.VOTING_OPEN) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only close an election that is VOTING_OPEN.");
            }
            if (election.getVotingEndTime() == null) {
                election.setVotingEndTime(Instant.now());
            }
        }

        election.setStatus(newStatus);
        return toElectionResponse(elections.save(election));
    }

    // --- Post Management ---
    @Transactional
    public ElectionDtos.PostResponse addPost(UUID electionId, ElectionDtos.CreatePostRequest req) {
        Election election = findElection(electionId);
        if (!election.isModifiable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot add posts once voting is active or completed.");
        }
        if (posts.existsByElectionIdAndTitleIgnoreCase(electionId, req.title().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A post with this title already exists in this election.");
        }
        Post post = new Post(election, req.title(), req.description(), req.maxSelections(), req.displayOrder());
        Post saved = posts.save(post);
        return new ElectionDtos.PostResponse(
                saved.getId().toString(),
                electionId.toString(),
                saved.getTitle(),
                saved.getDescription(),
                saved.getMaxSelections(),
                saved.getDisplayOrder(),
                Collections.emptyList()
        );
    }

    // --- Panel Management ---
    @Transactional
    public ElectionDtos.PanelResponse addPanel(UUID electionId, ElectionDtos.CreatePanelRequest req) {
        Election election = findElection(electionId);
        if (!election.isModifiable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot add panels once voting is active or completed.");
        }
        if (panels.existsByElectionIdAndNameIgnoreCase(electionId, req.name().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A panel with this name already exists in this election.");
        }
        Panel panel = new Panel(election, req.name(), req.motto(), req.colorHex());
        Panel saved = panels.save(panel);
        return toPanelResponse(saved);
    }

    // --- Candidate Management ---
    @Transactional
    public ElectionDtos.CandidateResponse nominateCandidate(UUID electionId, ElectionDtos.NominateCandidateRequest req) {
        Election election = findElection(electionId);
        if (!election.isModifiable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot nominate candidates once voting is active or completed.");
        }
        UUID postId = parseUuid(req.postId(), "postId");
        Post post = posts.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found."));
        if (!post.getElection().getId().equals(electionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Post does not belong to this election.");
        }

        Panel panel = null;
        if (req.panelId() != null && !req.panelId().isBlank()) {
            UUID panelId = parseUuid(req.panelId(), "panelId");
            panel = panels.findById(panelId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Panel not found."));
            if (!panel.getElection().getId().equals(electionId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Panel does not belong to this election.");
            }
        }

        if (candidates.existsByElectionIdAndPostIdAndEmailIgnoreCase(electionId, postId, req.email().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Candidate is already nominated for this post.");
        }

        Member member = members.findByEmailIgnoreCase(req.email().trim()).orElse(null);

        ElectionCandidate candidate = new ElectionCandidate(
                election,
                post,
                member,
                panel,
                req.fullName(),
                req.email(),
                req.phone(),
                req.statement(),
                req.photoUrl()
        );

        ElectionCandidate saved = candidates.save(candidate);
        return toCandidateResponse(saved);
    }

    @Transactional
    public ElectionDtos.CandidateResponse updateCandidateStatus(UUID candidateId, CandidateStatus status) {
        ElectionCandidate candidate = candidates.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found."));
        candidate.setStatus(status);
        return toCandidateResponse(candidates.save(candidate));
    }

    // --- Voter Roll Management & Deduplication ---
    @Transactional
    public ElectionDtos.VoterRollUploadResult uploadVoterRollCsv(UUID electionId, String csvData) {
        Election election = findElection(electionId);
        if (election.getStatus() == ElectionStatus.VOTING_OPEN || election.getStatus() == ElectionStatus.VOTING_CLOSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voter roll cannot be modified while voting is active or closed.");
        }

        List<String> errors = new ArrayList<>();
        int parsedRows = 0;
        int added = 0;
        int dupFlats = 0;
        int dupEmails = 0;

        Set<String> seenFlats = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(csvData))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                // Header check
                if (lineNum == 1 && line.toLowerCase().contains("flat")) {
                    continue;
                }

                parsedRows++;
                String[] parts = line.split(",", -1);
                if (parts.length < 3) {
                    errors.add("Line " + lineNum + ": Insufficient columns. Minimum required: flatNumber,fullName,email");
                    continue;
                }

                String flat = parts[0].trim().toUpperCase();
                String name = parts[1].trim();
                String email = parts[2].trim().toLowerCase();
                String phone = parts.length > 3 ? parts[3].trim() : null;
                String aadhaar = parts.length > 4 ? parts[4].trim() : null;
                boolean eligible = true;
                String reason = null;

                if (parts.length > 5 && !parts[5].trim().isEmpty()) {
                    eligible = Boolean.parseBoolean(parts[5].trim()) || "eligible".equalsIgnoreCase(parts[5].trim());
                }
                if (parts.length > 6) {
                    reason = parts[6].trim();
                }

                if (flat.isEmpty() || name.isEmpty() || !email.contains("@")) {
                    errors.add("Line " + lineNum + ": Invalid data format for flat '" + flat + "' or email '" + email + "'");
                    continue;
                }

                if (seenFlats.contains(flat) || voterRoll.existsByElectionIdAndFlatNumberIgnoreCase(electionId, flat)) {
                    dupFlats++;
                    errors.add("Line " + lineNum + ": Duplicate flat number detected: " + flat);
                    continue;
                }

                if (seenEmails.contains(email) || voterRoll.existsByElectionIdAndEmailIgnoreCase(electionId, email)) {
                    dupEmails++;
                    errors.add("Line " + lineNum + ": Duplicate email detected: " + email);
                    continue;
                }

                seenFlats.add(flat);
                seenEmails.add(email);

                VoterRollMember member = new VoterRollMember(
                        election.getTenant(),
                        election,
                        flat,
                        name,
                        email,
                        phone,
                        aadhaar,
                        eligible,
                        reason
                );
                voterRoll.save(member);
                added++;
            }
        } catch (Exception e) {
            errors.add("Error reading CSV content: " + e.getMessage());
        }

        return new ElectionDtos.VoterRollUploadResult(parsedRows, added, dupFlats, dupEmails, errors);
    }

    @Transactional
    public ElectionDtos.VoterRollResponse addSingleVoter(UUID electionId, ElectionDtos.SingleVoterRequest req) {
        Election election = findElection(electionId);
        String flat = req.flatNumber().trim().toUpperCase();
        String email = req.email().trim().toLowerCase();

        if (voterRoll.existsByElectionIdAndFlatNumberIgnoreCase(electionId, flat)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A voter is already registered for flat " + flat);
        }
        if (voterRoll.existsByElectionIdAndEmailIgnoreCase(electionId, email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A voter is already registered with email " + email);
        }

        boolean eligible = req.eligible() == null || req.eligible();
        VoterRollMember member = new VoterRollMember(
                election.getTenant(),
                election,
                flat,
                req.fullName(),
                email,
                req.phoneNumber(),
                req.aadhaarReference(),
                eligible,
                req.disqualificationReason()
        );

        return toVoterRollResponse(voterRoll.save(member));
    }

    @Transactional(readOnly = true)
    public List<ElectionDtos.VoterRollResponse> getVoterRoll(UUID electionId) {
        return voterRoll.findByElectionIdOrderByFlatNumberAsc(electionId).stream()
                .map(this::toVoterRollResponse)
                .toList();
    }

    // --- Helper Mappers ---
    public Election findElection(UUID id) {
        return elections.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Election not found: " + id));
    }

    private ElectionDtos.ElectionResponse toElectionResponse(Election e) {
        long totalVoters = voterRoll.countByElectionId(e.getId());
        long eligibleVoters = voterRoll.countByElectionIdAndEligibleTrue(e.getId());
        long votesCast = voterRoll.countByElectionIdAndHasVotedTrue(e.getId());

        return new ElectionDtos.ElectionResponse(
                e.getId().toString(),
                e.getTenant().getId().toString(),
                e.getTenant().getName(),
                e.getTitle(),
                e.getDescription(),
                e.getStatus().name(),
                e.getVotingStartTime() != null ? e.getVotingStartTime().toString() : null,
                e.getVotingEndTime() != null ? e.getVotingEndTime().toString() : null,
                e.getCreatedBy().getEmail(),
                e.getCreatedAt().toString(),
                totalVoters,
                eligibleVoters,
                votesCast
        );
    }

    private ElectionDtos.PanelResponse toPanelResponse(Panel p) {
        return new ElectionDtos.PanelResponse(
                p.getId().toString(),
                p.getElection().getId().toString(),
                p.getName(),
                p.getMotto(),
                p.getColorHex()
        );
    }

    private ElectionDtos.CandidateResponse toCandidateResponse(ElectionCandidate c) {
        return new ElectionDtos.CandidateResponse(
                c.getId().toString(),
                c.getElection().getId().toString(),
                c.getPost().getId().toString(),
                c.getPost().getTitle(),
                c.getPanel() != null ? c.getPanel().getId().toString() : null,
                c.getPanel() != null ? c.getPanel().getName() : null,
                c.getFullName(),
                c.getEmail(),
                c.getPhone(),
                c.getStatement(),
                c.getPhotoUrl(),
                c.getStatus().name(),
                c.getRegisteredAt().toString()
        );
    }

    private ElectionDtos.VoterRollResponse toVoterRollResponse(VoterRollMember v) {
        return new ElectionDtos.VoterRollResponse(
                v.getId().toString(),
                v.getElection().getId().toString(),
                v.getFlatNumber(),
                v.getFullName(),
                v.getEmail(),
                v.getPhoneNumber(),
                v.getAadhaarReference(),
                v.isEligible(),
                v.getDisqualificationReason(),
                v.isHasVoted(),
                v.getVotedAt() != null ? v.getVotedAt().toString() : null
        );
    }

    // --- Voting & Anonymized Secret Ballot Engine (REQ-VOTE-1 to REQ-VOTE-8, REQ-5, REQ-6) ---

    @Transactional(readOnly = true)
    public ElectionDtos.VoterBallotResponse getBallotForVoter(String userEmail, UUID electionId) {
        Election election = findElection(electionId);
        Optional<VoterRollMember> voterOpt = voterRoll.findByElectionIdAndEmailIgnoreCase(electionId, userEmail.trim());

        boolean isEligible = voterOpt.map(VoterRollMember::isEligible).orElse(false);
        boolean hasVoted = voterOpt.map(VoterRollMember::isHasVoted).orElse(false);
        String disqReason = voterOpt.map(VoterRollMember::getDisqualificationReason).orElse(
                voterOpt.isEmpty() ? "Not found on official voter roll for this society election." : null
        );

        List<Post> postList = posts.findByElectionIdOrderByDisplayOrderAsc(electionId);
        List<Panel> panelList = panels.findByElectionIdOrderByNameAsc(electionId);

        // Only approved candidates appear on the official ballot (REQ-VOTE-3)
        List<ElectionCandidate> approvedCandidates = candidates.findByElectionIdAndStatus(electionId, CandidateStatus.APPROVED);
        Map<UUID, List<ElectionCandidate>> candidateMap = new HashMap<>();
        for (ElectionCandidate c : approvedCandidates) {
            candidateMap.computeIfAbsent(c.getPost().getId(), k -> new ArrayList<>()).add(c);
        }

        List<ElectionDtos.PostResponse> postResponses = postList.stream().map(p -> {
            List<ElectionDtos.CandidateResponse> cDtos = candidateMap.getOrDefault(p.getId(), Collections.emptyList())
                    .stream().map(this::toCandidateResponse).toList();
            return new ElectionDtos.PostResponse(
                    p.getId().toString(),
                    election.getId().toString(),
                    p.getTitle(),
                    p.getDescription(),
                    p.getMaxSelections(),
                    p.getDisplayOrder(),
                    cDtos
            );
        }).toList();

        List<ElectionDtos.PanelResponse> panelResponses = panelList.stream()
                .map(this::toPanelResponse).toList();

        return new ElectionDtos.VoterBallotResponse(
                election.getId().toString(),
                election.getTitle(),
                election.getTenant().getName(),
                election.getStatus().name(),
                isEligible,
                hasVoted,
                disqReason,
                postResponses,
                panelResponses
        );
    }

    @Transactional
    public ElectionDtos.VoteReceiptResponse castVote(String userEmail, UUID electionId, ElectionDtos.CastVoteRequest req) {
        if (!req.dualFactorConfirmed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dual-factor vote confirmation is required before ballot submission.");
        }

        Election election = findElection(electionId);
        if (election.getStatus() != ElectionStatus.VOTING_OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voting is currently " + election.getStatus() + ". Ballots can only be cast when VOTING_OPEN.");
        }

        VoterRollMember voter = voterRoll.findByElectionIdAndEmailIgnoreCase(electionId, userEmail.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not listed on the validated voter roll for this election."));

        if (!voter.isEligible()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are disqualified from voting: " + (voter.getDisqualificationReason() != null ? voter.getDisqualificationReason() : "Ineligible"));
        }

        if (voter.isHasVoted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate vote rejected. A ballot has already been recorded for your flat/account.");
        }

        // Mark voter as voted (atomic update to prevent double voting)
        voter.markVoted();
        voterRoll.save(voter);

        // Cryptographic receipt generation (REQ-5, REQ-6)
        // Produces a verifiable zero-knowledge receipt without linking identity to candidates
        String salt = UUID.randomUUID().toString();
        String rawHashSource = electionId + ":" + salt + ":" + Instant.now().toEpochMilli();
        String ballotHash = sha256(rawHashSource);
        String receiptCode = "CVT-" + ballotHash.substring(0, 4).toUpperCase() + "-"
                + ballotHash.substring(4, 8).toUpperCase() + "-"
                + ballotHash.substring(8, 12).toUpperCase();

        boolean isPanelVote = req.panelId() != null && !req.panelId().isBlank();
        Panel panel = null;
        if (isPanelVote) {
            UUID pId = parseUuid(req.panelId(), "panelId");
            panel = panels.findById(pId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Selected panel not found."));
        }

        Ballot ballot = new Ballot(election, receiptCode, ballotHash, isPanelVote, panel);
        Ballot savedBallot = ballots.save(ballot);

        if (isPanelVote) {
            // Auto-cast for all approved candidates in this panel
            List<ElectionCandidate> panelCandidates = candidates.findByElectionIdAndStatus(electionId, CandidateStatus.APPROVED)
                    .stream()
                    .filter(c -> c.getPanel() != null && c.getPanel().getId().equals(panel.getId()))
                    .toList();
            for (ElectionCandidate c : panelCandidates) {
                ballotItems.save(new BallotItem(savedBallot, c.getPost(), c));
            }
        } else {
            // Individual selections per post
            if (req.selections() == null || req.selections().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ballot must contain candidate selections.");
            }
            for (ElectionDtos.PostSelection sel : req.selections()) {
                UUID postId = parseUuid(sel.postId(), "postId");
                Post post = posts.findById(postId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found: " + sel.postId()));

                if (sel.candidateIds().size() > post.getMaxSelections()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can select at most " + post.getMaxSelections() + " candidate(s) for " + post.getTitle());
                }

                for (String cIdStr : sel.candidateIds()) {
                    UUID cId = parseUuid(cIdStr, "candidateId");
                    ElectionCandidate candidate = candidates.findById(cId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found: " + cIdStr));
                    if (!candidate.getPost().getId().equals(post.getId())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Candidate does not contest for " + post.getTitle());
                    }
                    if (candidate.getStatus() != CandidateStatus.APPROVED) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Candidate is not approved for the ballot.");
                    }
                    ballotItems.save(new BallotItem(savedBallot, post, candidate));
                }
            }
        }

        return new ElectionDtos.VoteReceiptResponse(
                receiptCode,
                ballotHash,
                election.getId().toString(),
                election.getTitle(),
                election.getTenant().getName(),
                savedBallot.getCastAt().toString(),
                "Your vote has been cryptographically signed and permanently recorded in the ballot box."
        );
    }

    @Transactional(readOnly = true)
    public ElectionDtos.ReceiptVerifyResponse verifyReceipt(String receiptCode) {
        Ballot ballot = ballots.findByReceiptCode(receiptCode.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid receipt code. No matching ballot found."));

        return new ElectionDtos.ReceiptVerifyResponse(
                true,
                ballot.getReceiptCode(),
                ballot.getBallotHash(),
                ballot.getElection().getTitle(),
                ballot.getElection().getTenant().getName(),
                ballot.getCastAt().toString(),
                ballot.isPanelVote(),
                ballot.getPanel() != null ? ballot.getPanel().getName() : null
        );
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private UUID parseUuid(String val, String field) {
        try {
            return UUID.fromString(val);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be a valid UUID.");
        }
    }

    private Instant parseInstant(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return Instant.parse(val);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid timestamp format: " + val + ". Use ISO-8601 (e.g. 2026-10-30T10:00:00Z).");
        }
    }
}

