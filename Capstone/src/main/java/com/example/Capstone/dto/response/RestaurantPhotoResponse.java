package com.example.Capstone.dto.response;

public record RestaurantPhotoResponse(
        String imageUrl,
        String source,
        int displayOrder
) {
}
