package com.civitas.platform.modules.voting;

import com.civitas.platform.modules.elections.Election;
import com.civitas.platform.modules.elections.ElectionVoter;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "voter_participation", uniqueConstraints = {
        @UniqueConstraint(name = "uq_participation_election_voter", columnNames = {"election_id", "voter_id"}),
        @UniqueConstraint(name = "uq_participation_receipt_code", columnNames = {"receipt_code_hash"}),
        @UniqueConstraint(name = "uq_participation_receipt_id", columnNames = {"receipt_id"})
})
public class VoterParticipation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_id", nullable = false)
    private ElectionVoter voter;

    @Column(name = "receipt_id", nullable = false)
    private UUID receiptId = UUID.randomUUID();

    @Column(name = "receipt_code_hash", nullable = false, length = 64)
    private String receiptCodeHash;

    @Column(nullable = false, length = 64)
    private String receiptHash;

    @Column(nullable = false, length = 64)
    private String receiptSignature;

    @Column(nullable = false, length = 64)
    private String nullifierRoot;

    @Column(nullable = false)
    private int postCount;

    @Column(nullable = false)
    private Instant castAt = Instant.now();

    protected VoterParticipation() {
    }

    public VoterParticipation(Election election, ElectionVoter voter, UUID receiptId, String receiptCodeHash,
                              String receiptHash, String receiptSignature, String nullifierRoot, int postCount) {
        this.election = election;
        this.voter = voter;
        this.receiptId = receiptId;
        this.receiptCodeHash = receiptCodeHash;
        this.receiptHash = receiptHash;
        this.receiptSignature = receiptSignature;
        this.nullifierRoot = nullifierRoot;
        this.postCount = postCount;
    }

    public UUID getId() { return id; }
    public Election getElection() { return election; }
    public ElectionVoter getVoter() { return voter; }
    public UUID getReceiptId() { return receiptId; }
    public String getReceiptCodeHash() { return receiptCodeHash; }
    public String getReceiptHash() { return receiptHash; }
    public String getReceiptSignature() { return receiptSignature; }
    public String getNullifierRoot() { return nullifierRoot; }
    public int getPostCount() { return postCount; }
    public Instant getCastAt() { return castAt; }
}
