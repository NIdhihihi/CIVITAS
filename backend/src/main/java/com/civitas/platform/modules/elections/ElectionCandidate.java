package com.civitas.platform.modules.elections;

import com.civitas.platform.modules.members.Member;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "election_candidates", indexes = {
        @Index(name = "idx_election_candidate_post", columnList = "post_id"),
        @Index(name = "idx_election_candidate_panel", columnList = "panel_id")
})
public class ElectionCandidate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private ElectionPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "panel_id")
    private Panel panel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(nullable = false, length = 140)
    private String displayName;

    @Column(nullable = false, length = 1200)
    private String statement = "";

    @Column(length = 600)
    private String photoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CandidateStatus status = CandidateStatus.APPROVED;

    @Column(nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ElectionCandidate() {
    }

    public ElectionCandidate(ElectionPost post, Panel panel, Member member, String displayName,
                             String statement, String photoUrl, int sortOrder) {
        this.post = post;
        this.panel = panel;
        this.member = member;
        this.displayName = displayName.trim();
        this.statement = statement == null ? "" : statement.trim();
        this.photoUrl = photoUrl;
        this.sortOrder = sortOrder;
    }

    public UUID getId() { return id; }
    public ElectionPost getPost() { return post; }
    public Panel getPanel() { return panel; }
    public Member getMember() { return member; }
    public String getDisplayName() { return displayName; }
    public String getStatement() { return statement; }
    public String getPhotoUrl() { return photoUrl; }
    public CandidateStatus getStatus() { return status; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }

    public void setDisplayName(String displayName) { this.displayName = displayName.trim(); }
    public void setStatement(String statement) { this.statement = statement == null ? "" : statement.trim(); }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public void setPanel(Panel panel) { this.panel = panel; }
    public void setStatus(CandidateStatus status) { this.status = status; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
