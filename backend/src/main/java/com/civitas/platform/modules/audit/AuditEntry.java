package com.civitas.platform.modules.audit;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_ledger", indexes = {
        @Index(name = "idx_audit_sequence", columnList = "sequence_number", unique = true),
        @Index(name = "idx_audit_election", columnList = "election_id"),
        @Index(name = "idx_audit_created", columnList = "created_at")
})
public class AuditEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sequence_number", nullable = false)
    private long sequence;

    @Column(length = 80)
    private String tenantId;

    @Column(length = 80)
    private String electionId;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(nullable = false, length = 20)
    private String actorType;

    @Column(nullable = false, length = 64)
    private String actorHash;

    @Column(length = 60)
    private String resourceType;

    @Column(length = 100)
    private String resourceId;

    @Column(nullable = false, length = 64)
    private String ipHash;

    @Column(nullable = false, length = 64)
    private String deviceHash;

    @Column(nullable = false, length = 4000)
    private String metadata = "{}";

    @Column(nullable = false, length = 64)
    private String previousHash;

    @Column(nullable = false, length = 64)
    private String entryHash;

    @Column(nullable = false, length = 64)
    private String signature;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected AuditEntry() {
    }

    public AuditEntry(String tenantId, String electionId, String action, String actorType, String actorHash,
                      String resourceType, String resourceId, String ipHash, String deviceHash,
                      String metadata, String previousHash, String entryHash, String signature, Instant createdAt) {
        this.tenantId = tenantId;
        this.electionId = electionId;
        this.action = action;
        this.actorType = actorType;
        this.actorHash = actorHash;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.ipHash = ipHash;
        this.deviceHash = deviceHash;
        this.metadata = metadata;
        this.previousHash = previousHash;
        this.entryHash = entryHash;
        this.signature = signature;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public long getSequence() { return sequence; }
    public void setSequence(long sequence) { this.sequence = sequence; }
    public String getTenantId() { return tenantId; }
    public String getElectionId() { return electionId; }
    public String getAction() { return action; }
    public String getActorType() { return actorType; }
    public String getActorHash() { return actorHash; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getIpHash() { return ipHash; }
    public String getDeviceHash() { return deviceHash; }
    public String getMetadata() { return metadata; }
    public String getPreviousHash() { return previousHash; }
    public String getEntryHash() { return entryHash; }
    public String getSignature() { return signature; }
    public Instant getCreatedAt() { return createdAt; }
}
