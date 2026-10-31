package com.civitas.platform.modules.elections;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ElectionCandidateRepository extends JpaRepository<ElectionCandidate, UUID> {
    List<ElectionCandidate> findByPostIdOrderBySortOrderAscCreatedAtAsc(UUID postId);
    List<ElectionCandidate> findByPostElectionIdOrderByPostSortOrderAscSortOrderAscCreatedAtAsc(UUID electionId);
    List<ElectionCandidate> findByPostElectionIdAndStatusOrderByPostSortOrderAscSortOrderAscCreatedAtAsc(UUID electionId, CandidateStatus status);
    List<ElectionCandidate> findByPanelId(UUID panelId);
    long countByPostElectionId(UUID electionId);
}
