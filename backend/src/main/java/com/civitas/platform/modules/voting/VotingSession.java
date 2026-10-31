package com.civitas.platform.modules.voting;

import com.civitas.platform.modules.elections.Election;
import com.civitas.platform.modules.elections.ElectionVoter;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "voting_sessions", indexes = @Index(name = "idx_voting_session_token", columnList = "token_hash", unique = true))
public class VotingSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_id", nullable = false)
    private ElectionVoter voter;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VotingAuthMethod method;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant expiresAt;

    @Column
    private Instant authenticatedAt;

    @Column
    private Instant confirmationExpiresAt;

    @Column(length = 100)
    private String confirmationHash;

    @Column(nullable = false)
    private int confirmationAttempts = 0;

    @Column(length = 64)
    private String selectionHash;

    @Column
    private Instant consumedAt;

    protected VotingSession() {
    }

    public VotingSession(Election election, ElectionVoter voter, String tokenHash, VotingAuthMethod method,
                         Instant expiresAt) {
        this.election = election;
        this.voter = voter;
        this.tokenHash = tokenHash;
        this.method = method;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public Election getElection() { return election; }
    public ElectionVoter getVoter() { return voter; }
    public String getTokenHash() { return tokenHash; }
    public VotingAuthMethod getMethod() { return method; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getAuthenticatedAt() { return authenticatedAt; }
    public Instant getConfirmationExpiresAt() { return confirmationExpiresAt; }
    public String getConfirmationHash() { return confirmationHash; }
    public int getConfirmationAttempts() { return confirmationAttempts; }
    public String getSelectionHash() { return selectionHash; }
    public Instant getConsumedAt() { return consumedAt; }

    public void authenticate() { this.authenticatedAt = Instant.now(); }
    public void setConfirmation(String hash, Instant expiresAt) {
        this.confirmationHash = hash;
        this.confirmationExpiresAt = expiresAt;
        this.confirmationAttempts = 0;
    }
    public void setSelectionHash(String selectionHash) { this.selectionHash = selectionHash; }
    public void incrementConfirmationAttempts() { this.confirmationAttempts++; }
    public void cancelConfirmation() {
        this.confirmationHash = null;
        this.confirmationExpiresAt = null;
        this.confirmationAttempts = 0;
        this.selectionHash = null;
    }
    public void consume() { this.consumedAt = Instant.now(); }
}
