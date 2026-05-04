package com.example.Capstone.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.common.constant.DefaultImageProvider;
import com.example.Capstone.common.enums.ScoreEvent;
import com.example.Capstone.common.util.NicknameGenerator;
import com.example.Capstone.domain.User;
import com.example.Capstone.dto.request.AdditionalInfoRequest;
import com.example.Capstone.dto.request.UpdateUserRequest;
import com.example.Capstone.dto.response.UserResponse;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final NicknameGenerator nicknameGenerator;
    private final ReliabilityScoreService reliabilityScoreService;

    @Transactional
    public void updateAdditionalInfo(Long userId, AdditionalInfoRequest request) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));
        
        // 최초 입력 여부 확인
        boolean isFirstBirth  = user.getBirthYear() == null;
        boolean isFirstGender = user.getGender() == null;

        user.updateAdditionalInfo(
            request.birthYear(),
            request.birthMonth(),
            request.birthDay(),
            request.gender()
        );

        // 최초 1회만 점수 제공
        if (isFirstBirth) {
            reliabilityScoreService.increase(userId, ScoreEvent.PROFILE_COMPLETED);
        }
        if (isFirstGender) {
            reliabilityScoreService.increase(userId, ScoreEvent.PROFILE_COMPLETED);
        }
    }

    public UserResponse getMyInfo(Long userId) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
            .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));
        return UserResponse.from(user);
    }

    @Transactional
    public void updateUser(Long userId, UpdateUserRequest request) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
            .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));

        if (request.nickname() != null) {
            if (userRepository.existsByNickname(request.nickname())) {
                throw new BusinessException("이미 사용 중인 닉네임입니다.", HttpStatus.BAD_REQUEST);
            }
            // 랜덤 닉네임에서 최초 변경 시만 점수 제공
            boolean isFirstNicknameUpdate = nicknameGenerator.isRandomNickname(user.getNickname());
            user.updateNickname(request.nickname());
            if (isFirstNicknameUpdate) {
                reliabilityScoreService.increase(userId, ScoreEvent.PROFILE_COMPLETED);
        }
    }

    if (request.profileImageUrl() != null) {
        // 기본 이미지에서 최초 변경 시만 점수 제공
        boolean isFirstImageUpdate = DefaultImageProvider.DEFAULT_PROFILE_IMAGE_URL
                .equals(user.getProfileImageUrl());
        user.updateProfileImageUrl(request.profileImageUrl());
        if (isFirstImageUpdate) {
            reliabilityScoreService.increase(userId, ScoreEvent.PROFILE_COMPLETED);
        }
    }
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));
        user.delete();
    }
}
