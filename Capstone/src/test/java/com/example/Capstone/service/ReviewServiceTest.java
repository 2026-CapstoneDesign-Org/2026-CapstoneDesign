package com.example.Capstone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.Review;
import com.example.Capstone.domain.ReviewVote;
import com.example.Capstone.domain.User;
import com.example.Capstone.dto.response.ReviewResponse;
import com.example.Capstone.dto.response.UserReviewResponse;
import com.example.Capstone.repository.ReportRepository;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.ReviewImageRepository;
import com.example.Capstone.repository.ReviewRepository;
import com.example.Capstone.repository.ReviewVoteRepository;
import com.example.Capstone.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewImageRepository reviewImageRepository;

    @Mock
    private ReviewVoteRepository reviewVoteRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private ReliabilityScoreService reliabilityScoreService;

    @Mock
    private ReviewSummaryService reviewSummaryService;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    @DisplayName("restaurant review list includes viewer vote type for each review")
    void getReviewsIncludesViewerVoteType() {
        Long viewerUserId = 99L;
        Restaurant restaurant = restaurant(10L);
        Review likedReview = review(101L, user(1L), restaurant);
        Review dislikedReview = review(102L, user(2L), restaurant);
        Review notVotedReview = review(103L, user(3L), restaurant);

        given(reviewRepository.findAllByRestaurantIdAndIsDeletedFalseAndIsHiddenFalse(10L))
                .willReturn(List.of(likedReview, dislikedReview, notVotedReview));
        given(reviewVoteRepository.findAllByUserIdAndReviewIdIn(
                eq(viewerUserId),
                eq(List.of(101L, 102L, 103L))
        )).willReturn(List.of(
                vote(user(viewerUserId), likedReview, ReviewVote.VoteType.LIKE),
                vote(user(viewerUserId), dislikedReview, ReviewVote.VoteType.DISLIKE)
        ));
        given(reviewVoteRepository.countByReviewIdAndVoteType(anyLong(), any(ReviewVote.VoteType.class)))
                .willReturn(0L);

        List<ReviewResponse> responses = reviewService.getReviews(viewerUserId, 10L);

        assertThat(responses)
                .extracting(ReviewResponse::myVoteType)
                .containsExactly(ReviewVote.VoteType.LIKE, ReviewVote.VoteType.DISLIKE, null);
    }

    @Test
    @DisplayName("review list does not query viewer votes when viewer user id is absent")
    void getReviewsWithoutViewerUserIdReturnsNullVoteType() {
        Restaurant restaurant = restaurant(10L);
        Review review = review(101L, user(1L), restaurant);

        given(reviewRepository.findAllByRestaurantIdAndIsDeletedFalseAndIsHiddenFalse(10L))
                .willReturn(List.of(review));
        given(reviewVoteRepository.countByReviewIdAndVoteType(anyLong(), any(ReviewVote.VoteType.class)))
                .willReturn(0L);

        List<ReviewResponse> responses = reviewService.getReviews(10L);

        assertThat(responses).extracting(ReviewResponse::myVoteType).containsExactly((ReviewVote.VoteType) null);
        then(reviewVoteRepository).should(never()).findAllByUserIdAndReviewIdIn(anyLong(), any());
    }

    @Test
    @DisplayName("user review list uses path user id as target and principal user id as viewer")
    void getUserReviewsIncludesViewerVoteType() {
        Long targetUserId = 2L;
        Long viewerUserId = 99L;
        Restaurant restaurant = restaurant(10L);
        Review review = review(201L, user(targetUserId), restaurant);

        given(reviewRepository.findAllByUserIdAndIsDeletedFalseAndIsHiddenFalse(targetUserId))
                .willReturn(List.of(review));
        given(reviewVoteRepository.findAllByUserIdAndReviewIdIn(viewerUserId, List.of(201L)))
                .willReturn(List.of(vote(user(viewerUserId), review, ReviewVote.VoteType.LIKE)));
        given(reviewVoteRepository.countByReviewIdAndVoteType(anyLong(), any(ReviewVote.VoteType.class)))
                .willReturn(0L);

        List<UserReviewResponse> responses = reviewService.getUserReviews(targetUserId, viewerUserId);

        assertThat(responses).extracting(UserReviewResponse::myVoteType)
                .containsExactly(ReviewVote.VoteType.LIKE);
        then(reviewRepository).should().findAllByUserIdAndIsDeletedFalseAndIsHiddenFalse(targetUserId);
    }

    private User user(Long id) {
        User user = User.builder()
                .provider("KAKAO")
                .providerUserId("provider-" + id)
                .nickname("user-" + id)
                .profileImageUrl("http://image/" + id)
                .role(User.Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Restaurant restaurant(Long id) {
        Restaurant restaurant = Restaurant.builder()
                .name("Restaurant " + id)
                .address("Address")
                .categoryName("Korean")
                .regionName("Seoul")
                .lat(new BigDecimal("37.0"))
                .lng(new BigDecimal("127.0"))
                .build();
        ReflectionTestUtils.setField(restaurant, "id", id);
        return restaurant;
    }

    private Review review(Long id, User user, Restaurant restaurant) {
        Review review = Review.builder()
                .user(user)
                .restaurant(restaurant)
                .content("review-" + id)
                .build();
        ReflectionTestUtils.setField(review, "id", id);
        return review;
    }

    private ReviewVote vote(User user, Review review, ReviewVote.VoteType voteType) {
        return ReviewVote.builder()
                .user(user)
                .review(review)
                .voteType(voteType)
                .build();
    }
}
