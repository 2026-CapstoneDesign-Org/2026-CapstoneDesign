package com.example.Capstone.dto.response;

import java.time.LocalDateTime;

import com.example.Capstone.domain.User;

public record UserResponse(
        Long id,
        String nickname,
        String profileImageUrl,
        Short birthYear,
        Short birthMonth,
        Short birthDay,
        User.Gender gender,
        User.Role role,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getBirthYear(),
                user.getBirthMonth(),
                user.getBirthDay(),
                user.getGender(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
