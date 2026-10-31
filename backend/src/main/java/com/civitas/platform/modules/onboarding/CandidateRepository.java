package com.civitas.platform.modules.onboarding;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface CandidateRepository extends JpaRepository<Candidate,UUID>{List<Candidate> findByMemberEmailIgnoreCaseOrderByRegisteredAtDesc(String email);List<Candidate> findAllByOrderByRegisteredAtDesc();List<Candidate> findByTenantIdInOrderByRegisteredAtDesc(List<UUID> tenantIds);java.util.Optional<Candidate> findFirstByMemberIdAndTenantIdAndContestingPostIgnoreCase(java.util.UUID memberId,java.util.UUID tenantId,String contestingPost);}
