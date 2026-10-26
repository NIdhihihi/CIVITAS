package com.civitas.platform.modules.election;

import com.civitas.platform.modules.members.Member;
import com.civitas.platform.modules.onboarding.CandidateStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "election_candidates", uniqueConstraints = {
    @UniqueConstraint(name = "uq_election_post_candidate_email", columnNames = {"election_id", "post_id", "email"})
})
public class ElectionCandidate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "panel_id")
    private Panel panel;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 1500)
    private String statement;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CandidateStatus status = CandidateStatus.PENDING;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt = Instant.now();

    protected ElectionCandidate() {}

    public ElectionCandidate(Election election, Post post, Member member, Panel panel,
                             String fullName, String email, String phone, String statement, String photoUrl) {
        this.election = election;
        this.post = post;
        this.member = member;
        this.panel = panel;
        this.fullName = fullName.trim();
        this.email = email.trim().toLowerCase();
        this.phone = phone != null ? phone.trim() : null;
        this.statement = statement != null ? statement.trim() : null;
        this.photoUrl = photoUrl != null ? photoUrl.trim() : null;
        this.status = CandidateStatus.PENDING;
        this.registeredAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Election getElection() { return election; }
    public Post getPost() { return post; }
    public Member getMember() { return member; }
    public Panel getPanel() { return panel; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getStatement() { return statement; }
    public String getPhotoUrl() { return photoUrl; }
    public CandidateStatus getStatus() { return status; }
    public Instant getRegisteredAt() { return registeredAt; }

    public void setPanel(Panel panel) { this.panel = panel; }
    public void setPost(Post post) { this.post = post; }
    public void setStatement(String statement) { this.statement = statement != null ? statement.trim() : null; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl != null ? photoUrl.trim() : null; }
    public void setPhone(String phone) { this.phone = phone != null ? phone.trim() : null; }
    public void setStatus(CandidateStatus status) { this.status = status; }
}
