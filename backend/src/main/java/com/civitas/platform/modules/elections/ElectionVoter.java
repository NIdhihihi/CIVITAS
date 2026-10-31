package com.civitas.platform.modules.elections;

import com.civitas.platform.modules.members.Member;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "election_voters", uniqueConstraints = {
        @UniqueConstraint(name = "uq_election_voter_email", columnNames = {"election_id", "email"}),
        @UniqueConstraint(name = "uq_election_voter_aadhaar", columnNames = {"election_id", "aadhaar_hash"})
}, indexes = @Index(name = "idx_election_voter_election", columnList = "election_id"))
public class ElectionVoter {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(nullable = false, length = 140)
    private String fullName;

    @Column(length = 254)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(name = "aadhaar_hash", length = 64)
    private String aadhaarHash;

    @Column(length = 4)
    private String aadhaarLast4;

    /** Comma separated post UUIDs. Empty means eligible for every post. */
    @Column(length = 2000)
    private String eligiblePostIds = "";

    @Column(nullable = false)
    private boolean voted = false;

    @Column
    private Instant votedAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ElectionVoter() {
    }

    public ElectionVoter(Election election, String fullName, String email, String phone,
                         String aadhaarHash, String aadhaarLast4, String eligiblePostIds) {
        this.election = election;
        this.fullName = fullName.trim();
        this.email = email == null ? null : email.trim().toLowerCase();
        this.phone = phone == null ? null : phone.trim();
        this.aadhaarHash = aadhaarHash;
        this.aadhaarLast4 = aadhaarLast4;
        this.eligiblePostIds = eligiblePostIds == null ? "" : eligiblePostIds;
    }

    public UUID getId() { return id; }
    public Election getElection() { return election; }
    public Member getMember() { return member; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAadhaarHash() { return aadhaarHash; }
    public String getAadhaarLast4() { return aadhaarLast4; }
    public String getEligiblePostIds() { return eligiblePostIds; }
    public boolean isVoted() { return voted; }
    public Instant getVotedAt() { return votedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setMember(Member member) { this.member = member; }
    public void setEligiblePostIds(String eligiblePostIds) { this.eligiblePostIds = eligiblePostIds == null ? "" : eligiblePostIds; }
    public void markVoted(Instant at) { this.voted = true; this.votedAt = at; }
}
