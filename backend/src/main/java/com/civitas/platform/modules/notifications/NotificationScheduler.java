package com.civitas.platform.modules.notifications;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {
    private final NotificationService notifications;

    public NotificationScheduler(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Scheduled(fixedDelay = 15000, initialDelay = 10000)
    public void dispatchDueCommunications() {
        notifications.processDueCampaigns();
    }
}
