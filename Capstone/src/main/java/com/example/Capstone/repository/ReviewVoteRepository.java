package com.example.Capstone.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.ReviewVote;

@Repository
public interface ReviewVoteRepository extends JpaRepository<ReviewVote, Long> {
    Optional<ReviewVote> findByUserIdAndReviewId(Long userId, Long reviewId);
    long countByReviewIdAndVoteType(Long reviewId, ReviewVote.VoteType voteType);
    void deleteByUserIdAndReviewId(Long userId, Long reviewId);
}
