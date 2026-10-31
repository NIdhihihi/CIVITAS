package com.civitas.platform.modules.onboarding;
import com.civitas.platform.modules.members.*; import com.civitas.platform.modules.tenants.*; import org.springframework.dao.DataIntegrityViolationException; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import org.springframework.web.server.ResponseStatusException; import java.util.*;
@Service public class CandidateService {
 private final CandidateRepository candidates; private final MemberRepository members; private final TenantService tenants;
 public CandidateService(CandidateRepository candidates,MemberRepository members,TenantService tenants){this.candidates=candidates;this.members=members;this.tenants=tenants;}
 @Transactional public CandidateDtos.CandidateResponse register(String email,CandidateDtos.RegisterCandidateRequest r){
  Member m=members.findByEmailIgnoreCase(email).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  Tenant t; try{t=tenants.find(UUID.fromString(r.tenantId()));}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"tenantId must be a valid tenant UUID.");}
  tenants.join(email,t.getId().toString());
  try{Candidate saved=candidates.saveAndFlush(new Candidate(m,t,r.contestingPost(),r.statement(),r.phone()));if(m.getRole()!=Role.PLATFORM_ADMIN&&m.getRole()!=Role.TENANT_ADMIN)m.setRole(Role.CANDIDATE);members.save(m);return toResponse(saved);}catch(DataIntegrityViolationException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"You have already registered for this post in this society.");}
 }
 @Transactional(readOnly=true) public List<CandidateDtos.CandidateResponse> mine(String email){return candidates.findByMemberEmailIgnoreCaseOrderByRegisteredAtDesc(email).stream().map(this::toResponse).toList();}
 @Transactional(readOnly=true) public List<CandidateDtos.CandidateResponse> all(String email){
  Member admin=members.findByEmailIgnoreCase(email).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  if(admin.getRole()==Role.PLATFORM_ADMIN)return candidates.findAllByOrderByRegisteredAtDesc().stream().map(this::toResponse).toList();
  if(admin.getRole()==Role.TENANT_ADMIN){var tenantIds=tenants.adminTenantIds(email);return candidates.findByTenantIdInOrderByRegisteredAtDesc(tenantIds).stream().map(this::toResponse).toList();}
  throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Candidate application review requires an administrator role.");
 }
 @Transactional public CandidateDtos.CandidateResponse updateStatus(String email,UUID id,CandidateStatus status){
  if(status==CandidateStatus.PENDING)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"An application can only be approved or rejected.");
  Member admin=members.findByEmailIgnoreCase(email).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  Candidate candidate=candidates.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Candidate application not found."));
  if(admin.getRole()!=Role.PLATFORM_ADMIN && (admin.getRole()!=Role.TENANT_ADMIN||!tenants.isMemberOrCreator(email,candidate.getTenant().getId())))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You do not administer this society's applications.");
  if(candidate.getStatus()!=CandidateStatus.PENDING){
   if(candidate.getStatus()==status)return toResponse(candidate);
   throw new ResponseStatusException(HttpStatus.CONFLICT,"This application has already been reviewed and cannot be changed.");
  }
  candidate.setStatus(status);
  return toResponse(candidates.save(candidate));
 }
 private CandidateDtos.CandidateResponse toResponse(Candidate c){return new CandidateDtos.CandidateResponse(c.getId().toString(),c.getTenant().getId().toString(),c.getTenant().getName(),c.getMember().getEmail(),c.getMember().getFullName(),c.getContestingPost(),c.getStatement(),c.getPhone(),c.getStatus().name(),c.getRegisteredAt().toString());}
}
