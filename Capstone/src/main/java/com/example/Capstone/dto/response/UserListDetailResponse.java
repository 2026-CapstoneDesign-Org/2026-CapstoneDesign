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
        LocalDateTime createdAt
) {
    public static UserListDetailResponse from(UserList userList) {
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
                userList.getCreatedAt()
        );
    }
}
