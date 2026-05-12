package com.example.Capstone.dto.response;

import java.time.LocalDateTime;

import com.example.Capstone.domain.UserList;

public record UserListResponse(
        Long id,
        String title,
        String description,
        String regionName,
        Boolean isPublic,
        Boolean isRepresentative,
        LocalDateTime createdAt,
        boolean isLiked 
) {
    public static UserListResponse from(UserList userList, boolean isLiked) {
        return new UserListResponse(
                userList.getId(),
                userList.getTitle(),
                userList.getDescription(),
                userList.getRegionName(),
                userList.getIsPublic(),
                userList.getIsRepresentative(),
                userList.getCreatedAt(),
                isLiked
        );
    }

    public static UserListResponse from(UserList userList) {
        return from(userList, false);
    }
}
