package com.civitas.platform.modules.elections;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "panels", uniqueConstraints = @UniqueConstraint(
        name = "uq_panel_election_name", columnNames = {"election_id", "name"}))
public class Panel {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(nullable = false, length = 800)
    private String description = "";

    @Column(length = 600)
    private String photoUrl;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Panel() {
    }

    public Panel(Election election, String name, String description, String photoUrl) {
        this.election = election;
        this.name = name.trim();
        this.description = description == null ? "" : description.trim();
        this.photoUrl = photoUrl;
    }

    public UUID getId() { return id; }
    public Election getElection() { return election; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getPhotoUrl() { return photoUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public void setDescription(String description) { this.description = description == null ? "" : description.trim(); }
}
