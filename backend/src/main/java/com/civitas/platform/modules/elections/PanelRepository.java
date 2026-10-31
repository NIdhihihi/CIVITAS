package com.civitas.platform.modules.elections;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PanelRepository extends JpaRepository<Panel, UUID> {
    List<Panel> findByElectionIdOrderByCreatedAtAsc(UUID electionId);
    Optional<Panel> findByIdAndElectionId(UUID id, UUID electionId);
    boolean existsByElectionIdAndNameIgnoreCase(UUID electionId, String name);
}
