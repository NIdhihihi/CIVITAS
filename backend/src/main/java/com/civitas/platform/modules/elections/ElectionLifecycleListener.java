package com.civitas.platform.modules.elections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ElectionLifecycleListener {
    private static final Logger log = LoggerFactory.getLogger(ElectionLifecycleListener.class);
    private final ElectionLifecycleWorker worker;

    public ElectionLifecycleListener(ElectionLifecycleWorker worker) {
        this.worker = worker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void opened(ElectionOpenedEvent event) {
        try {
            worker.sendInvitations(event.electionId());
        } catch (RuntimeException ex) {
            log.error("Could not send invitations for election {}", event.electionId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void closed(ElectionClosedEvent event) {
        try {
            worker.sendClosureNotice(event.electionId());
        } catch (RuntimeException ex) {
            log.error("Could not send closure notice for election {}", event.electionId(), ex);
        }
        try {
            worker.tally(event.electionId());
        } catch (RuntimeException ex) {
            log.error("Automatic tally failed for election {}", event.electionId(), ex);
        }
    }
}
