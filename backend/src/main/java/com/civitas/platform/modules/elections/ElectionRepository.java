package com.civitas.platform.modules.elections;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ElectionRepository extends JpaRepository<Election, UUID> {
    java.util.Optional<Election> findByTenantIdAndTitleIgnoreCase(UUID tenantId, String title);
    List<Election> findByTenantIdInOrderByStartsAtDesc(List<UUID> tenantIds);
    List<Election> findAllByOrderByStartsAtDesc();
    List<Election> findByTenantIdOrderByStartsAtDesc(UUID tenantId);

    @Query("select e from Election e where e.status = 'SCHEDULED' and e.startsAt <= :now and e.endsAt > :now")
    List<Election> findDueToOpen(Instant now);

    @Query("select e from Election e where (e.status = 'ACTIVE' or e.status = 'SCHEDULED') and e.endsAt <= :now")
    List<Election> findDueToClose(Instant now);

    @Query("select e from Election e where e.status = 'CLOSED'")
    List<Election> findClosed();

    @Query("select e from Election e where e.status = 'ACTIVE' and e.startsAt <= :now and e.endsAt > :now order by e.endsAt asc")
    List<Election> findPublicActive(Instant now);

    long countByTenantId(UUID tenantId);
}
