package com.example.Capstone.service;

import com.example.Capstone.common.enums.ScoreEvent;
import com.example.Capstone.domain.ReliabilityScore;
import com.example.Capstone.domain.User;
import com.example.Capstone.repository.ReliabilityScoreRepository;
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
class ReliabilityScoreServiceTest {

    @Mock ReliabilityScoreRepository scoreRepository;
    @Mock UserRepository userRepository;

    @InjectMocks ReliabilityScoreService reliabilityScoreService;

    private User user;
    private ReliabilityScore reliabilityScore;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .provider("KAKAO")
                .providerUserId("test_1")
                .nickname("테스트유저1")
                .profileImageUrl("http://default.img")
                .role(User.Role.USER)
                .build();

        reliabilityScore = ReliabilityScore.builder()
                .user(user)
                .build();
    }

    @Test
    @DisplayName("점수 증가 - 희소성 보정 적용")
    void increase_with_rarity_coefficient() {
        given(scoreRepository.findByUserId(anyLong())).willReturn(Optional.of(reliabilityScore));

        reliabilityScoreService.increase(1L, ScoreEvent.REVIEW_CREATED);

        // 기본값 20.0 에서 증가했는지 확인
        assertThat(reliabilityScore.getScore()).isGreaterThan(20.0);
    }

    @Test
    @DisplayName("점수 감소 - 희소성 보정 없음")
    void decrease_without_rarity_coefficient() {
        given(scoreRepository.findByUserId(anyLong())).willReturn(Optional.of(reliabilityScore));

        reliabilityScoreService.decrease(1L, ScoreEvent.REVIEW_DISLIKED);

        // 기본값 20.0 에서 감소했는지 확인
        assertThat(reliabilityScore.getScore()).isLessThan(20.0);
    }

    @Test
    @DisplayName("점수는 0.0 미만으로 내려가지 않음")
    void score_not_below_zero() {
        given(scoreRepository.findByUserId(anyLong())).willReturn(Optional.of(reliabilityScore));

        // 큰 감소 이벤트 여러 번 적용
        for (int i = 0; i < 20; i++) {
            reliabilityScoreService.decrease(1L, ScoreEvent.MANUAL_REPORTED);
        }

        assertThat(reliabilityScore.getScore()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("점수는 100.0 초과하지 않음")
    void score_not_above_hundred() {
        given(scoreRepository.findByUserId(anyLong())).willReturn(Optional.of(reliabilityScore));

        // 많은 증가 이벤트 적용
        for (int i = 0; i < 1000; i++) {
            reliabilityScoreService.increase(1L, ScoreEvent.REVIEW_CREATED);
        }

        assertThat(reliabilityScore.getScore()).isLessThanOrEqualTo(100.0);
    }

    @Test
    @DisplayName("등급 - 점수 구간별 정확히 부여")
    void grade_correct_by_score() {

        assertThat(reliabilityScore.getGrade()).isEqualTo("tier1");

        // 점수 강제 업데이트 후 등급 확인
        reliabilityScore.updateScore(38.0);
        assertThat(reliabilityScore.getGrade()).isEqualTo("tier2");

        reliabilityScore.updateScore(55.0);
        assertThat(reliabilityScore.getGrade()).isEqualTo("tier3");

        reliabilityScore.updateScore(65.0);
        assertThat(reliabilityScore.getGrade()).isEqualTo("tier4");

        reliabilityScore.updateScore(75.0);
        assertThat(reliabilityScore.getGrade()).isEqualTo("tier5");

        reliabilityScore.updateScore(85.0);
        assertThat(reliabilityScore.getGrade()).isEqualTo("tier6");

        reliabilityScore.updateScore(93.0);
        assertThat(reliabilityScore.getGrade()).isEqualTo("tier7");

        reliabilityScore.updateScore(97.0);
        assertThat(reliabilityScore.getGrade()).isEqualTo("tier8");
    }

    @Test
    @DisplayName("신규 유저 점수 자동 생성")
    void create_score_for_new_user() {
        given(scoreRepository.findByUserId(anyLong())).willReturn(Optional.empty());
        given(userRepository.findByIdAndIsDeletedFalse(anyLong())).willReturn(Optional.of(user));
        given(scoreRepository.save(any())).willReturn(reliabilityScore);

        reliabilityScoreService.increase(1L, ScoreEvent.REVIEW_CREATED);

        then(scoreRepository).should().save(any());
    }
}