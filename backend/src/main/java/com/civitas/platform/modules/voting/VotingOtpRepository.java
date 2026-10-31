package com.civitas.platform.modules.voting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VotingOtpRepository extends JpaRepository<VotingOtp, UUID> {
    Optional<VotingOtp> findTopByElectionIdAndVoterIdAndUsedAtIsNullOrderByCreatedAtDesc(UUID electionId, UUID voterId);
    void deleteByElectionIdAndVoterId(UUID electionId, UUID voterId);
}
