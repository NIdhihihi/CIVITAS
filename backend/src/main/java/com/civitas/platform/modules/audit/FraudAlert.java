package com.civitas.platform.modules.audit;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_alerts", indexes = {
        @Index(name = "idx_fraud_created", columnList = "created_at"),
        @Index(name = "idx_fraud_status", columnList = "resolved")
})
public class FraudAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 60)
    private String type;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 1200)
    private String details;

    @Column(length = 80)
    private String electionId;

    @Column(nullable = false, length = 64)
    private String ipHash;

    @Column(nullable = false, length = 64)
    private String deviceHash;

    @Column(nullable = false)
    private boolean resolved = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant resolvedAt;

    protected FraudAlert() {
    }

    public FraudAlert(String type, String severity, String details, String electionId,
                      String ipHash, String deviceHash) {
        this.type = type;
        this.severity = severity;
        this.details = details;
        this.electionId = electionId;
        this.ipHash = ipHash;
        this.deviceHash = deviceHash;
    }

    public UUID getId() { return id; }
    public String getType() { return type; }
    public String getSeverity() { return severity; }
    public String getDetails() { return details; }
    public String getElectionId() { return electionId; }
    public String getIpHash() { return ipHash; }
    public String getDeviceHash() { return deviceHash; }
    public boolean isResolved() { return resolved; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void resolve() { this.resolved = true; this.resolvedAt = Instant.now(); }
}
