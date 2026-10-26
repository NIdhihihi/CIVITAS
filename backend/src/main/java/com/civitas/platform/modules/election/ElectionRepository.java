package com.civitas.platform.modules.election;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ElectionRepository extends JpaRepository<Election, UUID> {
    List<Election> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<Election> findByStatusOrderByVotingStartTimeAsc(ElectionStatus status);
}
