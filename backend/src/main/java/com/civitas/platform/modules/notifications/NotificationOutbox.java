package com.civitas.platform.modules.notifications;

import com.civitas.platform.modules.elections.Election;
import com.civitas.platform.modules.elections.ElectionVoter;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_outbox", indexes = @Index(name = "idx_outbox_campaign", columnList = "campaign_id"))
public class NotificationOutbox {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private NotificationCampaign campaign;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_id")
    private ElectionVoter voter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(length = 254)
    private String recipient;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 400)
    private String deliveryNote;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant sentAt;

    protected NotificationOutbox() {
    }

    public NotificationOutbox(NotificationCampaign campaign, Election election, ElectionVoter voter,
                              NotificationChannel channel, String recipient, String subject, String body) {
        this.campaign = campaign;
        this.election = election;
        this.voter = voter;
        this.channel = channel;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
    }

    public UUID getId() { return id; }
    public NotificationCampaign getCampaign() { return campaign; }
    public Election getElection() { return election; }
    public ElectionVoter getVoter() { return voter; }
    public NotificationChannel getChannel() { return channel; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getStatus() { return status; }
    public String getDeliveryNote() { return deliveryNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }

    public void sent(String note) { this.status = "SENT"; this.deliveryNote = note; this.sentAt = Instant.now(); }
    public void failed(String error) { this.status = "FAILED"; this.deliveryNote = error; }
}
