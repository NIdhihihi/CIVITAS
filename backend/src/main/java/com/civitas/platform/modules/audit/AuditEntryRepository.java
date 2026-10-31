package com.civitas.platform.modules.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {
    Optional<AuditEntry> findTopByOrderBySequenceDesc();
    List<AuditEntry> findAllByOrderBySequenceDesc(Pageable pageable);
    List<AuditEntry> findByElectionIdOrderBySequenceDesc(String electionId, Pageable pageable);
    List<AuditEntry> findByElectionIdOrderBySequenceAsc(String electionId);
    List<AuditEntry> findAllByOrderBySequenceAsc();
    long countByActionAndIpHashAndCreatedAtAfter(String action, String ipHash, Instant after);
    long countByActionAndActorHashAndCreatedAtAfter(String action, String actorHash, Instant after);
    Optional<AuditEntry> findTopByActorHashAndActionOrderByCreatedAtDesc(String actorHash, String action);
    Optional<AuditEntry> findTopByActorHashAndActionAndIpHashNotOrderByCreatedAtDesc(String actorHash, String action, String ipHash);
}
