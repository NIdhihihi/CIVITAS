package com.civitas.platform.modules.election;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {
    Optional<AuditLogEntry> findTopByOrderBySequenceNumberDesc();
    List<AuditLogEntry> findAllByOrderBySequenceNumberAsc();
    List<AuditLogEntry> findAllByOrderBySequenceNumberDesc();
    List<AuditLogEntry> findByElectionIdOrderBySequenceNumberAsc(UUID electionId);
    List<AuditLogEntry> findByTenantIdOrderBySequenceNumberAsc(UUID tenantId);
    long countByEventTypeAndActorAndTimestampAfter(String eventType, String actor, Instant after);
}
