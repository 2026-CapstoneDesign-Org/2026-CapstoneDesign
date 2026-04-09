package com.example.Capstone.dto.response;

import com.example.Capstone.domain.User;

public record FollowUserResponse(
    Long userId,
    String nickname,
    String profileImageUrl
) {
    public static FollowUserResponse from(User user) {
        return new FollowUserResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl()
        );
    }
}
