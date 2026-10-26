package com.civitas.platform.modules.election;

import com.civitas.platform.modules.tenants.Tenant;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "voter_roll", uniqueConstraints = {
    @UniqueConstraint(name = "uq_voter_election_flat", columnNames = {"election_id", "flat_number"}),
    @UniqueConstraint(name = "uq_voter_election_email", columnNames = {"election_id", "email"})
}, indexes = {
    @Index(name = "idx_voter_roll_election", columnList = "election_id"),
    @Index(name = "idx_voter_roll_email", columnList = "email")
})
public class VoterRollMember {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Column(name = "flat_number", nullable = false, length = 50)
    private String flatNumber;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "aadhaar_ref", length = 30)
    private String aadhaarReference;

    @Column(nullable = false)
    private boolean eligible = true;

    @Column(name = "disqualification_reason", length = 500)
    private String disqualificationReason;

    @Column(name = "has_voted", nullable = false)
    private boolean hasVoted = false;

    @Column(name = "voted_at")
    private Instant votedAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    protected VoterRollMember() {}

    public VoterRollMember(Tenant tenant, Election election, String flatNumber, String fullName,
                           String email, String phoneNumber, String aadhaarReference, boolean eligible,
                           String disqualificationReason) {
        this.tenant = tenant;
        this.election = election;
        this.flatNumber = flatNumber.trim().toUpperCase();
        this.fullName = fullName.trim();
        this.email = email.trim().toLowerCase();
        this.phoneNumber = phoneNumber != null ? phoneNumber.trim() : null;
        this.aadhaarReference = aadhaarReference != null ? aadhaarReference.trim() : null;
        this.eligible = eligible;
        this.disqualificationReason = disqualificationReason != null ? disqualificationReason.trim() : null;
        this.hasVoted = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public Election getElection() { return election; }
    public String getFlatNumber() { return flatNumber; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getAadhaarReference() { return aadhaarReference; }
    public boolean isEligible() { return eligible; }
    public String getDisqualificationReason() { return disqualificationReason; }
    public boolean isHasVoted() { return hasVoted; }
    public Instant getVotedAt() { return votedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setFlatNumber(String flatNumber) { this.flatNumber = flatNumber.trim().toUpperCase(); this.updatedAt = Instant.now(); }
    public void setFullName(String fullName) { this.fullName = fullName.trim(); this.updatedAt = Instant.now(); }
    public void setEmail(String email) { this.email = email.trim().toLowerCase(); this.updatedAt = Instant.now(); }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber != null ? phoneNumber.trim() : null; this.updatedAt = Instant.now(); }
    public void setAadhaarReference(String aadhaarReference) { this.aadhaarReference = aadhaarReference != null ? aadhaarReference.trim() : null; this.updatedAt = Instant.now(); }
    public void setEligible(boolean eligible) { this.eligible = eligible; this.updatedAt = Instant.now(); }
    public void setDisqualificationReason(String disqualificationReason) { this.disqualificationReason = disqualificationReason != null ? disqualificationReason.trim() : null; this.updatedAt = Instant.now(); }
    public void markVoted() { this.hasVoted = true; this.votedAt = Instant.now(); this.updatedAt = Instant.now(); }
}
