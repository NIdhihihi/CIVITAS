package com.civitas.platform.modules.elections;

import com.civitas.platform.common.CryptoUtils;
import com.civitas.platform.common.FileStorageService;
import com.civitas.platform.modules.audit.AuditService;
import com.civitas.platform.modules.members.Member;
import com.civitas.platform.modules.members.MemberRepository;
import com.civitas.platform.modules.members.Role;
import com.civitas.platform.modules.tenants.Tenant;
import com.civitas.platform.modules.tenants.TenantService;
import com.civitas.platform.modules.voting.BallotService;
import com.civitas.platform.modules.voting.VoterParticipationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
public class ElectionService {
    private final ElectionRepository elections;
    private final ElectionPostRepository posts;
    private final ElectionCandidateRepository candidates;
    private final PanelRepository panels;
    private final ElectionVoterRepository voters;
    private final VoterParticipationRepository participation;
    private final MemberRepository members;
    private final TenantService tenants;
    private final AuditService audit;
    private final BallotService ballots;
    private final FileStorageService files;
    private final ApplicationEventPublisher events;
    private final String masterSecret;

    public ElectionService(ElectionRepository elections, ElectionPostRepository posts,
                           ElectionCandidateRepository candidates, PanelRepository panels,
                           ElectionVoterRepository voters, VoterParticipationRepository participation,
                           MemberRepository members, TenantService tenants, AuditService audit,
                           BallotService ballots, FileStorageService files, ApplicationEventPublisher events,
                           @Value("${civitas.security.master-secret}") String masterSecret) {
        this.elections = elections;
        this.posts = posts;
        this.candidates = candidates;
        this.panels = panels;
        this.voters = voters;
        this.participation = participation;
        this.members = members;
        this.tenants = tenants;
        this.audit = audit;
        this.ballots = ballots;
        this.files = files;
        this.events = events;
        this.masterSecret = masterSecret;
    }

    @Transactional
    public ElectionDtos.ElectionResponse create(String email, ElectionDtos.CreateElectionRequest request) {
        Member member = requireMember(email);
        Tenant tenant = tenants.find(request.tenantId());
        if (member.getRole() != Role.PLATFORM_ADMIN && member.getRole() != Role.TENANT_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Society administrator access is required.");
        }
        if (member.getRole() != Role.PLATFORM_ADMIN && !tenants.isMemberOrCreator(email, tenant.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only create elections for a society you administer.");
        }
        validateWindow(request.startsAt(), request.endsAt());
        Election election = elections.save(new Election(tenant, request.title(), request.description(),
                request.startsAt(), request.endsAt(), request.notifyVoters() == null || request.notifyVoters(), member));
        audit.record("ELECTION_CREATED", election.getId().toString(), tenant.getId().toString(),
                "ELECTION", election.getId().toString(), Map.of("title", election.getTitle()));
        return view(election);
    }

    @Transactional
    public ElectionDtos.ElectionResponse update(String email, UUID electionId, ElectionDtos.UpdateElectionRequest request) {
        Election election = find(electionId);
        requireAdmin(email, election);
        requireDraftOrScheduled(election);
        if (request.title() != null && !request.title().isBlank()) election.setTitle(request.title());
        if (request.description() != null) election.setDescription(request.description());
        if (request.startsAt() != null) election.setStartsAt(request.startsAt());
        if (request.endsAt() != null) election.setEndsAt(request.endsAt());
        if (request.notifyVoters() != null) election.setNotifyVoters(request.notifyVoters());
        validateWindow(election.getStartsAt(), election.getEndsAt());
        elections.save(election);
        audit.record("ELECTION_UPDATED", electionId.toString(), election.getTenant().getId().toString(),
                "ELECTION", electionId.toString(), Map.of("title", election.getTitle()));
        return view(election);
    }

    @Transactional
    public ElectionDtos.ElectionResponse transition(String email, UUID electionId, ElectionStatus target) {
        Election election = find(electionId);
        requireAdmin(email, election);
        transition(election, target);
        return view(election);
    }

    @Transactional
    public void forceCount(String email, UUID electionId) {
        Election election = find(electionId);
        requireAdmin(email, election);
        if (election.getStatus() != ElectionStatus.CLOSED && election.getStatus() != ElectionStatus.COUNTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Counting is only available after the election is closed.");
        }
        events.publishEvent(new ElectionClosedEvent(electionId));
    }

    @Transactional
    public ElectionDtos.PostResponse addPost(String email, UUID electionId, ElectionDtos.CreatePostRequest request) {
        Election election = find(electionId);
        requireAdmin(email, election);
        requireDraftOrScheduled(election);
        if (posts.existsByElectionIdAndTitleIgnoreCase(electionId, request.title())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That post already exists in this election.");
        }
        int order = request.sortOrder() == null ? (int) posts.countByElectionId(electionId) : request.sortOrder();
        ElectionPost post = posts.save(new ElectionPost(election, request.title(), request.description(),
                request.votingMode() == null ? VotingMode.BOTH : request.votingMode(), order));
        audit.record("POST_ADDED", electionId.toString(), election.getTenant().getId().toString(),
                "ELECTION_POST", post.getId().toString(), Map.of("title", post.getTitle(), "votingMode", post.getVotingMode().name()));
        return postView(post);
    }

    @Transactional
    public void deletePost(String email, UUID electionId, UUID postId) {
        Election election = find(electionId);
        requireAdmin(email, election);
        requireDraftOrScheduled(election);
        ElectionPost post = posts.findByIdAndElectionId(postId, electionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found in this election."));
        List<ElectionCandidate> postCandidates = candidates.findByPostIdOrderBySortOrderAscCreatedAtAsc(postId);
        if (!postCandidates.isEmpty()) {
            candidates.deleteAll(postCandidates);
        }
        posts.delete(post);
        audit.record("POST_REMOVED", electionId.toString(), election.getTenant().getId().toString(),
                "ELECTION_POST", postId.toString(), Map.of("title", post.getTitle()));
    }

    @Transactional(readOnly = true)
    public List<ElectionDtos.PostResponse> listPosts(String email, UUID electionId) {
        Election election = find(electionId);
        requireAdminOrAuditor(email, election);
        return posts.findByElectionIdOrderBySortOrderAscCreatedAtAsc(electionId).stream().map(this::postView).toList();
    }

    @Transactional
    public ElectionDtos.PanelResponse addPanel(String email, UUID electionId, ElectionDtos.CreatePanelRequest request) {
        Election election = find(electionId);
        requireAdmin(email, election);
        requireDraftOrScheduled(election);
        if (panels.existsByElectionIdAndNameIgnoreCase(electionId, request.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That panel already exists in this election.");
        }
        String photo = request.photoBase64() != null && !request.photoBase64().isBlank()
                ? files.storeDataImage(request.photoBase64(), "panel")
                : FileStorageService.safePhotoUrl(request.photoUrl());
        Panel panel = panels.save(new Panel(election, request.name(), request.description(), photo));
        audit.record("PANEL_ADDED", electionId.toString(), election.getTenant().getId().toString(),
                "PANEL", panel.getId().toString(), Map.of("name", panel.getName()));
        return new ElectionDtos.PanelResponse(panel.getId().toString(), panel.getName(), panel.getDescription(),
                panel.getPhotoUrl(), List.of());
    }

    @Transactional
    public void deletePanel(String email, UUID electionId, UUID panelId) {
        Election election = find(electionId);
        requireAdmin(email, election);
        requireDraftOrScheduled(election);
        Panel panel = panels.findByIdAndElectionId(panelId, electionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Panel not found in this election."));
        List<ElectionCandidate> panelCandidates = candidates.findByPanelId(panelId);
        for (ElectionCandidate c : panelCandidates) {
            c.setPanel(null);
            candidates.save(c);
        }
        panels.delete(panel);
        audit.record("PANEL_REMOVED", electionId.toString(), election.getTenant().getId().toString(),
                "PANEL", panelId.toString(), Map.of("name", panel.getName()));
    }

    @Transactional(readOnly = true)
    public List<ElectionDtos.PanelResponse> listPanels(String email, UUID electionId) {
        Election election = find(electionId);
        requireAdminOrAuditor(email, election);
        return panels.findByElectionIdOrderByCreatedAtAsc(electionId).stream().map(panel -> {
            List<ElectionDtos.CandidateResponse> members = candidates.findByPanelId(panel.getId()).stream()
                    .map(this::candidateView).toList();
            return new ElectionDtos.PanelResponse(panel.getId().toString(), panel.getName(), panel.getDescription(),
                    panel.getPhotoUrl(), members);
        }).toList();
    }

    @Transactional
    public ElectionDtos.CandidateResponse addCandidate(String email, UUID electionId,
                                                       ElectionDtos.CreateCandidateRequest request) {
        Election election = find(electionId);
        requireAdmin(email, election);
        requireDraftOrScheduled(election);
        ElectionPost post = posts.findByIdAndElectionId(request.postId(), electionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a post that belongs to this election."));
        Panel panel = request.panelId() == null ? null : panels.findByIdAndElectionId(request.panelId(), electionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a panel that belongs to this election."));

        if (post.getVotingMode() == VotingMode.PANEL && panel == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This post is configured for panel voting only; candidates must be assigned to a panel.");
        }
        if (post.getVotingMode() == VotingMode.CANDIDATE && panel != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This post is configured for individual candidate voting only; panels cannot be assigned.");
        }
        if (panel != null) {
            boolean postAlreadyHasPanelCandidate = candidates.findByPanelId(panel.getId()).stream()
                    .anyMatch(c -> c.getPost().getId().equals(post.getId()));
            if (postAlreadyHasPanelCandidate) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Panel '" + panel.getName() + "' already has a candidate assigned for '" + post.getTitle() + "'.");
            }
        }

        Member member = request.memberId() == null ? null : members.findById(request.memberId()).orElse(null);
        String photo = request.photoBase64() != null && !request.photoBase64().isBlank()
                ? files.storeDataImage(request.photoBase64(), "candidate")
                : FileStorageService.safePhotoUrl(request.photoUrl());
        int order = request.sortOrder() == null ? candidates.findByPostIdOrderBySortOrderAscCreatedAtAsc(post.getId()).size() : request.sortOrder();
        ElectionCandidate candidate = new ElectionCandidate(post, panel, member, request.name(), request.statement(), photo, order);
        try {
            candidates.saveAndFlush(candidate);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The candidate could not be saved; check for duplicate data.");
        }
        audit.record("CANDIDATE_ADDED", electionId.toString(), election.getTenant().getId().toString(),
                "ELECTION_CANDIDATE", candidate.getId().toString(),
                Map.of("post", post.getTitle(), "candidate", candidate.getDisplayName()));
        return candidateView(candidate);
    }

    @Transactional
    public ElectionDtos.CandidateResponse updateCandidate(String email, UUID electionId, UUID candidateId,
                                                          ElectionDtos.UpdateCandidateRequest request) {
        Election election = find(electionId);
        requireAdmin(email, election);
        requireDraftOrScheduled(election);
        ElectionCandidate candidate = candidates.findById(candidateId)
                .filter(c -> c.getPost().getElection().getId().equals(electionId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found in this election."));
        if (request.name() != null && !request.name().isBlank()) candidate.setDisplayName(request.name());
        if (request.statement() != null) candidate.setStatement(request.statement());
        if (request.photoBase64() != null && !request.photoBase64().isBlank()) {
            candidate.setPhotoUrl(files.storeDataImage(request.photoBase64(), "candidate"));
        } else if (request.photoUrl() != null) {
            candidate.setPhotoUrl(FileStorageService.safePhotoUrl(request.photoUrl()));
        }
        if (Boolean.TRUE.equals(request.removePanel())) {
            if (candidate.getPost().getVotingMode() == VotingMode.PANEL) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This post is configured for panel voting only; candidates cannot be unlinked from a panel.");
            }
            candidate.setPanel(null);
        } else if (request.panelId() != null) {
            Panel panel = panels.findByIdAndElectionId(request.panelId(), electionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Panel not found in this election."));
            if (candidate.getPost().getVotingMode() == VotingMode.CANDIDATE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This post is configured for individual candidate voting only; panels cannot be assigned.");
            }
            boolean postAlreadyHasPanelCandidate = candidates.findByPanelId(panel.getId()).stream()
                    .anyMatch(c -> c.getPost().getId().equals(candidate.getPost().getId()) && !c.getId().equals(candidateId));
            if (postAlreadyHasPanelCandidate) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Panel '" + panel.getName() + "' already has a candidate assigned for '" + candidate.getPost().getTitle() + "'.");
            }
            candidate.setPanel(panel);
        }
        if (request.status() != null) candidate.setStatus(request.status());
        if (request.sortOrder() != null) candidate.setSortOrder(request.sortOrder());
        candidates.save(candidate);
        audit.record("CANDIDATE_UPDATED", electionId.toString(), election.getTenant().getId().toString(),
                "ELECTION_CANDIDATE", candidateId.toString(), Map.of("status", candidate.getStatus().name()));
        return candidateView(candidate);
    }

    @Transactional
    public void deleteCandidate(String email, UUID electionId, UUID candidateId) {
        Election election = find(electionId);
        requireAdmin(email, election);
        requireDraftOrScheduled(election);
        ElectionCandidate candidate = candidates.findById(candidateId)
                .filter(c -> c.getPost().getElection().getId().equals(electionId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found in this election."));
        candidates.delete(candidate);
        audit.record("CANDIDATE_REMOVED", electionId.toString(), election.getTenant().getId().toString(),
                "ELECTION_CANDIDATE", candidateId.toString(), Map.of("name", candidate.getDisplayName()));
    }

    @Transactional(readOnly = true)
    public List<ElectionDtos.CandidateResponse> listCandidates(String email, UUID electionId) {
        Election election = find(electionId);
        requireAdminOrAuditor(email, election);
        return candidates.findByPostElectionIdOrderByPostSortOrderAscSortOrderAscCreatedAtAsc(electionId).stream()
                .map(this::candidateView).toList();
    }

    @Transactional
    public ElectionDtos.VoterRollImportResponse importVoterRoll(String email, UUID electionId,
                                                                ElectionDtos.VoterRollRequest request) {
        Election election = find(electionId);
        requireAdmin(email, election);
        requireDraftOrScheduled(election);
        if (request.replace() != null && request.replace()) {
            if (participation.countByElectionId(electionId) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The voter roll cannot be replaced after voting has begun.");
            }
            voters.deleteAll(voters.findByElectionIdOrderByCreatedAtAsc(electionId));
        }
        List<ElectionDtos.VoterImportError> errors = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        Set<String> seenAadhaar = new HashSet<>();
        Set<String> seenPhones = new HashSet<>();
        voters.findByElectionIdOrderByCreatedAtAsc(electionId).forEach(v -> {
            if (v.getEmail() != null) seenEmails.add(v.getEmail().toLowerCase());
            if (v.getAadhaarHash() != null) seenAadhaar.add(v.getAadhaarHash());
            if (v.getPhone() != null) seenPhones.add(v.getPhone());
        });
        Map<String, UUID> postByTitle = new HashMap<>();
        posts.findByElectionIdOrderBySortOrderAscCreatedAtAsc(electionId)
                .forEach(p -> postByTitle.put(p.getTitle().toLowerCase(Locale.ROOT), p.getId()));

        String[] lines = request.csv().replace("\uFEFF", "").split("\\r?\\n");
        int imported = 0;
        int skipped = 0;
        int start = 0;
        if (lines.length > 0) {
            List<String> firstRowFields = parseCsvLine(lines[0]);
            String firstField = firstRowFields.isEmpty() ? "" : firstRowFields.get(0).toLowerCase(Locale.ROOT).trim();
            if (firstField.equals("name") || firstField.equals("full name") || firstField.equals("fullname")
                    || firstField.equals("voter") || firstField.equals("voter name")) {
                start = 1;
            }
        }
        for (int i = start; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isBlank()) {
                continue;
            }
            int row = i + 1;
            List<String> fields = parseCsvLine(line);
            if (fields.size() < 1 || fields.get(0).isBlank()) {
                errors.add(new ElectionDtos.VoterImportError(row, "name", "A voter name is required."));
                skipped++;
                continue;
            }
            String fullName = fields.get(0).trim();
            String voterEmail = fields.size() > 1 && !fields.get(1).isBlank() ? fields.get(1).trim().toLowerCase(Locale.ROOT) : null;
            String phone = fields.size() > 2 && !fields.get(2).isBlank() ? fields.get(2).trim() : null;
            String aadhaar = fields.size() > 3 && !fields.get(3).isBlank() ? fields.get(3).replaceAll("\\D", "") : null;
            String postTitles = fields.size() > 4 ? fields.get(4).trim() : "";
            if (voterEmail == null && phone == null && aadhaar == null) {
                errors.add(new ElectionDtos.VoterImportError(row, "identity", "Provide an email, phone, or Aadhaar number."));
                skipped++;
                continue;
            }
            if (voterEmail != null && !voterEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                errors.add(new ElectionDtos.VoterImportError(row, "email", "The email address is not valid."));
                skipped++;
                continue;
            }
            if (aadhaar != null && (aadhaar.length() < 4 || aadhaar.length() > 12)) {
                errors.add(new ElectionDtos.VoterImportError(row, "aadhaar", "Aadhaar must contain between 4 and 12 digits."));
                skipped++;
                continue;
            }
            String aadhaarHash = aadhaar == null ? null : IsAadhaarHash.hash(masterSecret, aadhaar);
            if ((voterEmail != null && seenEmails.contains(voterEmail)) || (aadhaarHash != null && seenAadhaar.contains(aadhaarHash))
                    || (phone != null && seenPhones.contains(phone))) {
                errors.add(new ElectionDtos.VoterImportError(row, "identity", "Duplicate voter in this election."));
                skipped++;
                continue;
            }
            String eligible = "";
            if (!postTitles.isBlank()) {
                List<String> ids = new ArrayList<>();
                boolean invalid = false;
                for (String title : postTitles.split("[,;|]")) {
                    if (title.isBlank()) continue;
                    UUID id = postByTitle.get(title.trim().toLowerCase(Locale.ROOT));
                    if (id == null) {
                        errors.add(new ElectionDtos.VoterImportError(row, "posts", "Unknown post: " + title.trim()));
                        invalid = true;
                        break;
                    }
                    ids.add(id.toString());
                }
                if (invalid) {
                    skipped++;
                    continue;
                }
                eligible = String.join(",", ids);
            }
            ElectionVoter voter = new ElectionVoter(election, fullName, voterEmail, phone, aadhaarHash,
                    aadhaar == null ? null : aadhaar.substring(Math.max(0, aadhaar.length() - 4)), eligible);
            if (voterEmail != null) {
                members.findByEmailIgnoreCase(voterEmail).ifPresent(voter::setMember);
            }
            voters.save(voter);
            if (voterEmail != null) seenEmails.add(voterEmail);
            if (aadhaarHash != null) seenAadhaar.add(aadhaarHash);
            if (phone != null) seenPhones.add(phone);
            imported++;
        }
        audit.record("VOTER_ROLL_IMPORTED", electionId.toString(), election.getTenant().getId().toString(),
                "ELECTION", electionId.toString(), Map.of("imported", imported, "skipped", skipped, "errors", errors.size()));
        return new ElectionDtos.VoterRollImportResponse(imported, skipped, errors, voters.countByElectionId(electionId));
    }

    @Transactional(readOnly = true)
    public List<ElectionDtos.VoterResponse> listVoters(String email, UUID electionId) {
        Election election = find(electionId);
        requireAdminOrAuditor(email, election);
        return voters.findByElectionIdOrderByCreatedAtAsc(electionId).stream().map(v ->
                new ElectionDtos.VoterResponse(v.getId().toString(), v.getFullName(), v.getEmail(), v.getPhone(),
                        v.getAadhaarLast4(), v.getEligiblePostIds(), v.isVoted(),
                        v.getVotedAt() == null ? null : v.getVotedAt().toString(), v.getCreatedAt().toString())).toList();
    }

    @Transactional
    public void deleteVoter(String email, UUID electionId, UUID voterId) {
        Election election = find(electionId);
        requireAdmin(email, election);
        requireDraftOrScheduled(election);
        ElectionVoter voter = voters.findByIdAndElectionId(voterId, electionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Voter not found."));
        if (voter.isVoted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A voter who has voted cannot be removed.");
        }
        voters.delete(voter);
        audit.record("VOTER_REMOVED", electionId.toString(), election.getTenant().getId().toString(),
                "ELECTION_VOTER", voterId.toString(), Map.of());
    }

    @Transactional(readOnly = true)
    public ElectionDtos.BallotResponse previewBallot(String email, UUID electionId) {
        Election election = find(electionId);
        requireAdminOrAuditor(email, election);
        return ballots.assemble(election, null);
    }

    @Transactional(readOnly = true)
    public List<ElectionDtos.ElectionResponse> mine(String email) {
        Member member = requireMember(email);
        List<Election> result;
        if (member.getRole() == Role.PLATFORM_ADMIN) {
            result = elections.findAllByOrderByStartsAtDesc();
        } else if (member.getRole() == Role.TENANT_ADMIN) {
            List<UUID> tenantIds = tenants.adminTenantIds(email);
            result = tenantIds.isEmpty() ? List.of() : elections.findByTenantIdInOrderByStartsAtDesc(tenantIds);
        } else if (member.getRole() == Role.AUDITOR) {
            result = elections.findAllByOrderByStartsAtDesc();
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Election administration requires an administrator role.");
        }
        return result.stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public ElectionDtos.ElectionResponse get(String email, UUID electionId) {
        Election election = find(electionId);
        requireAdminOrAuditor(email, election);
        return view(election);
    }

    /** Elections a voter can join from a public link, with no resident account required. */
    @Transactional(readOnly = true)
    public List<ElectionDtos.PublicElectionResponse> publicActive() {
        return elections.findPublicActive(Instant.now()).stream()
                .map(e -> new ElectionDtos.PublicElectionResponse(e.getId().toString(), e.getTitle(),
                        e.getTenant().getName(), e.getDescription(), e.getStartsAt(), e.getEndsAt()))
                .toList();
    }

    /** Ballot preview for a public election link. Eligibility is enforced again when the voter authenticates. */
    @Transactional(readOnly = true)
    public ElectionDtos.PublicElectionDetailResponse publicDetail(UUID electionId) {
        Election election = find(electionId);
        Instant now = Instant.now();
        if (election.getStatus() != ElectionStatus.ACTIVE || now.isBefore(election.getStartsAt())
                || !now.isBefore(election.getEndsAt())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This election is not open for voting.");
        }
        ElectionDtos.BallotResponse ballot = ballots.assemble(election, null);
        return new ElectionDtos.PublicElectionDetailResponse(election.getId().toString(), election.getTitle(),
                election.getTenant().getName(), election.getDescription(), election.getStartsAt(),
                election.getEndsAt(), election.getStatus().name(), ballot.eligibleVoters(),
                ballot.posts(), ballot.panels());
    }

    @Transactional
    public int openDueElections() {
        int opened = 0;
        for (Election election : elections.findDueToOpen(Instant.now())) {
            election.setStatus(ElectionStatus.ACTIVE);
            election.setOpenedAt(Instant.now());
            elections.save(election);
            events.publishEvent(new ElectionOpenedEvent(election.getId()));
            audit.record("ELECTION_OPENED_AUTOMATIC", "SYSTEM", "scheduler", election.getId().toString(),
                    election.getTenant().getId().toString(), "ELECTION", election.getId().toString(), Map.of());
            opened++;
        }
        return opened;
    }

    @Transactional
    public int closeDueElections() {
        int closed = 0;
        for (Election election : elections.findDueToClose(Instant.now())) {
            election.setStatus(ElectionStatus.CLOSED);
            election.setClosedAt(Instant.now());
            elections.save(election);
            events.publishEvent(new ElectionClosedEvent(election.getId()));
            audit.record("ELECTION_CLOSED_AUTOMATIC", "SYSTEM", "scheduler", election.getId().toString(),
                    election.getTenant().getId().toString(), "ELECTION", election.getId().toString(), Map.of());
            closed++;
        }
        return closed;
    }

    @Transactional
    public void transition(Election election, ElectionStatus target) {
        ElectionStatus current = election.getStatus();
        Instant now = Instant.now();
        if (target == current) {
            return;
        }
        if (target == ElectionStatus.ACTIVE) {
            if (current != ElectionStatus.DRAFT && current != ElectionStatus.SCHEDULED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft or scheduled elections can be opened.");
            }
            if (now.isBefore(election.getStartsAt())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The scheduled start time has not arrived yet. Wait for the election to open automatically.");
            }
            if (!now.isBefore(election.getEndsAt())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The polling window has already ended.");
            }
            election.setStatus(ElectionStatus.ACTIVE);
            election.setOpenedAt(now);
            elections.save(election);
            events.publishEvent(new ElectionOpenedEvent(election.getId()));
            audit.record("ELECTION_OPENED", election.getId().toString(), election.getTenant().getId().toString(),
                    "ELECTION", election.getId().toString(), Map.of());
        } else if (target == ElectionStatus.CLOSED) {
            if (current != ElectionStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an active election can be closed.");
            }
            election.setStatus(ElectionStatus.CLOSED);
            election.setClosedAt(now);
            elections.save(election);
            events.publishEvent(new ElectionClosedEvent(election.getId()));
            audit.record("ELECTION_CLOSED", election.getId().toString(), election.getTenant().getId().toString(),
                    "ELECTION", election.getId().toString(), Map.of());
        } else if (target == ElectionStatus.CANCELLED) {
            if (current != ElectionStatus.DRAFT && current != ElectionStatus.SCHEDULED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Only draft or scheduled elections can be cancelled. Close an active election first.");
            }
            election.setStatus(ElectionStatus.CANCELLED);
            elections.save(election);
            audit.record("ELECTION_CANCELLED", election.getId().toString(), election.getTenant().getId().toString(),
                    "ELECTION", election.getId().toString(), Map.of());
        } else if (target == ElectionStatus.SCHEDULED) {
            if (current != ElectionStatus.DRAFT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a draft election can be scheduled.");
            }
            election.setStatus(ElectionStatus.SCHEDULED);
            elections.save(election);
            audit.record("ELECTION_SCHEDULED", election.getId().toString(), election.getTenant().getId().toString(),
                    "ELECTION", election.getId().toString(), Map.of());
        } else if (target == ElectionStatus.DRAFT) {
            if (current != ElectionStatus.SCHEDULED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a scheduled election can be moved back to draft.");
            }
            election.setStatus(ElectionStatus.DRAFT);
            elections.save(election);
            audit.record("ELECTION_DRAFTED", election.getId().toString(), election.getTenant().getId().toString(),
                    "ELECTION", election.getId().toString(), Map.of());
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported status transition.");
        }
    }

    public Election find(UUID id) {
        return elections.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Election not found."));
    }

    public void requireAdmin(String email, Election election) {
        Member member = requireMember(email);
        if (member.getRole() == Role.PLATFORM_ADMIN) {
            return;
        }
        if (member.getRole() == Role.TENANT_ADMIN
                && tenants.isMemberOrCreator(email, election.getTenant().getId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not administer this election.");
    }

    public void requireAdminOrAuditor(String email, Election election) {
        Member member = requireMember(email);
        if (member.getRole() == Role.PLATFORM_ADMIN || member.getRole() == Role.AUDITOR) {
            return;
        }
        if (member.getRole() == Role.TENANT_ADMIN
                && tenants.isMemberOrCreator(email, election.getTenant().getId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not administer or audit this election.");
    }

    private Member requireMember(String email) {
        return members.findByEmailIgnoreCase(email).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again."));
    }

    private void requireDraftOrScheduled(Election election) {
        if (election.getStatus() != ElectionStatus.DRAFT && election.getStatus() != ElectionStatus.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The election setup is locked once voting starts.");
        }
    }

    private void validateWindow(Instant startsAt, Instant endsAt) {
        if (startsAt == null || endsAt == null || !startsAt.isBefore(endsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The polling window must end after it starts.");
        }
        if (!Instant.now().isBefore(endsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The polling window cannot end in the past.");
        }
    }

    public ElectionDtos.ElectionResponse view(Election e) {
        UUID id = e.getId();
        return new ElectionDtos.ElectionResponse(id.toString(), e.getTenant().getId().toString(),
                e.getTenant().getName(), e.getTenant().getCode(), e.getTitle(), e.getDescription(),
                e.getStartsAt(), e.getEndsAt(), e.getStatus().name(), e.isNotifyVoters(),
                e.getCreatedAt(), e.getCountedAt(), posts.countByElectionId(id),
                candidates.countByPostElectionId(id), voters.countByElectionId(id),
                participation.countByElectionId(id));
    }

    private ElectionDtos.PostResponse postView(ElectionPost post) {
        return new ElectionDtos.PostResponse(post.getId().toString(), post.getTitle(), post.getDescription(),
                post.getVotingMode().name(), post.getSortOrder(),
                candidates.findByPostIdOrderBySortOrderAscCreatedAtAsc(post.getId()).size());
    }

    private ElectionDtos.CandidateResponse candidateView(ElectionCandidate c) {
        return new ElectionDtos.CandidateResponse(c.getId().toString(), c.getPost().getId().toString(),
                c.getPost().getTitle(), c.getPanel() == null ? null : c.getPanel().getId().toString(),
                c.getPanel() == null ? null : c.getPanel().getName(), c.getDisplayName(), c.getStatement(),
                c.getPhotoUrl(), c.getStatus().name(), c.getSortOrder(),
                c.getMember() == null ? null : c.getMember().getEmail());
    }

    private List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                out.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        out.add(current.toString().trim());
        return out;
    }

    private static final class IsAadhaarHash {
        private static String hash(String secret, String digits) {
            return CryptoUtils.hmacSha256(secret, "aadhaar:" + digits);
        }
    }
}
