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
        boolean fallbackUsed,
        boolean fallbackAttempted,
        String fallbackReason,
        int fallbackResultCount
) {
    public SearchInterpretationResponse(
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
        this(
                rawQuery,
                normalizedQuery,
                explicitUserQuery,
                explicitRegionQuery,
                userKeyword,
                regionKeyword,
                restaurantKeyword,
                genericBrowseQuery,
                fallbackUsed,
                fallbackUsed,
                null,
                0
        );
    }
}
