package com.example.Capstone.dto.request;

public record UpdateUserRequest(
    String nickname,
    String profileImageUrl
) {}