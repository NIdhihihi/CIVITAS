package com.civitas.platform.modules.voting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VotingSessionRepository extends JpaRepository<VotingSession, UUID> {
    Optional<VotingSession> findByTokenHash(String tokenHash);
    boolean existsByElectionIdAndVoterIdAndConsumedAtIsNullAndExpiresAtAfter(UUID electionId, UUID voterId, java.time.Instant now);
}
