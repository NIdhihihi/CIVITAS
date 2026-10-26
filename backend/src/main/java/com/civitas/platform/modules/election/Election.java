package com.civitas.platform.modules.election;

import com.civitas.platform.modules.members.Member;
import com.civitas.platform.modules.tenants.Tenant;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "elections", indexes = {
    @Index(name = "idx_election_tenant", columnList = "tenant_id"),
    @Index(name = "idx_election_status", columnList = "status")
})
public class Election {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ElectionStatus status = ElectionStatus.DRAFT;

    @Column(name = "voting_start_time")
    private Instant votingStartTime;

    @Column(name = "voting_end_time")
    private Instant votingEndTime;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_member_id", nullable = false)
    private Member createdBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    protected Election() {}

    public Election(Tenant tenant, String title, String description, Instant votingStartTime, Instant votingEndTime, Member createdBy) {
        this.tenant = tenant;
        this.title = title.trim();
        this.description = description != null ? description.trim() : null;
        this.votingStartTime = votingStartTime;
        this.votingEndTime = votingEndTime;
        this.createdBy = createdBy;
        this.status = ElectionStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ElectionStatus getStatus() { return status; }
    public Instant getVotingStartTime() { return votingStartTime; }
    public Instant getVotingEndTime() { return votingEndTime; }
    public Member getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setTitle(String title) { this.title = title.trim(); this.updatedAt = Instant.now(); }
    public void setDescription(String description) { this.description = description != null ? description.trim() : null; this.updatedAt = Instant.now(); }
    public void setStatus(ElectionStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    public void setVotingStartTime(Instant votingStartTime) { this.votingStartTime = votingStartTime; this.updatedAt = Instant.now(); }
    public void setVotingEndTime(Instant votingEndTime) { this.votingEndTime = votingEndTime; this.updatedAt = Instant.now(); }

    public boolean isVotingOpen() {
        return this.status == ElectionStatus.VOTING_OPEN;
    }

    public boolean isModifiable() {
        return this.status == ElectionStatus.DRAFT || this.status == ElectionStatus.CONFIGURED;
    }
}
