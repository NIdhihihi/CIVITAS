package com.civitas.platform.modules.election;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "election_posts", uniqueConstraints = {
    @UniqueConstraint(name = "uq_election_post_title", columnNames = {"election_id", "title"})
})
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(name = "max_selections", nullable = false)
    private int maxSelections = 1;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Post() {}

    public Post(Election election, String title, String description, int maxSelections, int displayOrder) {
        this.election = election;
        this.title = title.trim();
        this.description = description != null ? description.trim() : null;
        this.maxSelections = Math.max(1, maxSelections);
        this.displayOrder = displayOrder;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Election getElection() { return election; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getMaxSelections() { return maxSelections; }
    public int getDisplayOrder() { return displayOrder; }
    public Instant getCreatedAt() { return createdAt; }

    public void setTitle(String title) { this.title = title.trim(); }
    public void setDescription(String description) { this.description = description != null ? description.trim() : null; }
    public void setMaxSelections(int maxSelections) { this.maxSelections = Math.max(1, maxSelections); }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}
