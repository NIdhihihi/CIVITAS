package com.civitas.platform.modules.voting;

import com.civitas.platform.modules.elections.Election;
import com.civitas.platform.modules.elections.ElectionVoter;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "voting_otps", indexes = {
        @Index(name = "idx_voting_otp_voter", columnList = "voter_id,election_id"),
        @Index(name = "idx_voting_otp_created", columnList = "created_at")
})
public class VotingOtp {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_id", nullable = false)
    private ElectionVoter voter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VotingAuthMethod method;

    @Column(nullable = false, length = 100)
    private String codeHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant usedAt;

    protected VotingOtp() {
    }

    public VotingOtp(Election election, ElectionVoter voter, VotingAuthMethod method, String codeHash, Instant expiresAt) {
        this.election = election;
        this.voter = voter;
        this.method = method;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public Election getElection() { return election; }
    public ElectionVoter getVoter() { return voter; }
    public VotingAuthMethod getMethod() { return method; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUsedAt() { return usedAt; }

    public void incrementAttempts() { this.attempts++; }
    public void markUsed() { this.usedAt = Instant.now(); }
}
