package com.example.Capstone.service.search.support;

import java.util.Comparator;
import java.util.Locale;

import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.RestaurantMenuItem;
import com.example.Capstone.domain.RestaurantTag;
import com.example.Capstone.dto.response.SearchRestaurantItemResponse;

public final class SearchRestaurantMatcher {

    public static final String MATCH_NAME_PREFIX = "NAME_PREFIX";
    public static final String MATCH_NAME_CONTAINS = "NAME_CONTAINS";
    public static final String MATCH_CATEGORY = "CATEGORY";
    public static final String MATCH_MENU = "MENU";
    public static final String MATCH_TAG = "TAG";
    public static final String MATCH_ADDRESS = "ADDRESS";
    public static final String MATCH_REGION = "REGION";
    public static final String MATCH_EXTERNAL_FALLBACK = "EXTERNAL_FALLBACK";

    private SearchRestaurantMatcher() {
    }

    public static String resolveMatchedBy(Restaurant restaurant, SearchInterpretation interpretation) {
        String restaurantKeyword = interpretation.restaurantKeyword();

        if (restaurantKeyword != null) {
            if (startsWithIgnoreCase(restaurant.getName(), restaurantKeyword)) {
                return MATCH_NAME_PREFIX;
            }
            if (containsIgnoreCase(restaurant.getName(), restaurantKeyword)) {
                return MATCH_NAME_CONTAINS;
            }
            if (containsIgnoreCase(restaurant.getCategoryName(), restaurantKeyword)
                    || containsIgnoreCase(restaurant.getPrimaryCategoryName(), restaurantKeyword)) {
                return MATCH_CATEGORY;
            }
            if (restaurant.getMenuItems().stream().anyMatch(menuItem -> matchesMenu(menuItem, restaurantKeyword))) {
                return MATCH_MENU;
            }
            if (restaurant.getRestaurantTags().stream().anyMatch(tag -> matchesTag(tag, restaurantKeyword))) {
                return MATCH_TAG;
            }
            if (containsIgnoreCase(restaurant.getAddress(), restaurantKeyword)
                    || containsIgnoreCase(restaurant.getRoadAddress(), restaurantKeyword)) {
                return MATCH_ADDRESS;
            }
        }

        if (interpretation.regionKeyword() != null && matchesRegion(restaurant, interpretation.regionKeyword())) {
            return MATCH_REGION;
        }

        return null;
    }

    public static Comparator<SearchRestaurantItemResponse> internalResultComparator() {
        return Comparator
                .comparingInt((SearchRestaurantItemResponse item) -> matchPriority(item.matchedBy()))
                .thenComparing(SearchRestaurantItemResponse::restaurantName, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(SearchRestaurantItemResponse::restaurantId, Comparator.nullsLast(Long::compareTo));
    }

    public static int matchPriority(String matchedBy) {
        return switch (matchedBy) {
            case MATCH_NAME_PREFIX -> 0;
            case MATCH_NAME_CONTAINS -> 1;
            case MATCH_CATEGORY -> 2;
            case MATCH_MENU -> 3;
            case MATCH_TAG -> 4;
            case MATCH_ADDRESS -> 5;
            case MATCH_REGION -> 6;
            default -> 100;
        };
    }

    public static boolean matchesRegion(Restaurant restaurant, String regionKeyword) {
        return containsIgnoreCase(restaurant.getRegionName(), regionKeyword)
                || containsIgnoreCase(restaurant.getRegionCityName(), regionKeyword)
                || containsIgnoreCase(restaurant.getRegionDistrictName(), regionKeyword)
                || containsIgnoreCase(restaurant.getRegionCountyName(), regionKeyword)
                || containsIgnoreCase(restaurant.getRegionTownName(), regionKeyword)
                || restaurant.getRegionFilterNames().stream()
                        .anyMatch(filterName -> containsIgnoreCase(filterName, regionKeyword));
    }

    public static String resolveRegionDisplayName(Restaurant restaurant, String regionKeyword) {
        if (containsIgnoreCase(restaurant.getRegionTownName(), regionKeyword)) {
            return restaurant.getRegionTownName();
        }
        if (containsIgnoreCase(restaurant.getRegionCountyName(), regionKeyword)) {
            return restaurant.getRegionCountyName();
        }
        if (containsIgnoreCase(restaurant.getRegionDistrictName(), regionKeyword)) {
            return restaurant.getRegionDistrictName();
        }
        if (containsIgnoreCase(restaurant.getRegionCityName(), regionKeyword)) {
            return restaurant.getRegionCityName();
        }
        return restaurant.getRegionName();
    }

    public static boolean containsIgnoreCase(String source, String keyword) {
        if (source == null || keyword == null) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private static boolean startsWithIgnoreCase(String source, String prefix) {
        if (source == null || prefix == null) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    private static boolean matchesMenu(RestaurantMenuItem menuItem, String keyword) {
        return containsIgnoreCase(menuItem.getMenuName(), keyword)
                || containsIgnoreCase(menuItem.getNormalizedMenuName(), keyword);
    }

    private static boolean matchesTag(RestaurantTag restaurantTag, String keyword) {
        return restaurantTag.getTag() != null
                && Boolean.TRUE.equals(restaurantTag.getTag().getIsActive())
                && containsIgnoreCase(restaurantTag.getTag().getTagName(), keyword);
    }
}
