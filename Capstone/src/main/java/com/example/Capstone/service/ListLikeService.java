package com.example.Capstone.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.common.enums.ScoreEvent;
import com.example.Capstone.domain.ListLike;
import com.example.Capstone.domain.User;
import com.example.Capstone.domain.UserList;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.ListLikeRepository;
import com.example.Capstone.repository.ListRestaurantRepository;
import com.example.Capstone.repository.UserListRepository;
import com.example.Capstone.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListLikeService {

    private final ListLikeRepository listLikeRepository;
    private final UserRepository userRepository;
    private final UserListRepository userListRepository;
    private final ListRestaurantRepository listRestaurantRepository;
    private final ReliabilityScoreService reliabilityScoreService;

    // 좋아요
    @Transactional
    public void like(Long userId, Long listId) {
        if (listLikeRepository.existsByUserIdAndUserListId(userId, listId)) {
            throw new BusinessException("이미 좋아요한 리스트입니다.", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));
        UserList userList = userListRepository.findByIdAndIsDeletedFalse(listId)
                .orElseThrow(() -> new EntityNotFoundException("리스트를 찾을 수 없습니다."));

        listLikeRepository.save(ListLike.builder()
                .user(user)
                .userList(userList)
                .build());

        // 아이템 5개 이상인 리스트만 점수 제공
        long itemCount = listRestaurantRepository.countByUserListId(listId);
        if (itemCount >= 5) {
            reliabilityScoreService.increase(userList.getUser().getId(), ScoreEvent.LIST_LIKED);
        }
    }

    // 좋아요 취소
    @Transactional
    public void unlike(Long userId, Long listId) {
        if (!listLikeRepository.existsByUserIdAndUserListId(userId, listId)) {
            throw new BusinessException("좋아요하지 않은 리스트입니다.", HttpStatus.BAD_REQUEST);
        }
        listLikeRepository.deleteByUserIdAndUserListId(userId, listId);
    }

    // 좋아요 수
    public long getLikeCount(Long listId) {
        return listLikeRepository.countByUserListId(listId);
    }
}
