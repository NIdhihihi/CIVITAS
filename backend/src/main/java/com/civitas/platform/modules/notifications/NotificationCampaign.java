package com.civitas.platform.modules.notifications;

import com.civitas.platform.modules.elections.Election;
import com.civitas.platform.modules.members.Member;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_campaigns", indexes = @Index(name = "idx_campaign_due", columnList = "status,scheduled_at"))
public class NotificationCampaign {
    public enum Status { SCHEDULED, SENDING, SENT, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 2000)
    private String template;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SCHEDULED;

    @Column(nullable = false)
    private Instant scheduledAt = Instant.now();

    @Column(nullable = false)
    private boolean automated = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_member_id")
    private Member createdBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant sentAt;

    @Column(nullable = false)
    private int sentCount = 0;

    @Column(nullable = false)
    private int failedCount = 0;

    protected NotificationCampaign() {
    }

    public NotificationCampaign(Election election, NotificationChannel channel, String subject, String template,
                                Instant scheduledAt, boolean automated, Member createdBy) {
        this.election = election;
        this.channel = channel;
        this.subject = subject.trim();
        this.template = template.trim();
        this.scheduledAt = scheduledAt == null ? Instant.now() : scheduledAt;
        this.automated = automated;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public Election getElection() { return election; }
    public NotificationChannel getChannel() { return channel; }
    public String getSubject() { return subject; }
    public String getTemplate() { return template; }
    public Status getStatus() { return status; }
    public Instant getScheduledAt() { return scheduledAt; }
    public boolean isAutomated() { return automated; }
    public Member getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public int getSentCount() { return sentCount; }
    public int getFailedCount() { return failedCount; }

    public void start() { this.status = Status.SENDING; }
    public void finish(int sent, int failed) {
        this.status = failed > 0 && sent == 0 ? Status.FAILED : Status.SENT;
        this.sentAt = Instant.now();
        this.sentCount = sent;
        this.failedCount = failed;
    }
    public void fail() { this.status = Status.FAILED; this.sentAt = Instant.now(); }
}
