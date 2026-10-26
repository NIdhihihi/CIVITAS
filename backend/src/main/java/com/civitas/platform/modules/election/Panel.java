package com.civitas.platform.modules.election;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "election_panels", uniqueConstraints = {
    @UniqueConstraint(name = "uq_election_panel_name", columnNames = {"election_id", "name"})
})
public class Panel {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String motto;

    @Column(name = "color_hex", length = 10)
    private String colorHex;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Panel() {}

    public Panel(Election election, String name, String motto, String colorHex) {
        this.election = election;
        this.name = name.trim();
        this.motto = motto != null ? motto.trim() : null;
        this.colorHex = colorHex != null ? colorHex.trim() : "#0e7490";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Election getElection() { return election; }
    public String getName() { return name; }
    public String getMotto() { return motto; }
    public String getColorHex() { return colorHex; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name.trim(); }
    public void setMotto(String motto) { this.motto = motto != null ? motto.trim() : null; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex != null ? colorHex.trim() : "#0e7490"; }
}
