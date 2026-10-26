package com.civitas.platform.modules.tenants;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {
    boolean existsByMemberEmailIgnoreCaseAndTenantId(String email, UUID tenantId);
    List<TenantMembership> findByMemberEmailIgnoreCaseOrderByJoinedAtDesc(String email);
}
