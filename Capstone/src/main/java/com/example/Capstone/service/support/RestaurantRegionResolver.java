package com.example.Capstone.service.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class RestaurantRegionResolver {

    private static final String CITY_SUFFIX = "시";
    private static final String DISTRICT_SUFFIX = "구";
    private static final String COUNTY_SUFFIX = "군";
    private static final List<String> TOWN_SUFFIXES = List.of("읍", "면", "동", "리");
    private static final List<String> COUNTY_TOWN_SUFFIXES = List.of("읍", "면");
    private static final List<String> DISTRICT_TOWN_SUFFIXES = List.of("동");

    private RestaurantRegionResolver() {
    }

    public static RegionSchema resolve(String fallbackRegionName, String addressValue, String... fallbackValues) {
        List<String> primaryTokens = tokenize(addressValue);
        List<String> allTokens = tokenize(addressValue, fallbackValues);

        TokenMatch primaryCounty = findLastTokenWithSuffix(primaryTokens, COUNTY_SUFFIX);
        TokenMatch primaryCity = findLastTokenWithSuffix(primaryTokens, CITY_SUFFIX);
        TokenMatch primaryDistrict = findLastTokenWithSuffix(primaryTokens, DISTRICT_SUFFIX);
        TokenMatch fallbackCounty = findLastTokenWithSuffix(allTokens, COUNTY_SUFFIX);
        TokenMatch fallbackCity = findLastTokenWithSuffix(allTokens, CITY_SUFFIX);
        TokenMatch fallbackDistrict = findLastTokenWithSuffix(allTokens, DISTRICT_SUFFIX);

        String county = firstNonBlank(primaryCounty.token(), fallbackCounty.token());
        String city = firstNonBlank(primaryCity.token(), fallbackCity.token());
        String district = firstNonBlank(primaryDistrict.token(), fallbackDistrict.token());

        int townAnchorIndex = county != null
                ? (primaryCounty.token() == null ? -1 : primaryCounty.index())
                : district != null
                        ? (primaryDistrict.token() == null ? -1 : primaryDistrict.index())
                        : primaryCity.index();
        String town = county != null
                ? findTownToken(primaryTokens, townAnchorIndex, RestaurantRegionResolver::isCountyTownToken)
                : district != null
                        ? firstNonBlank(
                                findTownToken(primaryTokens, townAnchorIndex, RestaurantRegionResolver::isDistrictTownToken),
                                findTownToken(primaryTokens, townAnchorIndex, RestaurantRegionResolver::isCountyTownToken)
                        )
                        : findTownToken(primaryTokens, townAnchorIndex, RestaurantRegionResolver::isAdministrativeTownToken);

        if (county != null) {
            return new RegionSchema(
                    county,
                    city,
                    null,
                    county,
                    town,
                    compact(city, county, town)
            );
        }

        if (city != null && district != null) {
            return new RegionSchema(
                    city + " " + district,
                    city,
                    district,
                    null,
                    town,
                    compact(city, district, town)
            );
        }

        if (city != null) {
            return new RegionSchema(
                    city,
                    city,
                    null,
                    null,
                    town,
                    compact(city, town)
            );
        }

        if (district != null) {
            return new RegionSchema(
                    district,
                    null,
                    district,
                    null,
                    town,
                    compact(district, town)
            );
        }

        String fallback = normalizeText(fallbackRegionName);
        return new RegionSchema(
                fallback,
                null,
                null,
                null,
                town,
                compact(fallback, town)
        );
    }

    private static List<String> tokenize(String primary, String... fallbackValues) {
        List<String> values = new ArrayList<>();
        addTokens(values, primary);
        if (fallbackValues != null) {
            for (String fallbackValue : fallbackValues) {
                addTokens(values, fallbackValue);
            }
        }
        return values;
    }

    private static void addTokens(List<String> values, String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return;
        }
        for (String token : normalized.split("\\s+")) {
            String normalizedToken = normalizeText(token);
            if (normalizedToken != null) {
                values.add(normalizedToken);
            }
        }
    }

    private static TokenMatch findLastTokenWithSuffix(List<String> tokens, String suffix) {
        for (int index = tokens.size() - 1; index >= 0; index -= 1) {
            String token = tokens.get(index);
            if (token.endsWith(suffix)) {
                return new TokenMatch(token, index);
            }
        }
        return new TokenMatch(null, -1);
    }

    private static String findTownToken(List<String> tokens, int anchorIndex, TownPredicate predicate) {
        int startIndex = anchorIndex < 0 ? 0 : anchorIndex + 1;
        for (int index = startIndex; index < tokens.size(); index += 1) {
            if (predicate.test(tokens.get(index))) {
                return tokens.get(index);
            }
        }
        return null;
    }

    private static boolean isAdministrativeTownToken(String token) {
        String normalized = normalizeText(token);
        if (normalized == null) {
            return false;
        }
        if (TOWN_SUFFIXES.stream().anyMatch(normalized::endsWith)) {
            return !normalized.matches("^(?:\\S*?)(?:\\d+|[A-Za-z]+)동$");
        }
        return normalized.matches(".+(?:\\d)?가$");
    }

    private static boolean isDistrictTownToken(String token) {
        if (!isAdministrativeTownToken(token)) {
            return false;
        }
        String normalized = normalizeText(token);
        return DISTRICT_TOWN_SUFFIXES.stream().anyMatch(normalized::endsWith)
                || normalized.matches(".+(?:\\d)?가$");
    }

    private static boolean isCountyTownToken(String token) {
        if (!isAdministrativeTownToken(token)) {
            return false;
        }
        String normalized = normalizeText(token);
        return COUNTY_TOWN_SUFFIXES.stream().anyMatch(normalized::endsWith);
    }

    private static List<String> compact(String... values) {
        LinkedHashSet<String> compacted = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String normalized = normalizeText(value);
                if (normalized != null) {
                    compacted.add(normalized);
                }
            }
        }
        return new ArrayList<>(compacted);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalizeText(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private interface TownPredicate {
        boolean test(String token);
    }

    private record TokenMatch(String token, int index) {
    }

    public record RegionSchema(
            String regionName,
            String regionCityName,
            String regionDistrictName,
            String regionCountyName,
            String regionTownName,
            List<String> regionFilterNames
    ) {
    }
}
