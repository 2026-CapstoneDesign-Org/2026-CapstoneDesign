package com.example.Capstone.service;

import com.example.Capstone.common.enums.ScoreEvent;
import com.example.Capstone.domain.*;
import com.example.Capstone.domain.Notification.NotificationType;
import com.example.Capstone.dto.request.CreateReviewRequest;
import com.example.Capstone.dto.request.ReviewVoteRequest;
import com.example.Capstone.dto.request.UpdateReviewRequest;
import com.example.Capstone.dto.response.PageResponse;
import com.example.Capstone.dto.response.ReviewResponse;
import com.example.Capstone.dto.response.UserReviewResponse;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final UserFollowRepository userFollowRepository;
    private final ReliabilityScoreService reliabilityScoreService;
    private final ReviewSummaryService reviewSummaryService;
    private final NotificationService notificationService;


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
        reviewSummaryService.invalidateCache(restaurantId); 

        notificationService.sendToFollowers(
            userId,
            NotificationType.FOLLOWING_NEW_REVIEW,
            user.getNickname() + "님이 새로운 리뷰를 작성했습니다.",
            review.getId(),
            "REVIEW"
        );
        
        return ReviewResponse.from(review, 0, 0);
    } 

    public PageResponse<ReviewResponse> getReviews(Long restaurantId, Long userId, Pageable pageable) {
        Page<ReviewResponse> page = reviewRepository
                .findAllByRestaurantIdWithDetailsOptimized(restaurantId, pageable)
                .map(review -> {
                    ReviewVote.VoteType myVote = null;
                    if (userId != null) {
                        myVote = reviewVoteRepository
                                .findByUserIdAndReviewId(userId, review.getId())
                                .map(ReviewVote::getVoteType)
                                .orElse(null);
                    }
                    return ReviewResponse.from(
                            review,
                            reviewVoteRepository.countByReviewIdAndVoteType(review.getId(), ReviewVote.VoteType.LIKE),
                            reviewVoteRepository.countByReviewIdAndVoteType(review.getId(), ReviewVote.VoteType.DISLIKE),
                            myVote
                    );
                });
        return PageResponse.from(page);
    }

    public PageResponse<UserReviewResponse> getUserReviews(Long userId, Long currentUserId, Pageable pageable) {
        Page<UserReviewResponse> page = reviewRepository
                .findAllByUserIdWithDetails(userId, pageable)
                .map(review -> {
                    ReviewVote.VoteType myVote = null;
                    if (currentUserId != null) {
                        myVote = reviewVoteRepository
                                .findByUserIdAndReviewId(currentUserId, review.getId())
                                .map(ReviewVote::getVoteType)
                                .orElse(null);
                    }
                    return UserReviewResponse.from(
                            review,
                            reviewVoteRepository.countByReviewIdAndVoteType(review.getId(), ReviewVote.VoteType.LIKE),
                            reviewVoteRepository.countByReviewIdAndVoteType(review.getId(), ReviewVote.VoteType.DISLIKE),
                            myVote
                    );
                });
        return PageResponse.from(page);
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

        reviewSummaryService.invalidateCache(review.getRestaurant().getId());
    }

    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        Review review = getOwnedReview(userId, reviewId);
        reviewSummaryService.invalidateCache(review.getRestaurant().getId());
        review.delete();
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

            notificationService.send(
                review.getUser().getId(),
                Notification.NotificationType.REVIEW_LIKE,
                user.getNickname() + "님이 리뷰를 좋아합니다.",
                review.getId(),
                "REVIEW"
            );
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

    private Map<Long, ReviewVote.VoteType> getMyVoteTypes(Long viewerUserId, List<Review> reviews) {
        if (viewerUserId == null || reviews.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> reviewIds = reviews.stream()
                .map(Review::getId)
                .toList();

        return reviewVoteRepository.findAllByUserIdAndReviewIdIn(viewerUserId, reviewIds)
                .stream()
                .collect(Collectors.toMap(
                        vote -> vote.getReview().getId(),
                        ReviewVote::getVoteType
                ));
    }
}
