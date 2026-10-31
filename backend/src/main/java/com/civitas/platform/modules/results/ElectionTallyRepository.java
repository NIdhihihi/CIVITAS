package com.civitas.platform.modules.results;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ElectionTallyRepository extends JpaRepository<ElectionTally, UUID> {
    List<ElectionTally> findByElectionIdOrderByPostSortOrderAscVotesDesc(UUID electionId);
    long countByElectionId(UUID electionId);
    void deleteByElectionId(UUID electionId);
}
