package com.example.Capstone.dto.response;

import java.util.List;

public record ReviewSummaryResponse(
        Long restaurantId,
        String restaurantName,
        String summary,
        List<String> positives,
        List<String> negatives,
        String sentiment,
        int reviewCount
) {}
