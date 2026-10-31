package com.civitas.platform.modules.elections;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "election_posts", uniqueConstraints = @UniqueConstraint(
        name = "uq_election_post_title", columnNames = {"election_id", "title"}))
public class ElectionPost {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 600)
    private String description = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VotingMode votingMode = VotingMode.BOTH;

    @Column(nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ElectionPost() {
    }

    public ElectionPost(Election election, String title, String description, VotingMode votingMode, int sortOrder) {
        this.election = election;
        this.title = title.trim();
        this.description = description == null ? "" : description.trim();
        this.votingMode = votingMode == null ? VotingMode.BOTH : votingMode;
        this.sortOrder = sortOrder;
    }

    public UUID getId() { return id; }
    public Election getElection() { return election; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public VotingMode getVotingMode() { return votingMode; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }

    public void setTitle(String title) { this.title = title.trim(); }
    public void setDescription(String description) { this.description = description == null ? "" : description.trim(); }
    public void setVotingMode(VotingMode votingMode) { this.votingMode = votingMode; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
