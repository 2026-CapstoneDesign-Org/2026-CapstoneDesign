package com.example.Capstone.dto.response;

public record SearchUserItemResponse(
        Long userId,
        String nickname,
        String profileImageUrl
) {
}
