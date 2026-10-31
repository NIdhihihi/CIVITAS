package com.civitas.platform.modules.notifications;

import com.civitas.platform.modules.audit.AuditService;
import com.civitas.platform.modules.auth.EmailService;
import com.civitas.platform.modules.elections.*;
import com.civitas.platform.modules.members.Member;
import com.civitas.platform.modules.members.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationCampaignRepository campaigns;
    private final NotificationOutboxRepository outbox;
    private final ElectionVoterRepository voters;
    private final MemberRepository members;
    private final EmailService email;
    private final AuditService audit;

    public NotificationService(NotificationCampaignRepository campaigns, NotificationOutboxRepository outbox,
                               ElectionVoterRepository voters, MemberRepository members, EmailService email,
                               AuditService audit) {
        this.campaigns = campaigns;
        this.outbox = outbox;
        this.voters = voters;
        this.members = members;
        this.email = email;
        this.audit = audit;
    }

    @Transactional
    public ElectionDtos.CommunicationResponse schedule(String adminEmail, Election election, ElectionDtos.CommunicationRequest request) {
        if (request.channel() != NotificationChannel.EMAIL) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "SMS and WhatsApp delivery providers are not configured. Choose email for this demo.");
        }
        Member admin = members.findByEmailIgnoreCase(adminEmail).orElse(null);
        NotificationCampaign campaign = campaigns.save(new NotificationCampaign(election, request.channel(),
                request.subject(), request.template(), request.scheduledAt(), false, admin));
        audit.record("COMMUNICATION_SCHEDULED", election.getId().toString(), election.getTenant().getId().toString(),
                "NOTIFICATION_CAMPAIGN", campaign.getId().toString(),
                Map.of("channel", request.channel().name(), "scheduledAt", campaign.getScheduledAt().toString()));
        if (!campaign.getScheduledAt().isAfter(Instant.now())) {
            dispatch(campaign);
        }
        return view(campaign);
    }

    @Transactional
    public void sendInvitations(Election election) {
        if (!election.isNotifyVoters()) {
            return;
        }
        String subject = "Vote now: " + election.getTitle();
        if (campaigns.existsByElectionIdAndAutomatedTrueAndSubject(election.getId(), subject)) {
            return;
        }
        NotificationCampaign campaign = campaigns.save(new NotificationCampaign(election, NotificationChannel.EMAIL,
                subject,
                "Hello {{name}}, voting is now open for {{election}}. Cast your vote securely from the CIVITAS app. Polls close at {{endsAt}}.",
                Instant.now(), true, null));
        dispatch(campaign);
    }

    @Transactional
    public void sendClosureNotice(Election election) {
        String subject = "Voting closed: " + election.getTitle();
        if (campaigns.existsByElectionIdAndAutomatedTrueAndSubject(election.getId(), subject)) {
            return;
        }
        NotificationCampaign campaign = campaigns.save(new NotificationCampaign(election, NotificationChannel.EMAIL,
                subject,
                "Hello {{name}}, voting for {{election}} has closed. Results will be available to the election administrator.",
                Instant.now(), true, null));
        dispatch(campaign);
    }

    public void processDueCampaigns() {
        for (NotificationCampaign campaign : campaigns.findTop100ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                NotificationCampaign.Status.SCHEDULED, Instant.now())) {
            try {
                dispatchCampaign(campaign.getId());
            } catch (RuntimeException ex) {
                log.error("Campaign {} failed", campaign.getId(), ex);
                failCampaign(campaign.getId());
            }
        }
    }

    @Transactional
    public void dispatchCampaign(UUID campaignId) {
        campaigns.findById(campaignId).ifPresent(this::dispatch);
    }

    @Transactional
    public void failCampaign(UUID campaignId) {
        campaigns.findById(campaignId).ifPresent(campaign -> {
            campaign.fail();
            campaigns.save(campaign);
        });
    }

    @Transactional
    public void dispatch(NotificationCampaign campaign) {
        if (campaign.getStatus() != NotificationCampaign.Status.SCHEDULED) {
            return;
        }
        if (campaign.getChannel() != NotificationChannel.EMAIL) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "SMS and WhatsApp delivery providers are not configured; this campaign was not sent.");
        }
        campaign.start();
        campaigns.save(campaign);
        int sent = 0;
        int failed = 0;
        List<ElectionVoter> recipients = voters.findByElectionIdOrderByCreatedAtAsc(campaign.getElection().getId());
        for (ElectionVoter voter : recipients) {
            if (outbox.existsByCampaignIdAndVoterId(campaign.getId(), voter.getId())) {
                sent++;
                continue;
            }
            String recipient = campaign.getChannel() == NotificationChannel.EMAIL ? voter.getEmail() : voter.getPhone();
            String subject = render(campaign.getSubject(), campaign.getElection(), voter);
            String body = render(campaign.getTemplate(), campaign.getElection(), voter);
            NotificationOutbox message = new NotificationOutbox(campaign, campaign.getElection(), voter,
                    campaign.getChannel(), recipient, subject, body);
            if (recipient == null || recipient.isBlank()) {
                message.failed("No " + (campaign.getChannel() == NotificationChannel.EMAIL ? "email" : "phone") + " on the voter roll.");
                failed++;
            } else {
                try {
                    deliver(campaign.getChannel(), recipient, subject, body);
                    message.sent(localNote(campaign.getChannel()));
                    sent++;
                } catch (RuntimeException ex) {
                    message.failed(ex.getMessage() == null ? "Delivery failed" : ex.getMessage());
                    failed++;
                }
            }
            outbox.save(message);
        }
        campaign.finish(sent, failed);
        campaigns.save(campaign);
        audit.record("COMMUNICATION_SENT", campaign.getElection().getId().toString(),
                campaign.getElection().getTenant().getId().toString(), "NOTIFICATION_CAMPAIGN",
                campaign.getId().toString(), Map.of("channel", campaign.getChannel().name(), "sent", sent, "failed", failed));
    }

    private void deliver(NotificationChannel channel, String recipient, String subject, String body) {
        if (channel == NotificationChannel.EMAIL) {
            email.sendNotification(recipient, subject, body);
            return;
        }
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                "SMS and WhatsApp delivery providers are not configured.");
    }

    private String localNote(NotificationChannel channel) {
        if (channel == NotificationChannel.EMAIL) {
            return email.isSmtpConfigured() ? "Sent through SMTP" : "Captured by the local development email console";
        }
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                "SMS and WhatsApp delivery providers are not configured.");
    }

    private String render(String template, Election election, ElectionVoter voter) {
        return template
                .replace("{{name}}", voter.getFullName())
                .replace("{{election}}", election.getTitle())
                .replace("{{society}}", election.getTenant().getName())
                .replace("{{endsAt}}", election.getEndsAt().toString())
                .replace("{{startsAt}}", election.getStartsAt().toString());
    }

    @Transactional(readOnly = true)
    public List<ElectionDtos.CommunicationResponse> list(UUID electionId) {
        return campaigns.findByElectionIdOrderByCreatedAtDesc(electionId).stream().map(this::view).toList();
    }

    public ElectionDtos.CommunicationResponse view(NotificationCampaign c) {
        return new ElectionDtos.CommunicationResponse(c.getId().toString(), c.getChannel().name(), c.getSubject(),
                c.getTemplate(), c.getStatus().name(), c.getScheduledAt(), c.getSentAt(), c.getSentCount(),
                c.getFailedCount(), c.isAutomated());
    }
}
