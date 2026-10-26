package com.civitas.platform.modules.election;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VoterRollRepository extends JpaRepository<VoterRollMember, UUID> {
    List<VoterRollMember> findByElectionIdOrderByFlatNumberAsc(UUID electionId);
    Optional<VoterRollMember> findByElectionIdAndEmailIgnoreCase(UUID electionId, String email);
    Optional<VoterRollMember> findByElectionIdAndFlatNumberIgnoreCase(UUID electionId, String flatNumber);
    boolean existsByElectionIdAndFlatNumberIgnoreCase(UUID electionId, String flatNumber);
    boolean existsByElectionIdAndEmailIgnoreCase(UUID electionId, String email);
    long countByElectionId(UUID electionId);
    long countByElectionIdAndEligibleTrue(UUID electionId);
    long countByElectionIdAndHasVotedTrue(UUID electionId);
}
