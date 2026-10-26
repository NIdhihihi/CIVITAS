package com.civitas.platform.modules.election;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BallotRepository extends JpaRepository<Ballot, UUID> {
    List<Ballot> findByElectionId(UUID electionId);
    Optional<Ballot> findByReceiptCode(String receiptCode);
    Optional<Ballot> findByBallotHash(String ballotHash);
    long countByElectionId(UUID electionId);
}
