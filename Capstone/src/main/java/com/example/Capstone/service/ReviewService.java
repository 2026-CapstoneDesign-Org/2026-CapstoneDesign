package com.example.Capstone.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.domain.Report;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.Review;
import com.example.Capstone.domain.ReviewImage;
import com.example.Capstone.domain.ReviewVote;
import com.example.Capstone.domain.User;
import com.example.Capstone.dto.request.CreateReviewRequest;
import com.example.Capstone.dto.request.ReviewVoteRequest;
import com.example.Capstone.dto.request.UpdateReviewRequest;
import com.example.Capstone.dto.response.ReviewResponse;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.ReportRepository;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.ReviewImageRepository;
import com.example.Capstone.repository.ReviewRepository;
import com.example.Capstone.repository.ReviewVoteRepository;
import com.example.Capstone.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private static final int DISLIKE_THRESHOLD = 10;  // 자동 신고 기준

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final ReviewVoteRepository reviewVoteRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;

    // 리뷰 작성
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

        // 이미지 저장
        if (request.imageUrls() != null) {
            request.imageUrls().forEach(url ->
                    reviewImageRepository.save(ReviewImage.builder()
                            .review(review)
                            .imageUrl(url)
                            .build())
            );
        }

        return ReviewResponse.from(review, 0, 0);
    }

    // 리뷰 목록
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

    // 리뷰 수정
    @Transactional
    public void updateReview(Long userId, Long reviewId, UpdateReviewRequest request) {
        Review review = getOwnedReview(userId, reviewId);
        review.update(request.content());

        // 이미지 교체
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

    // 리뷰 삭제
    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        Review review = getOwnedReview(userId, reviewId);
        review.delete();
    }

    // 리뷰 평가 (좋아요/싫어요)
    @Transactional
    public void vote(Long userId, Long reviewId, ReviewVoteRequest request) {
        Review review = reviewRepository.findByIdAndIsDeletedFalse(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("리뷰를 찾을 수 없습니다."));

        // 자기 리뷰 평가 방지
        if (review.getUser().getId().equals(userId)) {
            throw new BusinessException("자신의 리뷰는 평가할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));

        // 이미 평가했으면 변경
        reviewVoteRepository.findByUserIdAndReviewId(userId, reviewId)
                .ifPresentOrElse(
                        vote -> vote.updateVoteType(request.voteType()),
                        () -> reviewVoteRepository.save(ReviewVote.builder()
                                .user(user)
                                .review(review)
                                .voteType(request.voteType())
                                .build())
                );

        // 싫어요 기준 초과 시 자동 신고
        long dislikeCount = reviewVoteRepository
                .countByReviewIdAndVoteType(reviewId, ReviewVote.VoteType.DISLIKE);

        if (dislikeCount >= DISLIKE_THRESHOLD) {
            autoReport(review);
        }
    }

    // 평가 취소
    @Transactional
    public void cancelVote(Long userId, Long reviewId) {
        if (!reviewVoteRepository.findByUserIdAndReviewId(userId, reviewId).isPresent()) {
            throw new BusinessException("평가하지 않은 리뷰입니다.", HttpStatus.BAD_REQUEST);
        }
        reviewVoteRepository.deleteByUserIdAndReviewId(userId, reviewId);
    }

    // 싫어요 기준 초과 시 자동 신고
    private void autoReport(Review review) {
        // 이미 자동 신고된 리뷰는 중복 신고 안 함
        boolean alreadyReported = reportRepository
                .existsByReporterIdAndTargetTypeAndTargetId(
                        null, Report.TargetType.REVIEW, review.getId());
        if (!alreadyReported) {
            reportRepository.save(Report.autoReport()
                    .targetType(Report.TargetType.REVIEW)
                    .targetId(review.getId())
                    .reason("싫어요 " + DISLIKE_THRESHOLD + "회 초과로 자동 신고")
                    .build());
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
