package com.example.Capstone.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.example.Capstone.domain.Review;
import com.example.Capstone.domain.ReviewImage;

public record UserReviewResponse(
        Long id,
        Long userId,
        String nickname,
        String content,
        List<String> imageUrls,
        long likeCount,
        long dislikeCount,
        LocalDateTime createdAt,
        RestaurantSimpleResponse restaurant
) {
    public static UserReviewResponse from(Review review, long likeCount, long dislikeCount) {
        return new UserReviewResponse(
                review.getId(),
                review.getUser().getId(),
                review.getUser().getNickname(),
                review.getContent(),
                review.getImages().stream()
                        .map(ReviewImage::getImageUrl)
                        .toList(),
                likeCount,
                dislikeCount,
                review.getCreatedAt(),
                RestaurantSimpleResponse.from(review.getRestaurant())
        );
    }
}
