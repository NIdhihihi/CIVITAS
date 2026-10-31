package com.civitas.platform.modules.voting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoterParticipationRepository extends JpaRepository<VoterParticipation, UUID> {
    boolean existsByElectionIdAndVoterId(UUID electionId, UUID voterId);
    Optional<VoterParticipation> findByReceiptCodeHash(String receiptCodeHash);
    Optional<VoterParticipation> findByReceiptId(UUID receiptId);
    Optional<VoterParticipation> findByReceiptHash(String receiptHash);
    List<VoterParticipation> findByVoterIdOrderByCastAtDesc(UUID voterId);
    long countByElectionId(UUID electionId);
    void deleteByElectionId(UUID electionId);
}
