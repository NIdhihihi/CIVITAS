package com.civitas.platform.modules.voting;

import com.civitas.platform.modules.elections.Election;
import com.civitas.platform.modules.elections.ElectionPost;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "votes", uniqueConstraints = @UniqueConstraint(
        name = "uq_vote_election_post_nullifier", columnNames = {"election_id", "post_id", "nullifier"}),
        indexes = @Index(name = "idx_vote_election", columnList = "election_id"))
public class Vote {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private ElectionPost post;

    @Column(nullable = false, length = 64)
    private String nullifier;

    @Column(nullable = false, length = 12000)
    private String encryptedChoice;

    @Column(nullable = false, length = 40)
    private String iv;

    @Column(nullable = false, length = 600)
    private String commitment;

    @Column(nullable = false, length = 600)
    private String proofA;

    @Column(nullable = false, length = 600)
    private String proofS1;

    @Column(nullable = false, length = 600)
    private String proofS2;

    @Column(nullable = false)
    private Instant castAt = Instant.now();

    protected Vote() {
    }

    public Vote(Election election, ElectionPost post, String nullifier, String encryptedChoice, String iv,
                String commitment, String proofA, String proofS1, String proofS2) {
        this.election = election;
        this.post = post;
        this.nullifier = nullifier;
        this.encryptedChoice = encryptedChoice;
        this.iv = iv;
        this.commitment = commitment;
        this.proofA = proofA;
        this.proofS1 = proofS1;
        this.proofS2 = proofS2;
    }

    public UUID getId() { return id; }
    public Election getElection() { return election; }
    public ElectionPost getPost() { return post; }
    public String getNullifier() { return nullifier; }
    public String getEncryptedChoice() { return encryptedChoice; }
    public String getIv() { return iv; }
    public String getCommitment() { return commitment; }
    public String getProofA() { return proofA; }
    public String getProofS1() { return proofS1; }
    public String getProofS2() { return proofS2; }
    public Instant getCastAt() { return castAt; }
}
