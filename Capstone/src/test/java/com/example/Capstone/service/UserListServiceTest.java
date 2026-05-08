package com.example.Capstone.service;

import com.example.Capstone.common.enums.ScoreEvent;
import com.example.Capstone.domain.*;
import com.example.Capstone.dto.request.AddRestaurantRequest;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserListServiceTest {

    @Mock UserListRepository userListRepository;
    @Mock UserRepository userRepository;
    @Mock RestaurantRepository restaurantRepository;
    @Mock ListRestaurantRepository listRestaurantRepository;
    @Mock ReliabilityScoreService reliabilityScoreService;

    @InjectMocks UserListService userListService;

    private User user;
    private UserList userList;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .provider("KAKAO")
                .providerUserId("test_1")
                .nickname("테스트유저1")
                .profileImageUrl("http://default.img")
                .role(User.Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        userList = UserList.builder()
                .user(user)
                .title("테스트리스트")
                .description("설명")
                .regionName("서울")
                .build();
    }

    @Test
    @DisplayName("대표 리스트 지정 - 최초 1회만 점수 제공")
    void setRepresentative_score_first_time_only() {
        given(userListRepository.findAllByUserIdAndIsDeletedFalse(anyLong())).willReturn(List.of(userList));
        given(userListRepository.findByIdAndIsDeletedFalse(anyLong())).willReturn(Optional.of(userList));
        given(userListRepository.existsByUserIdAndIsRepresentativeTrueAndIsDeletedFalse(anyLong()))
                .willReturn(false);  // 최초 지정

        assertThatNoException().isThrownBy(() -> userListService.setRepresentative(1L, 1L));

        then(reliabilityScoreService).should().increase(any(), eq(ScoreEvent.REPRESENTATIVE_SET));
    }

    @Test
    @DisplayName("대표 리스트 변경 - 재지정 시 점수 미제공")
    void setRepresentative_no_score_when_already_set() {
        given(userListRepository.findAllByUserIdAndIsDeletedFalse(anyLong())).willReturn(List.of(userList));
        given(userListRepository.findByIdAndIsDeletedFalse(anyLong())).willReturn(Optional.of(userList));
        given(userListRepository.existsByUserIdAndIsRepresentativeTrueAndIsDeletedFalse(anyLong()))
                .willReturn(true);  // 이미 대표 리스트 있음

        assertThatNoException().isThrownBy(() -> userListService.setRepresentative(1L, 1L));

        then(reliabilityScoreService).should(never()).increase(any(), any());
    }

    @Test
    @DisplayName("대표 리스트 비공개 전환 불가")
    void toggleVisibility_fail_representative() {
        userList.setRepresentative(true);
        userList.setPublic(true);

        given(userListRepository.findByIdAndIsDeletedFalse(anyLong())).willReturn(Optional.of(userList));

        assertThatThrownBy(() -> userListService.toggleVisibility(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("대표 리스트는 비공개로 변경할 수 없습니다.");
    }
}
