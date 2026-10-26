package com.civitas.platform.modules.election;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only immutable audit ledger entry backed by a cryptographic hash chain (REQ-4, REQ-7).
 * Conforms to Maharashtra Co-operative Societies Act Sec 73CB legal compliance.
 */
@Entity
@Table(name = "audit_log_entries", indexes = {
    @Index(name = "idx_audit_seq", columnList = "sequence_number", unique = true),
    @Index(name = "idx_audit_election", columnList = "election_id"),
    @Index(name = "idx_audit_event_type", columnList = "event_type"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
public class AuditLogEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sequence_number", nullable = false, unique = true)
    private long sequenceNumber;

    @Column(name = "election_id")
    private UUID electionId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(nullable = false, length = 254)
    private String actor;

    @Column(name = "action_details", length = 2000)
    private String actionDetails;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "device_fingerprint", length = 300)
    private String deviceFingerprint;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    @Column(name = "entry_hash", nullable = false, length = 64)
    private String entryHash;

    protected AuditLogEntry() {}

    public AuditLogEntry(long sequenceNumber, UUID electionId, UUID tenantId, String eventType,
                         String actor, String actionDetails, String ipAddress,
                         String deviceFingerprint, Instant timestamp,
                         String previousHash, String entryHash) {
        this.sequenceNumber = sequenceNumber;
        this.electionId = electionId;
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.actor = actor;
        this.actionDetails = actionDetails;
        this.ipAddress = ipAddress != null ? ipAddress : "127.0.0.1";
        this.deviceFingerprint = deviceFingerprint != null ? deviceFingerprint : "Unknown Device";
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.previousHash = previousHash;
        this.entryHash = entryHash;
    }

    public UUID getId() { return id; }
    public long getSequenceNumber() { return sequenceNumber; }
    public UUID getElectionId() { return electionId; }
    public UUID getTenantId() { return tenantId; }
    public String getEventType() { return eventType; }
    public String getActor() { return actor; }
    public String getActionDetails() { return actionDetails; }
    public String getIpAddress() { return ipAddress; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public Instant getTimestamp() { return timestamp; }
    public String getPreviousHash() { return previousHash; }
    public String getEntryHash() { return entryHash; }
}
