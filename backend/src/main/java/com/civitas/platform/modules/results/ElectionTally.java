package com.civitas.platform.modules.results;

import com.civitas.platform.modules.elections.Election;
import com.civitas.platform.modules.elections.ElectionPost;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "election_tallies", indexes = {
        @Index(name = "idx_tally_election", columnList = "election_id"),
        @Index(name = "idx_tally_post", columnList = "post_id")
})
public class ElectionTally {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private ElectionPost post;

    @Column(nullable = false, length = 20)
    private String contestType;

    @Column(nullable = false)
    private UUID contestId;

    @Column(nullable = false, length = 140)
    private String contestName;

    @Column(length = 600)
    private String photoUrl;

    @Column(nullable = false)
    private long votes;

    @Column(nullable = false)
    private Instant countedAt = Instant.now();

    protected ElectionTally() {
    }

    public ElectionTally(Election election, ElectionPost post, String contestType, UUID contestId,
                         String contestName, String photoUrl, long votes) {
        this.election = election;
        this.post = post;
        this.contestType = contestType;
        this.contestId = contestId;
        this.contestName = contestName;
        this.photoUrl = photoUrl;
        this.votes = votes;
    }

    public UUID getId() { return id; }
    public Election getElection() { return election; }
    public ElectionPost getPost() { return post; }
    public String getContestType() { return contestType; }
    public UUID getContestId() { return contestId; }
    public String getContestName() { return contestName; }
    public String getPhotoUrl() { return photoUrl; }
    public long getVotes() { return votes; }
    public Instant getCountedAt() { return countedAt; }
    public void addVote() { this.votes++; }
}
