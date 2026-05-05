package com.example.Capstone.service;

import com.example.Capstone.common.enums.ScoreEvent;
import com.example.Capstone.domain.*;
import com.example.Capstone.dto.request.CreateReviewRequest;
import com.example.Capstone.dto.request.ReviewVoteRequest;
import com.example.Capstone.dto.request.UpdateReviewRequest;
import com.example.Capstone.dto.response.ReviewResponse;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private static final int DISLIKE_THRESHOLD = 10;

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final ReviewVoteRepository reviewVoteRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final ReliabilityScoreService reliabilityScoreService;

    @Transactional
    public ReviewResponse createReview(Long userId, Long restaurantId, CreateReviewRequest request) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));
        Restaurant restaurant = restaurantRepository
                .findByIdAndIsDeletedFalseAndIsHiddenFalse(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("식당을 찾을 수 없습니다."));

        Review review = Review.builder()
                .user(user)
                .restaurant(restaurant)
                .content(request.content())
                .build();
        reviewRepository.save(review);

        if (request.imageUrls() != null) {
            request.imageUrls().forEach(url ->
                    reviewImageRepository.save(ReviewImage.builder()
                            .review(review)
                            .imageUrl(url)
                            .build())
            );
        }

        reliabilityScoreService.increase(userId, ScoreEvent.REVIEW_CREATED);
        return ReviewResponse.from(review, 0, 0);
    }

    public List<ReviewResponse> getReviews(Long restaurantId) {
        return reviewRepository
                .findAllByRestaurantIdAndIsDeletedFalseAndIsHiddenFalse(restaurantId)
                .stream()
                .map(review -> ReviewResponse.from(
                        review,
                        reviewVoteRepository.countByReviewIdAndVoteType(review.getId(), ReviewVote.VoteType.LIKE),
                        reviewVoteRepository.countByReviewIdAndVoteType(review.getId(), ReviewVote.VoteType.DISLIKE)
                ))
                .toList();
    }

    @Transactional
    public void updateReview(Long userId, Long reviewId, UpdateReviewRequest request) {
        Review review = getOwnedReview(userId, reviewId);
        review.update(request.content());

        reviewImageRepository.deleteAllByReviewId(reviewId);
        if (request.imageUrls() != null) {
            request.imageUrls().forEach(url ->
                    reviewImageRepository.save(ReviewImage.builder()
                            .review(review)
                            .imageUrl(url)
                            .build())
            );
        }
    }

    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        getOwnedReview(userId, reviewId).delete();
    }

    @Transactional
    public void vote(Long userId, Long reviewId, ReviewVoteRequest request) {
        Review review = reviewRepository.findByIdAndIsDeletedFalse(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("리뷰를 찾을 수 없습니다."));

        if (review.getUser().getId().equals(userId)) {
            throw new BusinessException("자신의 리뷰는 평가할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));

        reviewVoteRepository.findByUserIdAndReviewId(userId, reviewId)
                .ifPresentOrElse(
                        vote -> vote.updateVoteType(request.voteType()),
                        () -> reviewVoteRepository.save(ReviewVote.builder()
                                .user(user)
                                .review(review)
                                .voteType(request.voteType())
                                .build())
                );

        // 리뷰 작성자 점수 증감
        if (request.voteType() == ReviewVote.VoteType.LIKE) {
            reliabilityScoreService.increase(review.getUser().getId(), ScoreEvent.REVIEW_LIKED);
        } else {
            reliabilityScoreService.decrease(review.getUser().getId(), ScoreEvent.REVIEW_DISLIKED); // ← 추가
        }

        long dislikeCount = reviewVoteRepository
                .countByReviewIdAndVoteType(reviewId, ReviewVote.VoteType.DISLIKE);
        if (dislikeCount >= DISLIKE_THRESHOLD) {
            autoReport(review);
        }
    }

    @Transactional
    public void cancelVote(Long userId, Long reviewId) {
        if (reviewVoteRepository.findByUserIdAndReviewId(userId, reviewId).isEmpty()) {
            throw new BusinessException("평가하지 않은 리뷰입니다.", HttpStatus.BAD_REQUEST);
        }
        reviewVoteRepository.deleteByUserIdAndReviewId(userId, reviewId);
    }

    private void autoReport(Review review) {
        boolean alreadyReported = reportRepository
                .existsByReporterIdAndTargetTypeAndTargetId(
                        null, Report.TargetType.REVIEW, review.getId());
        if (!alreadyReported) {
            reportRepository.save(Report.autoReport()
                    .targetType(Report.TargetType.REVIEW)
                    .targetId(review.getId())
                    .reason("싫어요 " + DISLIKE_THRESHOLD + "회 초과로 자동 신고")
                    .build());

            reliabilityScoreService.decrease(review.getUser().getId(), ScoreEvent.AUTO_REPORTED);
        }
    }

    private Review getOwnedReview(Long userId, Long reviewId) {
        Review review = reviewRepository.findByIdAndIsDeletedFalse(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("리뷰를 찾을 수 없습니다."));
        if (!review.getUser().getId().equals(userId)) {
            throw new BusinessException("리뷰 작성자가 아닙니다.", HttpStatus.FORBIDDEN);
        }
        return review;
    }
}
