package com.civitas.platform.modules.elections;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ElectionLifecycleScheduler {
    private final ElectionService elections;

    public ElectionLifecycleScheduler(ElectionService elections) {
        this.elections = elections;
    }

    @Scheduled(fixedDelay = 15000, initialDelay = 12000)
    public void run() {
        elections.openDueElections();
        elections.closeDueElections();
    }
}
