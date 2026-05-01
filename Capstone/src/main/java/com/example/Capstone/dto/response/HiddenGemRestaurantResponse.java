package com.example.Capstone.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record HiddenGemRestaurantResponse(
        LocalDateTime generatedAt,
        String regionTownName,
        int limit,
        List<HiddenGemRestaurantItemResponse> items
) {
    public static HiddenGemRestaurantResponse of(
            LocalDateTime generatedAt,
            String regionTownName,
            int limit,
            List<HiddenGemRestaurantItemResponse> items
    ) {
        return new HiddenGemRestaurantResponse(
                generatedAt,
                regionTownName,
                limit,
                items
        );
    }
}
