package com.example.Capstone.service;

import com.example.Capstone.common.enums.ScoreEvent;
import com.example.Capstone.domain.User;
import com.example.Capstone.domain.UserFollow;
import com.example.Capstone.domain.Notification.NotificationType;
import com.example.Capstone.dto.response.FollowCountResponse;
import com.example.Capstone.dto.response.FollowUserResponse;
import com.example.Capstone.dto.response.PageResponse;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.UserFollowRepository;
import com.example.Capstone.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

    private final UserFollowRepository userFollowRepository;
    private final UserRepository userRepository;
    private final ReliabilityScoreService reliabilityScoreService;
    private final NotificationService notificationService;

    @Transactional
    public void follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new BusinessException("자기 자신을 팔로우할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        if (userFollowRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new BusinessException("이미 팔로우 중입니다.", HttpStatus.BAD_REQUEST);
        }

        User follower  = userRepository.findByIdAndIsDeletedFalse(followerId)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));
        User following = userRepository.findByIdAndIsDeletedFalse(followingId)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));

        userFollowRepository.save(UserFollow.builder()
                .follower(follower)
                .following(following)
                .build());

        reliabilityScoreService.increase(followingId, ScoreEvent.FOLLOWED);

        notificationService.send(
                followingId,
                NotificationType.FOLLOW,
                follower.getNickname() + "님이 팔로우했습니다.",
                followerId,
                "USER"
        );
    }

    @Transactional
    public void unfollow(Long followerId, Long followingId) {
        if (!userFollowRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new BusinessException("팔로우 중이 아닙니다.", HttpStatus.BAD_REQUEST);
        }
        userFollowRepository.deleteByFollowerIdAndFollowingId(followerId, followingId);
    }

    public PageResponse<FollowUserResponse> getFollowers(Long userId, Pageable pageable) {
        Page<FollowUserResponse> page = userFollowRepository
                .findAllByFollowingId(userId, pageable)
                .map(follow -> FollowUserResponse.from(follow.getFollower()));
        return PageResponse.from(page);
    }

    public PageResponse<FollowUserResponse> getFollowings(Long userId, Pageable pageable) {
        Page<FollowUserResponse> page = userFollowRepository
                .findAllByFollowerId(userId, pageable)
                .map(follow -> FollowUserResponse.from(follow.getFollowing()));
        return PageResponse.from(page);
    }

    public FollowCountResponse getFollowCount(Long userId) {
        return new FollowCountResponse(
                userFollowRepository.countByFollowingId(userId),
                userFollowRepository.countByFollowerId(userId)
        );
    }

    public boolean isFollowing(Long followerId, Long followingId) {
        return userFollowRepository.existsByFollowerIdAndFollowingId(followerId, followingId);
    }
}
