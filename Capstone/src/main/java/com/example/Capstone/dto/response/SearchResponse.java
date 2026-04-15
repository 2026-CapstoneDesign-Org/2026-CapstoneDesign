package com.example.Capstone.dto.response;

import java.util.List;

public record SearchResponse(
        String query,
        String primaryType,
        SearchInterpretationResponse interpretation,
        int restaurantCount,
        int userCount,
        int regionCount,
        List<SearchRestaurantItemResponse> restaurants,
        List<SearchUserItemResponse> users,
        List<SearchRegionItemResponse> regions
) {
}
