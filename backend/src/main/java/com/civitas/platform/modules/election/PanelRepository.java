package com.civitas.platform.modules.election;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface PanelRepository extends JpaRepository<Panel, UUID> {
    List<Panel> findByElectionIdOrderByNameAsc(UUID electionId);
    boolean existsByElectionIdAndNameIgnoreCase(UUID electionId, String name);
}
