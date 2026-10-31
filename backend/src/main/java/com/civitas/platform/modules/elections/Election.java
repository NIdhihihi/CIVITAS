package com.civitas.platform.modules.elections;

import com.civitas.platform.modules.members.Member;
import com.civitas.platform.modules.tenants.Tenant;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "elections", indexes = {
        @Index(name = "idx_election_tenant", columnList = "tenant_id"),
        @Index(name = "idx_election_status_window", columnList = "status,starts_at,ends_at")
})
public class Election {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 1600)
    private String description = "";

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ElectionStatus status = ElectionStatus.DRAFT;

    @Column(nullable = false)
    private boolean notifyVoters = true;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_member_id", nullable = false)
    private Member createdBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Column
    private Instant openedAt;

    @Column
    private Instant closedAt;

    @Column
    private Instant countedAt;

    @Version
    private long version;

    protected Election() {
    }

    public Election(Tenant tenant, String title, String description, Instant startsAt, Instant endsAt,
                    boolean notifyVoters, Member createdBy) {
        this.tenant = tenant;
        this.title = title.trim();
        this.description = description == null ? "" : description.trim();
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.notifyVoters = notifyVoters;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public ElectionStatus getStatus() { return status; }
    public boolean isNotifyVoters() { return notifyVoters; }
    public Member getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getCountedAt() { return countedAt; }
    public long getVersion() { return version; }

    public void setTitle(String title) { this.title = title.trim(); this.updatedAt = Instant.now(); }
    public void setDescription(String description) { this.description = description == null ? "" : description.trim(); this.updatedAt = Instant.now(); }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; this.updatedAt = Instant.now(); }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; this.updatedAt = Instant.now(); }
    public void setNotifyVoters(boolean notifyVoters) { this.notifyVoters = notifyVoters; this.updatedAt = Instant.now(); }
    public void setStatus(ElectionStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public void setCountedAt(Instant countedAt) { this.countedAt = countedAt; }
}
