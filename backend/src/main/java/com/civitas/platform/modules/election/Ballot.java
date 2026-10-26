package com.civitas.platform.modules.election;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Anonymized Ballot: Does NOT store member_id or voter_id to guarantee zero-knowledge ballot secrecy (REQ-6).
 * Linked to a unique cryptographic receipt hash for voter verification (REQ-5, REQ-VOTE-6).
 */
@Entity
@Table(name = "ballots", indexes = {
    @Index(name = "idx_ballot_election", columnList = "election_id"),
    @Index(name = "idx_ballot_receipt_code", columnList = "receipt_code", unique = true),
    @Index(name = "idx_ballot_hash", columnList = "ballot_hash", unique = true)
})
public class Ballot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Column(name = "receipt_code", nullable = false, unique = true, length = 64)
    private String receiptCode;

    @Column(name = "ballot_hash", nullable = false, unique = true, length = 128)
    private String ballotHash;

    @Column(name = "is_panel_vote", nullable = false)
    private boolean isPanelVote = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "panel_id")
    private Panel panel;

    @Column(name = "cast_at", nullable = false)
    private Instant castAt = Instant.now();

    protected Ballot() {}

    public Ballot(Election election, String receiptCode, String ballotHash, boolean isPanelVote, Panel panel) {
        this.election = election;
        this.receiptCode = receiptCode;
        this.ballotHash = ballotHash;
        this.isPanelVote = isPanelVote;
        this.panel = panel;
        this.castAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Election getElection() { return election; }
    public String getReceiptCode() { return receiptCode; }
    public String getBallotHash() { return ballotHash; }
    public boolean isPanelVote() { return isPanelVote; }
    public Panel getPanel() { return panel; }
    public Instant getCastAt() { return castAt; }
}
