package com.civitas.platform.modules.election;

import com.civitas.platform.modules.onboarding.CandidateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ElectionCandidateRepository extends JpaRepository<ElectionCandidate, UUID> {
    List<ElectionCandidate> findByElectionIdOrderByRegisteredAtAsc(UUID electionId);
    List<ElectionCandidate> findByPostIdOrderByRegisteredAtAsc(UUID postId);
    List<ElectionCandidate> findByElectionIdAndStatus(UUID electionId, CandidateStatus status);
    List<ElectionCandidate> findByPostIdAndStatus(UUID postId, CandidateStatus status);
    boolean existsByElectionIdAndPostIdAndEmailIgnoreCase(UUID electionId, UUID postId, String email);
    long countByElectionIdAndStatus(UUID electionId, CandidateStatus status);
}
