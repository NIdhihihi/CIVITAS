package com.civitas.platform.modules.elections;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ElectionVoterRepository extends JpaRepository<ElectionVoter, UUID> {
    List<ElectionVoter> findByElectionIdOrderByCreatedAtAsc(UUID electionId);
    List<ElectionVoter> findByElectionIdAndEmailIgnoreCase(UUID electionId, String email);
    List<ElectionVoter> findByElectionIdAndAadhaarHash(UUID electionId, String aadhaarHash);
    List<ElectionVoter> findByElectionIdAndAadhaarLast4(UUID electionId, String aadhaarLast4);
    Optional<ElectionVoter> findByIdAndElectionId(UUID id, UUID electionId);
    boolean existsByElectionIdAndEmailIgnoreCase(UUID electionId, String email);
    boolean existsByElectionIdAndAadhaarHash(UUID electionId, String aadhaarHash);
    long countByElectionId(UUID electionId);
    long countByElectionIdAndVotedTrue(UUID electionId);

    @org.springframework.data.jpa.repository.Query("select distinct v.election.id from ElectionVoter v where lower(v.email) = lower(:email)")
    List<UUID> findElectionIdsByEmail(String email);
    List<ElectionVoter> findByEmailIgnoreCaseOrderByCreatedAtDesc(String email);
}
