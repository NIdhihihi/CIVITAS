package com.civitas.platform.modules.notifications;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationCampaignRepository extends JpaRepository<NotificationCampaign, UUID> {
    List<NotificationCampaign> findByElectionIdOrderByCreatedAtDesc(UUID electionId);
    List<NotificationCampaign> findByStatusAndScheduledAtLessThanEqual(NotificationCampaign.Status status, Instant now);
    boolean existsByElectionIdAndAutomatedTrueAndSubject(UUID electionId, String subject);
    List<NotificationCampaign> findTop100ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(NotificationCampaign.Status status, Instant now);
}
