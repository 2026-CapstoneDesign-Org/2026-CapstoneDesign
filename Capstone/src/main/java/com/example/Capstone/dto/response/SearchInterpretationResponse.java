package com.example.Capstone.dto.response;

public record SearchInterpretationResponse(
        String rawQuery,
        String normalizedQuery,
        boolean explicitUserQuery,
        boolean explicitRegionQuery,
        String userKeyword,
        String regionKeyword,
        String restaurantKeyword,
        boolean genericBrowseQuery,
        boolean fallbackUsed
) {
}
