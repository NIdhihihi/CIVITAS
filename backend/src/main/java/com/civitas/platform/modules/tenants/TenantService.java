package com.civitas.platform.modules.tenants;
import com.civitas.platform.modules.members.*; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import org.springframework.web.server.ResponseStatusException; import java.util.*;
@Service public class TenantService {
 private final TenantRepository tenants; private final MemberRepository members; private final TenantMembershipRepository memberships;
 public TenantService(TenantRepository tenants,MemberRepository members,TenantMembershipRepository memberships){this.tenants=tenants;this.members=members;this.memberships=memberships;}
 @Transactional public TenantDtos.TenantResponse create(String email,TenantDtos.CreateTenantRequest r){
  String code=r.code().trim().toUpperCase(); if(tenants.existsByCodeIgnoreCase(code))throw new ResponseStatusException(HttpStatus.CONFLICT,"Tenant code is already in use.");
  Member member=members.findByEmailIgnoreCase(email).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  if(member.getRole()!=Role.PLATFORM_ADMIN)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only a platform administrator can create a society.");
  Tenant tenant=tenants.save(new Tenant(r.name(),code,r.city(),member));
   if(!memberships.existsByMemberEmailIgnoreCaseAndTenantId(email,tenant.getId()))memberships.save(new TenantMembership(member,tenant));
   return toResponse(tenant);
 }
 @Transactional(readOnly=true) public List<TenantDtos.TenantResponse> mine(String email){var joined=new LinkedHashMap<UUID,Tenant>();tenants.findByCreatedByEmailIgnoreCase(email).forEach(t->joined.put(t.getId(),t));memberships.findByMemberEmailIgnoreCaseOrderByJoinedAtDesc(email).forEach(m->joined.putIfAbsent(m.getTenant().getId(),m.getTenant()));return joined.values().stream().map(this::toResponse).toList();}
 @Transactional(readOnly=true) public List<TenantDtos.TenantResponse> all(String email){
  Member member=members.findByEmailIgnoreCase(email).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  if(member.getRole()==Role.PLATFORM_ADMIN)return tenants.findAll().stream().map(this::toResponse).toList();
  if(member.getRole()!=Role.TENANT_ADMIN&&member.getRole()!=Role.AUDITOR&&member.getRole()!=Role.CANDIDATE&&member.getRole()!=Role.USER)
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Society directory access is not available for this account.");
  return tenants.findAll().stream().map(t->new TenantDtos.TenantResponse(t.getId().toString(),t.getName(),t.getCode(),t.getCity(),null)).toList();
 }
 @Transactional public TenantDtos.TenantResponse join(String email,String tenantId){UUID id;try{id=UUID.fromString(tenantId);}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"tenantId must be a valid UUID.");}Member member=members.findByEmailIgnoreCase(email).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));Tenant tenant=find(id);if(!memberships.existsByMemberEmailIgnoreCaseAndTenantId(email,id))memberships.save(new TenantMembership(member,tenant));return toResponse(tenant);}
 public Tenant find(UUID id){return tenants.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found."));}
 @Transactional(readOnly=true) public boolean isMemberOrCreator(String email,UUID tenantId){return tenants.findById(tenantId).map(t->t.getCreatedBy().getEmail().equalsIgnoreCase(email)).orElse(false)||memberships.existsByMemberEmailIgnoreCaseAndTenantId(email,tenantId);}
 @Transactional(readOnly=true) public List<UUID> adminTenantIds(String email){var ids=new LinkedHashMap<UUID,Tenant>();tenants.findByCreatedByEmailIgnoreCase(email).forEach(t->ids.put(t.getId(),t));memberships.findByMemberEmailIgnoreCaseOrderByJoinedAtDesc(email).forEach(m->ids.putIfAbsent(m.getTenant().getId(),m.getTenant()));return new ArrayList<>(ids.keySet());}
 public TenantDtos.TenantResponse toResponse(Tenant t){return new TenantDtos.TenantResponse(t.getId().toString(),t.getName(),t.getCode(),t.getCity(),t.getCreatedBy().getEmail());}
}
