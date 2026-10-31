package com.civitas.platform.modules.voting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VoteRepository extends JpaRepository<Vote, UUID> {
    List<Vote> findByElectionId(UUID electionId);
    long countByElectionId(UUID electionId);
    void deleteByElectionId(UUID electionId);
}
