package com.example.Capstone.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.domain.User;
import com.example.Capstone.dto.request.AdditionalInfoRequest;
import com.example.Capstone.dto.request.UpdateUserRequest;
import com.example.Capstone.dto.response.UserResponse;
import com.example.Capstone.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    @Transactional
    public void updateAdditionalInfo(Long userId, AdditionalInfoRequest request) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));
        
        user.updateAdditionalInfo(
            request.birthYear(),
            request.birthMonth(),
            request.birthDay(),
            request.gender()
        );
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
                throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
            }
            user.updateNickname(request.nickname());
        }

        if (request.profileImageUrl() != null) {
            user.updateProfileImageUrl(request.profileImageUrl());
        }
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));
        user.delete();
    }
}
