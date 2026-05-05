package com.example.Capstone.service;

import com.example.Capstone.common.enums.ScoreEvent;
import com.example.Capstone.domain.*;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.*;
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
class ListLikeServiceTest {

    @Mock ListLikeRepository listLikeRepository;
    @Mock UserRepository userRepository;
    @Mock UserListRepository userListRepository;
    @Mock ListRestaurantRepository listRestaurantRepository;
    @Mock ReliabilityScoreService reliabilityScoreService;

    @InjectMocks ListLikeService listLikeService;

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

        userList = UserList.builder()
                .user(user)
                .title("테스트리스트")
                .description("설명")
                .regionName("서울")
                .build();
    }

    @Test
    @DisplayName("리스트 좋아요 성공 - 아이템 5개 이상 점수 제공")
    void like_success_with_5_items() {
        given(listLikeRepository.existsByUserIdAndUserListId(anyLong(), anyLong())).willReturn(false);
        given(userRepository.findByIdAndIsDeletedFalse(anyLong())).willReturn(Optional.of(user));
        given(userListRepository.findByIdAndIsDeletedFalse(anyLong())).willReturn(Optional.of(userList));
        given(listLikeRepository.save(any())).willReturn(
                ListLike.builder()
                        .user(user)
                        .userList(userList)
                        .build()
        );
        given(listRestaurantRepository.countByUserListId(anyLong())).willReturn(5L);

        assertThatNoException().isThrownBy(() -> listLikeService.like(1L, 1L));

        then(reliabilityScoreService).should().increase(any(), eq(ScoreEvent.LIST_LIKED));
    }

    @Test
    @DisplayName("리스트 좋아요 성공 - 아이템 5개 미만 점수 미제공")
    void like_success_without_score_under_5_items() {
        given(listLikeRepository.existsByUserIdAndUserListId(anyLong(), anyLong())).willReturn(false);
        given(userRepository.findByIdAndIsDeletedFalse(anyLong())).willReturn(Optional.of(user));
        given(userListRepository.findByIdAndIsDeletedFalse(anyLong())).willReturn(Optional.of(userList));
        given(listLikeRepository.save(any())).willReturn(
                ListLike.builder()
                        .user(user)
                        .userList(userList)
                        .build()
        );
        given(listRestaurantRepository.countByUserListId(anyLong())).willReturn(4L);

        assertThatNoException().isThrownBy(() -> listLikeService.like(1L, 1L));

        then(reliabilityScoreService).should(never()).increase(any(), any());
    }

    @Test
    @DisplayName("리스트 좋아요 실패 - 중복 좋아요")
    void like_fail_duplicate() {
        given(listLikeRepository.existsByUserIdAndUserListId(anyLong(), anyLong())).willReturn(true);

        assertThatThrownBy(() -> listLikeService.like(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 좋아요한 리스트입니다.");
    }

    @Test
    @DisplayName("리스트 좋아요 취소 성공")
    void unlike_success() {
        given(listLikeRepository.existsByUserIdAndUserListId(anyLong(), anyLong())).willReturn(true);

        assertThatNoException().isThrownBy(() -> listLikeService.unlike(1L, 1L));
        then(listLikeRepository).should().deleteByUserIdAndUserListId(1L, 1L);
    }

    @Test
    @DisplayName("리스트 좋아요 취소 실패 - 좋아요 안 한 리스트")
    void unlike_fail_not_liked() {
        given(listLikeRepository.existsByUserIdAndUserListId(anyLong(), anyLong())).willReturn(false);

        assertThatThrownBy(() -> listLikeService.unlike(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("좋아요하지 않은 리스트입니다.");
    }
}