package com.example.Capstone.service.search.support;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.User;
import com.example.Capstone.client.PcmapSearchClient.PcmapRestaurantCandidate;
import com.example.Capstone.dto.response.SearchInterpretationResponse;
import com.example.Capstone.dto.response.SearchRegionItemResponse;
import com.example.Capstone.dto.response.SearchRestaurantItemResponse;
import com.example.Capstone.dto.response.SearchUserItemResponse;
import com.example.Capstone.service.support.RestaurantCategoryResolver;

public final class SearchResultMapper {

    private SearchResultMapper() {
    }

    public static SearchInterpretationResponse toInterpretationResponse(SearchInterpretation interpretation) {
        return new SearchInterpretationResponse(
                interpretation.rawQuery(),
                interpretation.normalizedQuery(),
                interpretation.explicitUserQuery(),
                interpretation.explicitRegionQuery(),
                interpretation.userKeyword(),
                interpretation.regionKeyword(),
                interpretation.restaurantKeyword(),
                interpretation.genericBrowseQuery(),
                interpretation.fallbackUsed()
        );
    }

    public static SearchRestaurantItemResponse toInternalRestaurantItem(Restaurant restaurant, String matchedBy, String source) {
        return new SearchRestaurantItemResponse(
                restaurant.getId(),
                restaurant.getPcmapPlaceId(),
                source,
                restaurant.getName(),
                restaurant.getRegionName(),
                restaurant.getDisplayAddress(),
                restaurant.getLat(),
                restaurant.getLng(),
                restaurant.getImageUrl(),
                restaurant.getPrimaryCategoryName(),
                restaurant.getCategoryNames(),
                matchedBy
        );
    }

    public static SearchRestaurantItemResponse toExternalRestaurantItem(
            PcmapRestaurantCandidate candidate,
            String regionName,
            String source,
            String matchedBy,
            String resolvedAddress
    ) {
        return new SearchRestaurantItemResponse(
                null,
                candidate.placeId(),
                source,
                candidate.name(),
                regionName,
                resolvedAddress,
                parseCoordinate(candidate.y()),
                parseCoordinate(candidate.x()),
                candidate.imageUrl(),
                RestaurantCategoryResolver.resolvePrimaryCategory(candidate.categoryName()),
                candidate.categoryName() == null || candidate.categoryName().isBlank()
                        ? List.of()
                        : List.of(candidate.categoryName()),
                matchedBy
        );
    }

    public static SearchUserItemResponse toUserItem(User user) {
        return new SearchUserItemResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl()
        );
    }

    public static SearchRegionItemResponse toRegionItem(String regionName, String displayName, String regionKeyword) {
        return new SearchRegionItemResponse(
                regionName,
                displayName,
                regionKeyword,
                "/rankings/restaurants?regionName=" + URLEncoder.encode(regionName, StandardCharsets.UTF_8)
        );
    }

    private static BigDecimal parseCoordinate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
