package com.example.Capstone.service;

import com.example.Capstone.common.enums.ScoreEvent;
import com.example.Capstone.domain.User;
import com.example.Capstone.domain.UserFollow;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.UserFollowRepository;
import com.example.Capstone.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock UserFollowRepository userFollowRepository;
    @Mock UserRepository userRepository;
    @Mock ReliabilityScoreService reliabilityScoreService;

    @InjectMocks FollowService followService;

    private User follower;
    private User following;

    @BeforeEach
    void setUp() {
        follower = User.builder()
                .provider("KAKAO")
                .providerUserId("test_1")
                .nickname("팔로워유저")
                .profileImageUrl("http://default.img")
                .role(User.Role.USER)
                .build();

        following = User.builder()
                .provider("KAKAO")
                .providerUserId("test_2")
                .nickname("팔로잉유저")
                .profileImageUrl("http://default.img")
                .role(User.Role.USER)
                .build();
    }

    @Test
    @DisplayName("팔로우 성공 - 팔로잉 유저 점수 증가")
    void follow_success() {
        given(userFollowRepository.existsByFollowerIdAndFollowingId(anyLong(), anyLong())).willReturn(false);
        given(userRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(follower));
        given(userRepository.findByIdAndIsDeletedFalse(2L)).willReturn(Optional.of(following));
        given(userFollowRepository.save(any())).willReturn(
                UserFollow.builder()
                        .follower(follower)
                        .following(following)
                        .build()
        );

        assertThatNoException().isThrownBy(() -> followService.follow(1L, 2L));

        then(reliabilityScoreService).should().increase(eq(2L), eq(ScoreEvent.FOLLOWED));
    }

    @Test
    @DisplayName("팔로우 실패 - 자기 자신 팔로우")
    void follow_fail_self() {
        assertThatThrownBy(() -> followService.follow(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("자기 자신을 팔로우할 수 없습니다.");
    }

    @Test
    @DisplayName("팔로우 실패 - 중복 팔로우")
    void follow_fail_duplicate() {
        given(userFollowRepository.existsByFollowerIdAndFollowingId(anyLong(), anyLong())).willReturn(true);

        assertThatThrownBy(() -> followService.follow(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 팔로우 중입니다.");
    }

    @Test
    @DisplayName("언팔로우 성공")
    void unfollow_success() {
        given(userFollowRepository.existsByFollowerIdAndFollowingId(anyLong(), anyLong())).willReturn(true);

        assertThatNoException().isThrownBy(() -> followService.unfollow(1L, 2L));
        then(userFollowRepository).should().deleteByFollowerIdAndFollowingId(1L, 2L);
    }

    @Test
    @DisplayName("언팔로우 실패 - 팔로우 안 한 유저")
    void unfollow_fail_not_following() {
        given(userFollowRepository.existsByFollowerIdAndFollowingId(anyLong(), anyLong())).willReturn(false);

        assertThatThrownBy(() -> followService.unfollow(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("팔로우 중이 아닙니다.");
    }
}