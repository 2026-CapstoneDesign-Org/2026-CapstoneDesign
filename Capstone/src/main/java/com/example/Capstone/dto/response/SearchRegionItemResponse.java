package com.example.Capstone.dto.response;

public record SearchRegionItemResponse(
        String regionName,
        String displayName,
        String regionKeyword,
        String rankingPath
) {
}
