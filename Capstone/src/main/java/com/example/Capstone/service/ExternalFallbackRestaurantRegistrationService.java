package com.example.Capstone.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.client.NaverLocalSearchClient;
import com.example.Capstone.client.NaverLocalSearchClient.NaverLocalRestaurantCandidate;
import com.example.Capstone.client.PcmapPlaceDetailClient;
import com.example.Capstone.client.PcmapPlaceDetailClient.PcmapMenuItem;
import com.example.Capstone.client.PcmapPlaceDetailClient.PcmapRestaurantDetail;
import com.example.Capstone.client.PcmapSearchClient;
import com.example.Capstone.client.PcmapSearchClient.PcmapRestaurantCandidate;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.RestaurantMenuItem;
import com.example.Capstone.dto.request.AddExternalRestaurantRequest;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.RestaurantMenuItemRepository;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.service.support.RestaurantCategoryResolver;
import com.example.Capstone.service.support.RestaurantRegionResolver;
import com.example.Capstone.service.support.RestaurantRegionResolver.RegionSchema;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExternalFallbackRestaurantRegistrationService {

    private static final BigDecimal MAX_REASONABLE_MENU_PRICE = new BigDecimal("10000000");

    private static final List<String> FOOD_CATEGORY_KEYWORDS = List.of(
            "음식점", "식당", "한식", "중식", "일식", "양식", "분식",
            "국밥", "고기", "구이", "국수", "칼국수", "만두", "냉면",
            "초밥", "스시", "치킨", "피자", "햄버거", "샌드위치",
            "브런치", "베이커리", "케이크", "디저트", "카페", "커피",
            "주점", "호프", "맥주", "이자카야", "파스타", "뷔페",
            "조개", "보쌈", "떡볶이", "김밥"
    );
    private static final List<String> BLOCKED_CATEGORY_KEYWORDS = List.of(
            "오토바이", "자동차", "스터디카페", "공유오피스", "사진",
            "스튜디오", "문구", "마트", "편의점", "생활용품", "의류",
            "인테리어", "반려동물", "병원", "입시", "키즈카페",
            "건축", "철거", "공사", "가구", "영화관", "명함"
    );
    private static final List<String> BLOCKED_PLACE_NAME_KEYWORDS = List.of(
            "판넬", "패널", "칸막이", "공사", "철거", "방수", "창고",
            "주방기구", "집기", "자동문"
    );
    private static final List<String> BLOCKED_MENU_RAW_TYPES = List.of(
            "BusStation", "InnerRoute", "BusinessTool", "SubwayStation",
            "SubwayStationInfo", "RelatedLink", "FsasReview",
            "InformationFacilities", "NewBusinessHour", "RestaurantSeatItems",
            "PlaceDetailBase", "UgcImage"
    );
    private static final List<String> BLOCKED_MENU_NAME_KEYWORDS = List.of(
            "스마트콜", "에버라인", "정류장", "아파트", "예약",
            "네이버주문", "네이버예약"
    );

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMenuItemRepository restaurantMenuItemRepository;
    private final PcmapSearchClient pcmapSearchClient;
    private final PcmapPlaceDetailClient pcmapPlaceDetailClient;
    private final NaverLocalSearchClient naverLocalSearchClient;

    @Transactional
    public Restaurant registerVerifiedRestaurant(AddExternalRestaurantRequest request, String listRegionName) {
        PcmapRestaurantCandidate candidate = resolveExternalCandidate(request);
        PcmapRestaurantDetail detail = pcmapPlaceDetailClient.fetchRestaurantDetail(candidate.placeId())
                .orElseThrow(() -> new BusinessException(
                        "External restaurant detail could not be verified.",
                        HttpStatus.BAD_REQUEST
                ));
        validatePlaceIdentity(candidate, detail);

        String address = resolveAddress(detail, candidate);
        if (address == null) {
            throw new BusinessException("External restaurant address could not be verified.", HttpStatus.BAD_REQUEST);
        }

        RegionSchema regionSchema = RestaurantRegionResolver.resolve(
                listRegionName,
                address,
                detail.roadAddress(),
                candidate.address(),
                candidate.roadAddress(),
                candidate.fullAddress()
        );
        validateRegionMatch(listRegionName, regionSchema.regionName());
        validateFoodPlace(candidate, detail);

        Optional<Restaurant> byPlaceId = restaurantRepository.findByPcmapPlaceId(candidate.placeId());
        if (byPlaceId.isPresent()) {
            Restaurant existing = byPlaceId.get();
            validateVisibleRestaurant(existing);
            validateRegionMatch(listRegionName, existing.getRegionName());
            return existing;
        }

        Optional<Restaurant> byNameAndAddress = restaurantRepository.findByNameAndAddress(
                resolveName(detail, candidate),
                address
        );
        if (byNameAndAddress.isPresent()) {
            Restaurant existing = byNameAndAddress.get();
            validateVisibleRestaurant(existing);
            validateRegionMatch(listRegionName, existing.getRegionName());
            return existing;
        }

        Restaurant restaurant = restaurantRepository.save(createRestaurant(candidate, detail, address, regionSchema));
        replaceMenuItems(restaurant, detail.menus());
        return restaurant;
    }

    private PcmapRestaurantCandidate resolveExternalCandidate(AddExternalRestaurantRequest request) {
        return pcmapSearchClient.searchRestaurants(request.searchQuery(), 20).stream()
                .filter(candidate -> request.externalPlaceId().equals(candidate.placeId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "External restaurant candidate is no longer present in search results.",
                        HttpStatus.BAD_REQUEST
                ));
    }

    private void validatePlaceIdentity(PcmapRestaurantCandidate candidate, PcmapRestaurantDetail detail) {
        if (!candidate.placeId().equals(detail.placeId())) {
            throw new BusinessException("External restaurant placeId verification failed.", HttpStatus.BAD_REQUEST);
        }
        String detailName = normalizeText(detail.name());
        if (detailName == null) {
            throw new BusinessException("External restaurant name could not be verified.", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateFoodPlace(PcmapRestaurantCandidate candidate, PcmapRestaurantDetail detail) {
        if (hasBlockedCategorySignal(candidate, detail) || hasBlockedPlaceNameSignal(candidate, detail)) {
            throw new BusinessException("External candidate is not a restaurant.", HttpStatus.BAD_REQUEST);
        }

        String businessType = normalizeText(detail.businessType());
        if (businessType != null && "restaurant".equalsIgnoreCase(businessType)) {
            return;
        }
        if (hasFoodCategorySignal(candidate, detail)) {
            return;
        }
        if (detail.menus() != null && detail.menus().stream().anyMatch(this::isReliableFoodMenuItem)) {
            return;
        }

        throw new BusinessException("External candidate lacks reliable restaurant signals.", HttpStatus.BAD_REQUEST);
    }

    private boolean hasFoodCategorySignal(PcmapRestaurantCandidate candidate, PcmapRestaurantDetail detail) {
        String signal = joinSignals(candidate.categoryName(), detail.categoryName(), candidate.name(), detail.name());
        return FOOD_CATEGORY_KEYWORDS.stream().anyMatch(signal::contains);
    }

    private boolean hasBlockedCategorySignal(PcmapRestaurantCandidate candidate, PcmapRestaurantDetail detail) {
        String signal = joinSignals(candidate.categoryName(), detail.categoryName());
        return BLOCKED_CATEGORY_KEYWORDS.stream().anyMatch(signal::contains);
    }

    private boolean hasBlockedPlaceNameSignal(PcmapRestaurantCandidate candidate, PcmapRestaurantDetail detail) {
        String signal = joinSignals(candidate.name(), detail.name());
        return BLOCKED_PLACE_NAME_KEYWORDS.stream().anyMatch(signal::contains);
    }

    private boolean isReliableFoodMenuItem(PcmapMenuItem menuItem) {
        String menuName = normalizeText(menuItem.name());
        if (menuName == null || !hasAlphaOrHangul(menuName) || isDigitsOnly(menuName)) {
            return false;
        }
        if (BLOCKED_MENU_NAME_KEYWORDS.stream().anyMatch(menuName::contains)) {
            return false;
        }
        String rawTypeName = normalizeText(menuItem.rawTypeName());
        if (rawTypeName != null && BLOCKED_MENU_RAW_TYPES.contains(rawTypeName)) {
            return false;
        }
        if (normalizeText(menuItem.priceText()) != null) {
            return true;
        }
        return FOOD_CATEGORY_KEYWORDS.stream().anyMatch(menuName::contains);
    }

    private Restaurant createRestaurant(
            PcmapRestaurantCandidate candidate,
            PcmapRestaurantDetail detail,
            String address,
            RegionSchema regionSchema
    ) {
        Optional<NaverLocalRestaurantCandidate> officialCandidate =
                naverLocalSearchClient.findBestRestaurantMatch(resolveName(detail, candidate), address);
        String categoryName = firstNonBlank(
                officialCandidate.map(NaverLocalRestaurantCandidate::category).orElse(null),
                detail.categoryName(),
                candidate.categoryName()
        );
        String phoneNumber = firstNonBlank(
                detail.phoneNumber(),
                officialCandidate.map(NaverLocalRestaurantCandidate::telephone).orElse(null)
        );

        return Restaurant.builder()
                .name(resolveName(detail, candidate))
                .address(address)
                .roadAddress(normalizeText(detail.roadAddress()))
                .categoryName(categoryName)
                .primaryCategoryName(RestaurantCategoryResolver.resolvePrimaryCategory(categoryName, detail.categoryName(), candidate.categoryName()))
                .regionName(regionSchema.regionName())
                .regionCityName(regionSchema.regionCityName())
                .regionDistrictName(regionSchema.regionDistrictName())
                .regionCountyName(regionSchema.regionCountyName())
                .regionTownName(regionSchema.regionTownName())
                .regionFilterNames(resolveRegionFilterNames(regionSchema, detail, candidate))
                .conveniences(detail.conveniences())
                .lat(toBigDecimal(firstNonBlank(detail.y(), candidate.y())))
                .lng(toBigDecimal(firstNonBlank(detail.x(), candidate.x())))
                .imageUrl(firstNonBlank(detail.imageUrl(), candidate.imageUrl()))
                .phoneNumber(phoneNumber)
                .businessHoursRaw(detail.businessHoursRaw())
                .pcmapPlaceId(candidate.placeId())
                .menuUpdatedAt(LocalDateTime.now())
                .build();
    }

    private void replaceMenuItems(Restaurant restaurant, List<PcmapMenuItem> menuItems) {
        if (menuItems == null || menuItems.isEmpty()) {
            return;
        }

        List<RestaurantMenuItem> items = menuItems.stream()
                .map(menu -> toMenuItem(restaurant, menu))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        if (!items.isEmpty()) {
            restaurantMenuItemRepository.saveAll(items);
        }
    }

    private Optional<RestaurantMenuItem> toMenuItem(Restaurant restaurant, PcmapMenuItem menu) {
        String menuName = normalizeMenuName(menu.name());
        if (menuName == null || !hasAlphaOrHangul(menuName) || isDigitsOnly(menuName)) {
            return Optional.empty();
        }
        String rawTypeName = normalizeText(menu.rawTypeName());
        if (rawTypeName != null && BLOCKED_MENU_RAW_TYPES.contains(rawTypeName)) {
            return Optional.empty();
        }
        if (BLOCKED_MENU_NAME_KEYWORDS.stream().anyMatch(menuName::contains)) {
            return Optional.empty();
        }

        return Optional.of(RestaurantMenuItem.builder()
                .restaurant(restaurant)
                .displayOrder(menu.displayOrder())
                .menuName(menuName)
                .normalizedMenuName(menuName)
                .priceText(normalizeText(menu.priceText()))
                .priceValue(toMenuPrice(menu.priceText()))
                .description(normalizeText(menu.description()))
                .build());
    }

    private List<String> resolveRegionFilterNames(
            RegionSchema regionSchema,
            PcmapRestaurantDetail detail,
            PcmapRestaurantCandidate candidate
    ) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (regionSchema.regionFilterNames() != null) {
            regionSchema.regionFilterNames().stream()
                    .map(this::normalizeText)
                    .filter(value -> value != null)
                    .forEach(values::add);
        }
        addIfPresent(values, regionSchema.regionName());
        addIfPresent(values, detail.address());
        addIfPresent(values, detail.roadAddress());
        addIfPresent(values, candidate.address());
        addIfPresent(values, candidate.roadAddress());
        addIfPresent(values, candidate.fullAddress());
        return List.copyOf(values);
    }

    private void validateVisibleRestaurant(Restaurant restaurant) {
        if (Boolean.TRUE.equals(restaurant.getIsDeleted()) || Boolean.TRUE.equals(restaurant.getIsHidden())) {
            throw new BusinessException("Selected restaurant is not available.", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateRegionMatch(String expectedRegionName, String actualRegionName) {
        String expected = normalizeText(expectedRegionName);
        String actual = normalizeText(actualRegionName);
        if (expected == null || actual == null || !expected.equals(actual)) {
            throw new BusinessException("Restaurant region must match list region.", HttpStatus.BAD_REQUEST);
        }
    }

    private String resolveAddress(PcmapRestaurantDetail detail, PcmapRestaurantCandidate candidate) {
        return firstNonBlank(detail.roadAddress(), detail.address(), candidate.roadAddress(), candidate.address(), candidate.fullAddress());
    }

    private String resolveName(PcmapRestaurantDetail detail, PcmapRestaurantCandidate candidate) {
        return firstNonBlank(detail.name(), candidate.name());
    }

    private String joinSignals(String... values) {
        StringBuilder builder = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                String normalized = normalizeText(value);
                if (normalized != null) {
                    builder.append(' ').append(normalized);
                }
            }
        }
        return builder.toString();
    }

    private boolean hasAlphaOrHangul(String value) {
        return value.matches(".*[가-힣A-Za-z].*");
    }

    private boolean isDigitsOnly(String value) {
        String normalized = value.replaceAll("\\s+", "");
        return !normalized.isBlank() && normalized.matches("^\\d[\\d.-]*$");
    }

    private String normalizeMenuName(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        return normalizeText(normalized
                .replaceAll("\\[[^]]*]", " ")
                .replaceAll("\\([^)]*\\)", " ")
                .replaceAll("\\s+", " "));
    }

    private BigDecimal toMenuPrice(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        String digits = normalized.replaceAll("[^\\d.]", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            BigDecimal price = new BigDecimal(digits);
            if (price.signum() < 0 || price.compareTo(MAX_REASONABLE_MENU_PRICE) > 0) {
                return null;
            }
            return price;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
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

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void addIfPresent(LinkedHashSet<String> values, String value) {
        String normalized = normalizeText(value);
        if (normalized != null) {
            values.add(normalized);
        }
    }
}
