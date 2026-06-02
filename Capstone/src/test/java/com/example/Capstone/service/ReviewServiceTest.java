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
import com.example.Capstone.repository.UserFollowRepository;
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
    private UserFollowRepository userFollowRepository;

    @Mock
    private ReliabilityScoreService reliabilityScoreService;

    @Mock
    private ReviewSummaryService reviewSummaryService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ReviewService reviewService;

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
