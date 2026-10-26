package com.civitas.platform.modules.tenants;

import com.civitas.platform.modules.members.Member;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_memberships", uniqueConstraints = @UniqueConstraint(
        name = "uq_tenant_membership_member_tenant", columnNames = {"member_id", "tenant_id"}))
public class TenantMembership {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private Instant joinedAt = Instant.now();

    protected TenantMembership() {}

    public TenantMembership(Member member, Tenant tenant) {
        this.member = member;
        this.tenant = tenant;
    }

    public Tenant getTenant() { return tenant; }
}
