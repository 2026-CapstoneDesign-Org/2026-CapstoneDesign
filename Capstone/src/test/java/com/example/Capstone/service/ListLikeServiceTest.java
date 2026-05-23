package com.example.Capstone.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.Capstone.common.enums.ScoreEvent;
import com.example.Capstone.domain.ListLike;
import com.example.Capstone.domain.User;
import com.example.Capstone.domain.UserList;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.ListLikeRepository;
import com.example.Capstone.repository.ListRestaurantRepository;
import com.example.Capstone.repository.UserListRepository;
import com.example.Capstone.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ListLikeServiceTest {

    @Mock
    private ListLikeRepository listLikeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserListRepository userListRepository;

    @Mock
    private ListRestaurantRepository listRestaurantRepository;

    @Mock
    private ReliabilityScoreService reliabilityScoreService;

    @InjectMocks
    private ListLikeService listLikeService;

    private User user;
    private User owner;
    private UserList userList;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .provider("KAKAO")
                .providerUserId("test_1")
                .nickname("test_user_1")
                .profileImageUrl("http://default.img")
                .role(User.Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        owner = User.builder()
                .provider("KAKAO")
                .providerUserId("test_2")
                .nickname("test_user_2")
                .profileImageUrl("http://default.img")
                .role(User.Role.USER)
                .build();
        ReflectionTestUtils.setField(owner, "id", 2L);

        userList = UserList.builder()
                .user(owner)
                .title("test-list")
                .description("description")
                .regionName("Seoul")
                .build();
        ReflectionTestUtils.setField(userList, "id", 1L);
    }

    @Test
    @DisplayName("List like succeeds and scores owner when list has 5 or more items")
    void like_success_with_5_items() {
        given(listLikeRepository.existsByUserIdAndUserListId(anyLong(), anyLong())).willReturn(false);
        given(userRepository.findByIdAndIsDeletedFalse(anyLong())).willReturn(Optional.of(user));
        given(userListRepository.findByIdAndIsDeletedFalse(anyLong())).willReturn(Optional.of(userList));
        given(listLikeRepository.save(any())).willReturn(ListLike.builder()
                .user(user)
                .userList(userList)
                .build());
        given(listRestaurantRepository.countByUserListId(anyLong())).willReturn(5L);

        assertThatNoException().isThrownBy(() -> listLikeService.like(1L, 1L));

        then(reliabilityScoreService).should().increase(eq(2L), eq(ScoreEvent.LIST_LIKED));
    }

    @Test
    @DisplayName("List like succeeds without score when list has fewer than 5 items")
    void like_success_without_score_under_5_items() {
        given(listLikeRepository.existsByUserIdAndUserListId(anyLong(), anyLong())).willReturn(false);
        given(userRepository.findByIdAndIsDeletedFalse(anyLong())).willReturn(Optional.of(user));
        given(userListRepository.findByIdAndIsDeletedFalse(anyLong())).willReturn(Optional.of(userList));
        given(listLikeRepository.save(any())).willReturn(ListLike.builder()
                .user(user)
                .userList(userList)
                .build());
        given(listRestaurantRepository.countByUserListId(anyLong())).willReturn(4L);

        assertThatNoException().isThrownBy(() -> listLikeService.like(1L, 1L));

        then(reliabilityScoreService).should(never()).increase(any(), any());
    }

    @Test
    @DisplayName("List like fails when duplicated")
    void like_fail_duplicate() {
        given(listLikeRepository.existsByUserIdAndUserListId(anyLong(), anyLong())).willReturn(true);

        assertThatThrownBy(() -> listLikeService.like(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 좋아요한 리스트입니다.");
    }

    @Test
    @DisplayName("List unlike succeeds")
    void unlike_success() {
        given(listLikeRepository.existsByUserIdAndUserListId(anyLong(), anyLong())).willReturn(true);

        assertThatNoException().isThrownBy(() -> listLikeService.unlike(1L, 1L));
        then(listLikeRepository).should().deleteByUserIdAndUserListId(1L, 1L);
    }

    @Test
    @DisplayName("List unlike fails when not liked")
    void unlike_fail_not_liked() {
        given(listLikeRepository.existsByUserIdAndUserListId(anyLong(), anyLong())).willReturn(false);

        assertThatThrownBy(() -> listLikeService.unlike(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("좋아요하지 않은 리스트입니다.");
    }
}
