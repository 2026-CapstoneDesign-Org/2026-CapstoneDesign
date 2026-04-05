package com.example.Capstone.service;

import org.springframework.stereotype.Service;

import com.example.Capstone.domain.User;
import com.example.Capstone.dto.request.AdditionalInfoRequest;
import com.example.Capstone.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
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
}
