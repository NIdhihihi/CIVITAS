package com.civitas.platform.modules.demo;

import com.civitas.platform.modules.elections.*;
import com.civitas.platform.modules.members.Member;
import com.civitas.platform.modules.members.MemberRepository;
import com.civitas.platform.modules.members.Role;
import com.civitas.platform.modules.onboarding.Candidate;
import com.civitas.platform.modules.onboarding.CandidateRepository;
import com.civitas.platform.modules.tenants.Tenant;
import com.civitas.platform.modules.tenants.TenantMembership;
import com.civitas.platform.modules.tenants.TenantMembershipRepository;
import com.civitas.platform.modules.tenants.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Idempotent, opt-in dataset for a local workflow walkthrough. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String SOCIETY_CODE = "CIVITASDEMO";
    private static final String LIVE_ELECTION = "CIVITAS Demo - Live Society Election";
    private static final String PANEL_ELECTION = "CIVITAS Demo - Panel Ballot Showcase";
    private static final String SCHEDULED_ELECTION = "CIVITAS Demo - Scheduled Community Poll";
    private static final String SETUP_ELECTION = "CIVITAS Demo - Candidate Setup Practice";

    private final TransactionTemplate transactions;
    private final MemberRepository members;
    private final TenantRepository tenants;
    private final TenantMembershipRepository memberships;
    private final ElectionRepository elections;
    private final ElectionPostRepository posts;
    private final ElectionCandidateRepository electionCandidates;
    private final ElectionVoterRepository voters;
    private final PanelRepository panels;
    private final CandidateRepository candidateApplications;
    private final PasswordEncoder passwords;
    private final String adminEmail;
    private final String adminPassword;
    private final String demoPassword;
    private final boolean enabled;

    public DemoDataSeeder(TransactionTemplate transactions, MemberRepository members,
                          TenantRepository tenants, TenantMembershipRepository memberships,
                          ElectionRepository elections, ElectionPostRepository posts,
                          ElectionCandidateRepository electionCandidates, ElectionVoterRepository voters,
                          PanelRepository panels, CandidateRepository candidateApplications,
                          PasswordEncoder passwords,
                          @Value("${app.bootstrap-admin.email:admin@civitas.local}") String adminEmail,
                          @Value("${app.bootstrap-admin.password:civitas@123}") String adminPassword,
                          @Value("${civitas.demo.password:civitas-demo-123}") String demoPassword,
                          @Value("${civitas.demo.seed.enabled:false}") boolean enabled) {
        this.transactions = transactions;
        this.members = members;
        this.tenants = tenants;
        this.memberships = memberships;
        this.elections = elections;
        this.posts = posts;
        this.electionCandidates = electionCandidates;
        this.voters = voters;
        this.panels = panels;
        this.candidateApplications = candidateApplications;
        this.passwords = passwords;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.demoPassword = demoPassword;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (demoPassword == null || demoPassword.length() < 8) {
            throw new IllegalStateException("CIVITAS_DEMO_PASSWORD must contain at least 8 characters.");
        }
        transactions.executeWithoutResult(status -> seed());
    }

    private void seed() {
        Member admin = ensureMember(adminEmail, "CIVITAS Demo Administrator", adminPassword, Role.PLATFORM_ADMIN);
        Tenant society = tenants.findByCodeIgnoreCase(SOCIETY_CODE)
                .orElseGet(() -> tenants.save(new Tenant("CIVITAS Demo Society", SOCIETY_CODE, "Pune", admin)));

        Member voterMember = ensureMember("demo.voter@civitas.local", "Demo Resident Voter", demoPassword, Role.USER);
        Member candidateOne = ensureMember("demo.candidate@civitas.local", "Demo Candidate One", demoPassword, Role.CANDIDATE);
        Member candidateTwo = ensureMember("demo.candidate2@civitas.local", "Demo Candidate Two", demoPassword, Role.CANDIDATE);
        ensureMembership(admin, society);
        ensureMembership(voterMember, society);
        ensureMembership(candidateOne, society);
        ensureMembership(candidateTwo, society);

        Instant now = Instant.now();
        Election live = ensureElection(society, admin, LIVE_ELECTION, now.minus(1, ChronoUnit.HOURS),
                now.plus(7, ChronoUnit.DAYS), ElectionStatus.ACTIVE);
        Election panelShowcase = ensureElection(society, admin, PANEL_ELECTION, now.minus(1, ChronoUnit.HOURS),
                now.plus(7, ChronoUnit.DAYS), ElectionStatus.ACTIVE);
        Election scheduled = ensureElection(society, admin, SCHEDULED_ELECTION, now.plus(14, ChronoUnit.DAYS),
                now.plus(15, ChronoUnit.DAYS), ElectionStatus.SCHEDULED);
        Election practice = ensureElection(society, admin, SETUP_ELECTION, now.plus(1, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS), ElectionStatus.DRAFT);

        ElectionPost chairPost = ensurePost(live, "Chairperson", VotingMode.BOTH, 0);
        ElectionPost secretaryPost = ensurePost(live, "Secretary", VotingMode.CANDIDATE, 1);
        ensureElectionCandidate(chairPost, null, null, "Asha Rao", "Experienced resident focused on transparent budgets and reliable maintenance.", 0);
        ensureElectionCandidate(chairPost, null, null, "Rohan Shah", "Building a safer, greener, and more connected community.", 1);
        Panel unity = panels.findByElectionIdOrderByCreatedAtAsc(live.getId()).stream()
                .filter(p -> p.getName().equalsIgnoreCase("Unity Team")).findFirst()
                .orElseGet(() -> panels.save(new Panel(live, "Unity Team", "A team focused on practical resident services.", null)));
        ensureElectionCandidate(chairPost, unity, null, "Asha Rao", "Experienced resident focused on transparent budgets and reliable maintenance.", 2);
        ensureElectionCandidate(secretaryPost, null, null, "Maya Patel", "Clear communication, timely follow-ups, and accessible records.", 0);
        ensureElectionCandidate(secretaryPost, null, null, "Dev Kulkarni", "A member-first approach to community planning.", 1);

        ensureVoter(live, voterMember, voterMember.getFullName(), voterMember.getEmail(), "9876500101");
        ensureVoter(live, null, "Asha Demo Resident", "asha.demo@civitas.local", "9876500102");
        ensureVoter(live, null, "Rohan Demo Resident", "rohan.demo@civitas.local", "9876500103");

        ElectionPost panelChair = ensurePost(panelShowcase, "Community Chair", VotingMode.BOTH, 0);
        ElectionPost panelSecretary = ensurePost(panelShowcase, "Community Secretary", VotingMode.BOTH, 1);
        Panel careTeam = ensurePanel(panelShowcase, "Care & Connect Team",
                "A slate focused on resident support and better communication.");
        Panel greenTeam = ensurePanel(panelShowcase, "Green Neighbourhood Team",
                "A slate focused on greener shared spaces and practical upkeep.");
        ensureElectionCandidate(panelChair, careTeam, candidateOne, "Anaya Mehta",
                "A welcoming community with responsive resident support.", 0);
        ensureElectionCandidate(panelSecretary, careTeam, candidateTwo, "Kabir Shah",
                "Clear notices and reliable follow-through on resident requests.", 0);
        ensureElectionCandidate(panelChair, greenTeam, null, "Ira Desai",
                "More shade, cleaner shared spaces, and visible maintenance plans.", 1);
        ensureElectionCandidate(panelSecretary, greenTeam, null, "Neil Kapoor",
                "Simple digital updates and predictable meeting notes.", 1);
        ensureElectionCandidate(panelChair, null, null, "Tara Nair",
                "An independent option focused on accessibility and inclusion.", 2);
        ensureElectionCandidate(panelSecretary, null, null, "Arjun Rao",
                "Independent, timely communication for every building.", 2);
        ensureVoter(panelShowcase, voterMember, voterMember.getFullName(), voterMember.getEmail(), "9876500101");
        ensureVoter(panelShowcase, null, "Meera Demo Resident", "meera.demo@civitas.local", "9876500104");

        ElectionPost scheduledPost = ensurePost(scheduled, "Shared Facilities Coordinator", VotingMode.CANDIDATE, 0);
        ensureElectionCandidate(scheduledPost, null, null, "Nisha Verma",
                "A transparent booking calendar and fair access to shared spaces.", 0);
        ensureElectionCandidate(scheduledPost, null, null, "Omar Fernandes",
                "Better maintenance reporting and a clear schedule for common facilities.", 1);
        ensureVoter(scheduled, voterMember, voterMember.getFullName(), voterMember.getEmail(), "9876500101");
        ensureVoter(scheduled, null, "Meera Demo Resident", "meera.demo@civitas.local", "9876500104");

        ElectionPost practiceTreasurer = ensurePost(practice, "Treasurer", VotingMode.CANDIDATE, 0);
        ensureCandidateApplication(candidateOne, society, "Treasurer",
                "I will publish clear monthly summaries and make the budget easy for every resident to understand.", "9876500201");
        ensureCandidateApplication(candidateTwo, society, "Secretary",
                "I will make notices timely, meeting notes searchable, and resident requests easy to track.", "9876500202");
        log.info("CIVITAS demo data is ready: society {}, active elections {}, {}, scheduled election {}, draft election {} (practice post {}).",
                society.getCode(), live.getTitle(), panelShowcase.getTitle(), scheduled.getTitle(), practice.getTitle(), practiceTreasurer.getTitle());
        log.info("Demo accounts use the configured CIVITAS_DEMO_PASSWORD. Candidate accounts: demo.candidate@civitas.local and demo.candidate2@civitas.local; voter account: demo.voter@civitas.local.");
    }

    private Member ensureMember(String email, String fullName, String password, Role role) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("Set CIVITAS_ADMIN_EMAIL and CIVITAS_ADMIN_PASSWORD before enabling demo data.");
        }
        Member member = members.findByEmailIgnoreCase(normalized).orElseGet(() -> new Member(normalized, fullName));
        member.setFullName(fullName);
        member.setPasswordHash(passwords.encode(password));
        member.setRole(role);
        member.setEmailVerified(true);
        return members.save(member);
    }

    private void ensureMembership(Member member, Tenant society) {
        if (!memberships.existsByMemberEmailIgnoreCaseAndTenantId(member.getEmail(), society.getId())) {
            memberships.save(new TenantMembership(member, society));
        }
    }

    private Election ensureElection(Tenant society, Member admin, String title, Instant startsAt,
                                   Instant endsAt, ElectionStatus initialStatus) {
        return elections.findByTenantIdAndTitleIgnoreCase(society.getId(), title).orElseGet(() -> {
            Election election = new Election(society, title,
                    "Seeded walkthrough data. Use this election to explore the CIVITAS " + initialStatus.name().toLowerCase() + " workflow.",
                    startsAt, endsAt, false, admin);
            election.setStatus(initialStatus);
            if (initialStatus == ElectionStatus.ACTIVE) election.setOpenedAt(startsAt);
            return elections.save(election);
        });
    }

    private ElectionPost ensurePost(Election election, String title, VotingMode mode, int order) {
        return posts.findByElectionIdOrderBySortOrderAscCreatedAtAsc(election.getId()).stream()
                .filter(post -> post.getTitle().equalsIgnoreCase(title)).findFirst()
                .orElseGet(() -> posts.save(new ElectionPost(election, title,
                        "Demo ballot for " + title.toLowerCase() + ".", mode, order)));
    }

    private Panel ensurePanel(Election election, String name, String description) {
        return panels.findByElectionIdOrderByCreatedAtAsc(election.getId()).stream()
                .filter(panel -> panel.getName().equalsIgnoreCase(name)).findFirst()
                .orElseGet(() -> panels.save(new Panel(election, name, description, null)));
    }

    private void ensureElectionCandidate(ElectionPost post, Panel panel, Member member,
                                         String name, String statement, int order) {
        boolean exists = electionCandidates.findByPostIdOrderBySortOrderAscCreatedAtAsc(post.getId()).stream()
                .anyMatch(candidate -> candidate.getDisplayName().equalsIgnoreCase(name)
                        && (panel == null ? candidate.getPanel() == null : panel.equals(candidate.getPanel())));
        if (!exists) electionCandidates.save(new ElectionCandidate(post, panel, member, name, statement, null, order));
    }

    private void ensureVoter(Election election, Member member, String fullName, String email, String phone) {
        if (voters.existsByElectionIdAndEmailIgnoreCase(election.getId(), email)) return;
        ElectionVoter voter = new ElectionVoter(election, fullName, email, phone, null, null, "");
        voter.setMember(member);
        voters.save(voter);
    }

    private void ensureCandidateApplication(Member member, Tenant society, String post, String statement, String phone) {
        candidateApplications.findFirstByMemberIdAndTenantIdAndContestingPostIgnoreCase(
                member.getId(), society.getId(), post).orElseGet(() ->
                candidateApplications.save(new Candidate(member, society, post, statement, phone)));
    }
}
