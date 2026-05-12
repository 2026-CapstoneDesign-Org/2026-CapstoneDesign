package com.example.Capstone.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.example.Capstone.domain.Review;
import com.example.Capstone.domain.ReviewImage;
import com.example.Capstone.domain.ReviewVote;

public record ReviewResponse(
        Long id,
        Long userId,
        String nickname,
        String content,
        List<String> imageUrls,
        long likeCount,
        long dislikeCount,
        ReviewVote.VoteType myVoteType,
        LocalDateTime createdAt
) {
    public static ReviewResponse from(Review review, long likeCount, long dislikeCount) {
        return from(review, likeCount, dislikeCount, null);
    }

    public static ReviewResponse from(
            Review review,
            long likeCount,
            long dislikeCount,
            ReviewVote.VoteType myVoteType
    ) {
        return new ReviewResponse(
                review.getId(),
                review.getUser().getId(),
                review.getUser().getNickname(),
                review.getContent(),
                review.getImages().stream()
                        .map(ReviewImage::getImageUrl)
                        .toList(),
                likeCount,
                dislikeCount,
                myVoteType,
                review.getCreatedAt()
        );
    }
}
