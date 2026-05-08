package com.example.Capstone.dto.response;

import com.example.Capstone.domain.RestaurantPhoto;

public record RestaurantPhotoResponse(
        String imageUrl,
        String source,
        int displayOrder
) {
    public static RestaurantPhotoResponse from(RestaurantPhoto photo) {
        return new RestaurantPhotoResponse(
                photo.getImageUrl(),
                photo.getSource(),
                photo.getDisplayOrder()
        );
    }
}
