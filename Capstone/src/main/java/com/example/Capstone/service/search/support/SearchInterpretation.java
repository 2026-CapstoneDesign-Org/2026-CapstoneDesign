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
        boolean fallbackUsed
) {
    public SearchInterpretation withFallbackUsed(boolean fallbackUsed) {
        return new SearchInterpretation(
                rawQuery,
                normalizedQuery,
                explicitUserQuery,
                explicitRegionQuery,
                userKeyword,
                regionKeyword,
                restaurantKeyword,
                genericBrowseQuery,
                fallbackUsed
        );
    }
}
