package com.example.Capstone.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "review_votes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "review_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ReviewVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private VoteType voteType;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum VoteType {
        LIKE, DISLIKE
    }

    @Builder
    private ReviewVote(User user, Review review, VoteType voteType) {
        this.user     = user;
        this.review   = review;
        this.voteType = voteType;
    }

    public void updateVoteType(VoteType voteType) {
        this.voteType = voteType;
    }
}
