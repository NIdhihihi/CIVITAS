package com.civitas.platform.modules.elections;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ElectionPostRepository extends JpaRepository<ElectionPost, UUID> {
    List<ElectionPost> findByElectionIdOrderBySortOrderAscCreatedAtAsc(UUID electionId);
    Optional<ElectionPost> findByIdAndElectionId(UUID id, UUID electionId);
    boolean existsByElectionIdAndTitleIgnoreCase(UUID electionId, String title);
    long countByElectionId(UUID electionId);
}
