package com.example.Capstone.service.search.support;

public record SearchInterpretation(
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
    public SearchInterpretation(
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
                false,
                null,
                0
        );
    }

    public SearchInterpretation withFallbackUsed(boolean fallbackUsed) {
        return withFallbackDecision(fallbackUsed, fallbackUsed, fallbackUsed ? "FALLBACK_USED" : null, 0);
    }

    public SearchInterpretation withFallbackDecision(boolean fallbackUsed, String fallbackReason) {
        return withFallbackDecision(fallbackUsed, fallbackUsed, fallbackReason, fallbackUsed ? 1 : 0);
    }

    public SearchInterpretation withFallbackDecision(
            boolean fallbackUsed,
            boolean fallbackAttempted,
            String fallbackReason,
            int fallbackResultCount
    ) {
        return new SearchInterpretation(
                rawQuery,
                normalizedQuery,
                explicitUserQuery,
                explicitRegionQuery,
                userKeyword,
                regionKeyword,
                restaurantKeyword,
                genericBrowseQuery,
                fallbackUsed,
                fallbackAttempted,
                fallbackReason,
                fallbackResultCount
        );
    }
}
