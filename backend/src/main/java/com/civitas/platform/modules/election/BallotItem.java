package com.civitas.platform.modules.election;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "ballot_items", indexes = {
    @Index(name = "idx_ballot_item_ballot", columnList = "ballot_id"),
    @Index(name = "idx_ballot_item_candidate", columnList = "candidate_id")
})
public class BallotItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ballot_id", nullable = false)
    private Ballot ballot;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private ElectionCandidate candidate;

    protected BallotItem() {}

    public BallotItem(Ballot ballot, Post post, ElectionCandidate candidate) {
        this.ballot = ballot;
        this.post = post;
        this.candidate = candidate;
    }

    public UUID getId() { return id; }
    public Ballot getBallot() { return ballot; }
    public Post getPost() { return post; }
    public ElectionCandidate getCandidate() { return candidate; }
}
