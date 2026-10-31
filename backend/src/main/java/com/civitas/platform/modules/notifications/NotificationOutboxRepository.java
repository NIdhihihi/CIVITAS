package com.civitas.platform.modules.notifications;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {
    List<NotificationOutbox> findByCampaignIdOrderByCreatedAtAsc(UUID campaignId);
    boolean existsByCampaignIdAndVoterId(UUID campaignId, UUID voterId);
    long countByCampaignIdAndStatus(UUID campaignId, String status);
}
