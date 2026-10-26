package com.civitas.platform.modules.election;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {
    List<Post> findByElectionIdOrderByDisplayOrderAsc(UUID electionId);
    boolean existsByElectionIdAndTitleIgnoreCase(UUID electionId, String title);
    long countByElectionId(UUID electionId);
}
