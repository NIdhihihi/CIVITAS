package com.civitas.platform.modules.elections;

import com.civitas.platform.modules.notifications.NotificationService;
import com.civitas.platform.modules.results.TallyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Runs post-commit election work in a fresh transaction.
 *
 * <p>An {@code AFTER_COMMIT} listener still executes on the original transaction's thread while
 * its resources are bound. Any transactional work started there would join the already-committed
 * transaction and fail with "no transaction is in progress". Each responsibility is therefore
 * given its own {@code REQUIRES_NEW} transaction, and failures stay isolated from one another.</p>
 */
@Service
public class ElectionLifecycleWorker {
    private final ElectionRepository elections;
    private final NotificationService notifications;
    private final TallyService tally;

    public ElectionLifecycleWorker(ElectionRepository elections, NotificationService notifications,
                                   TallyService tally) {
        this.elections = elections;
        this.notifications = notifications;
        this.tally = tally;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendInvitations(UUID electionId) {
        elections.findById(electionId).ifPresent(notifications::sendInvitations);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendClosureNotice(UUID electionId) {
        elections.findById(electionId).ifPresent(notifications::sendClosureNotice);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tally(UUID electionId) {
        tally.tally(electionId);
    }
}
