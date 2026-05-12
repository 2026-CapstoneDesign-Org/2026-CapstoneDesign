package com.example.Capstone.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.example.Capstone.domain.UserList;

public record UserListDetailResponse(
        Long id,
        String title,
        String description,
        String regionName,
        Boolean isPublic,
        Boolean isRepresentative,
        List<ListRestaurantResponse> restaurants,
        LocalDateTime createdAt,
        boolean isLiked
) {
    public static UserListDetailResponse from(UserList userList, boolean isLiked) {
        return new UserListDetailResponse(
                userList.getId(),
                userList.getTitle(),
                userList.getDescription(),
                userList.getRegionName(),
                userList.getIsPublic(),
                userList.getIsRepresentative(),
                userList.getListRestaurants().stream()
                        .map(ListRestaurantResponse::from)
                        .toList(),
                userList.getCreatedAt(),
                isLiked
        );
    }

    public static UserListDetailResponse from(UserList userList) {
        return from(userList, false);
    }
}
