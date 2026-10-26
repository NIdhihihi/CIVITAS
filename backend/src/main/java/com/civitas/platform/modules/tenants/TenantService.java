package com.civitas.platform.modules.tenants;
import com.civitas.platform.modules.members.*; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import org.springframework.web.server.ResponseStatusException; import java.util.*;
@Service public class TenantService {
 private final TenantRepository tenants; private final MemberRepository members; private final TenantMembershipRepository memberships;
 public TenantService(TenantRepository tenants,MemberRepository members,TenantMembershipRepository memberships){this.tenants=tenants;this.members=members;this.memberships=memberships;}
 @Transactional public TenantDtos.TenantResponse create(String email,TenantDtos.CreateTenantRequest r){
  String code=r.code().trim().toUpperCase(); if(tenants.existsByCodeIgnoreCase(code))throw new ResponseStatusException(HttpStatus.CONFLICT,"Tenant code is already in use.");
  Member member=members.findByEmailIgnoreCase(email).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  Tenant tenant=tenants.save(new Tenant(r.name(),code,r.city(),member)); return toResponse(tenant);
 }
 @Transactional(readOnly=true) public List<TenantDtos.TenantResponse> mine(String email){var joined=new LinkedHashMap<UUID,Tenant>();tenants.findByCreatedByEmailIgnoreCase(email).forEach(t->joined.put(t.getId(),t));memberships.findByMemberEmailIgnoreCaseOrderByJoinedAtDesc(email).forEach(m->joined.putIfAbsent(m.getTenant().getId(),m.getTenant()));return joined.values().stream().map(this::toResponse).toList();}
 @Transactional(readOnly=true) public List<TenantDtos.TenantResponse> all(){return tenants.findAll().stream().map(this::toResponse).toList();}
 @Transactional public TenantDtos.TenantResponse join(String email,String tenantId){UUID id;try{id=UUID.fromString(tenantId);}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"tenantId must be a valid UUID.");}Member member=members.findByEmailIgnoreCase(email).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));Tenant tenant=find(id);if(!memberships.existsByMemberEmailIgnoreCaseAndTenantId(email,id))memberships.save(new TenantMembership(member,tenant));return toResponse(tenant);}
 public Tenant find(UUID id){return tenants.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found."));}
 public TenantDtos.TenantResponse toResponse(Tenant t){return new TenantDtos.TenantResponse(t.getId().toString(),t.getName(),t.getCode(),t.getCity(),t.getCreatedBy().getEmail());}
}
