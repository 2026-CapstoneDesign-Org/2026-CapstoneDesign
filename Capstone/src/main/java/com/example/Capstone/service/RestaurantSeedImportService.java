package com.example.Capstone.service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.RestaurantMenuItem;
import com.example.Capstone.domain.RestaurantTag;
import com.example.Capstone.domain.Tag;
import com.example.Capstone.dto.request.ImportRestaurantSeedRequest;
import com.example.Capstone.dto.response.RestaurantSeedImportResponse;
import com.example.Capstone.repository.RestaurantMenuItemRepository;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.RestaurantTagRepository;
import com.example.Capstone.repository.TagRepository;
import com.example.Capstone.service.seed.RestaurantSeedFileLoader;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantSeedImportService {

    private static final BigDecimal MAX_REASONABLE_MENU_PRICE = new BigDecimal("10000000");

    private static final Path DEFAULT_RESTAURANTS_FILE_PATH = Path.of("seed-data", "restaurants-seed-preview.json");
    private static final Path DEFAULT_MENU_ITEMS_FILE_PATH = Path.of("seed-data", "restaurant-menu-items-seed-preview.json");
    private static final Path DEFAULT_TAGS_FILE_PATH = Path.of("seed-data", "tags-seed-preview.json");
    private static final Path DEFAULT_RESTAURANT_TAGS_FILE_PATH = Path.of("seed-data", "restaurant-tags-seed-preview.json");

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMenuItemRepository restaurantMenuItemRepository;
    private final TagRepository tagRepository;
    private final RestaurantTagRepository restaurantTagRepository;
    private final RestaurantSeedFileLoader restaurantSeedFileLoader;

    @Transactional
    public RestaurantSeedImportResponse importSeed(ImportRestaurantSeedRequest request) {
        Path restaurantsPath = restaurantSeedFileLoader.resolvePath(
                request == null ? null : request.restaurantsFilePath(),
                DEFAULT_RESTAURANTS_FILE_PATH
        );
        Path menuItemsPath = restaurantSeedFileLoader.resolvePath(
                request == null ? null : request.menuItemsFilePath(),
                DEFAULT_MENU_ITEMS_FILE_PATH
        );
        Path tagsPath = restaurantSeedFileLoader.resolvePath(
                request == null ? null : request.tagsFilePath(),
                DEFAULT_TAGS_FILE_PATH
        );
        Path restaurantTagsPath = restaurantSeedFileLoader.resolvePath(
                request == null ? null : request.restaurantTagsFilePath(),
                DEFAULT_RESTAURANT_TAGS_FILE_PATH
        );

        List<RestaurantSeedRow> restaurantRows = restaurantSeedFileLoader.readRows(
                restaurantsPath,
                new TypeReference<List<RestaurantSeedRow>>() {}
        );
        List<RestaurantMenuItemSeedRow> menuItemRows = restaurantSeedFileLoader.readRows(
                menuItemsPath,
                new TypeReference<List<RestaurantMenuItemSeedRow>>() {}
        );
        List<TagSeedRow> tagRows = restaurantSeedFileLoader.readRows(
                tagsPath,
                new TypeReference<List<TagSeedRow>>() {}
        );
        List<RestaurantTagSeedRow> restaurantTagRows = restaurantSeedFileLoader.readRows(
                restaurantTagsPath,
                new TypeReference<List<RestaurantTagSeedRow>>() {}
        );

        Map<Long, List<RestaurantMenuItemSeedRow>> menuItemsBySeedIndex = menuItemRows.stream()
                .collect(Collectors.groupingBy(
                        RestaurantMenuItemSeedRow::restaurantSeedIndex,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, List<RestaurantTagSeedRow>> restaurantTagsBySeedIndex = restaurantTagRows.stream()
                .collect(Collectors.groupingBy(
                        RestaurantTagSeedRow::restaurantSeedIndex,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<Long, Restaurant> restaurantsBySeedIndex = new LinkedHashMap<>();
        int createdRestaurantCount = 0;
        int updatedRestaurantCount = 0;

        for (RestaurantSeedRow row : restaurantRows) {
            Optional<Restaurant> existingRestaurant = findRestaurant(row);
            Restaurant restaurant;

            if (existingRestaurant.isPresent()) {
                restaurant = existingRestaurant.get();
                updateRestaurant(restaurant, row);
                updatedRestaurantCount += 1;
            } else {
                restaurant = restaurantRepository.save(createRestaurant(row));
                createdRestaurantCount += 1;
            }

            restaurantsBySeedIndex.put(row.seedIndex(), restaurant);
        }

        int createdTagCount = 0;
        int updatedTagCount = 0;
        Map<String, Tag> tagsByKey = new LinkedHashMap<>();

        for (TagSeedRow row : tagRows) {
            String normalizedParentTagKey = normalizeParentTagKey(row.parentTagKey());
            Optional<Tag> existingTag = tagRepository.findByTagKey(row.tagKey());
            if (existingTag.isPresent()) {
                Tag tag = existingTag.get();
                tag.updateInfo(row.tagName(), normalizedParentTagKey, row.isActive());
                tagsByKey.put(tag.getTagKey(), tag);
                updatedTagCount += 1;
            } else {
                Tag tag = tagRepository.save(Tag.builder()
                        .tagKey(row.tagKey())
                        .tagName(row.tagName())
                        .parentTagKey(normalizedParentTagKey)
                        .isActive(row.isActive())
                        .build());
                tagsByKey.put(tag.getTagKey(), tag);
                createdTagCount += 1;
            }
        }

        synchronizeTags(tagsByKey.keySet());

        int replacedMenuItemCount = 0;
        int replacedRestaurantTagCount = 0;

        for (RestaurantSeedRow row : restaurantRows) {
            Restaurant restaurant = restaurantsBySeedIndex.get(row.seedIndex());
            if (restaurant == null) {
                throw new EntityNotFoundException("seed index???대떦?섎뒗 ?앸떦??李얠쓣 ???놁뒿?덈떎: " + row.seedIndex());
            }

            replacedMenuItemCount += replaceMenuItems(
                    restaurant,
                    menuItemsBySeedIndex.getOrDefault(row.seedIndex(), List.of())
            );
            replacedRestaurantTagCount += replaceRestaurantTags(
                    restaurant,
                    restaurantTagsBySeedIndex.getOrDefault(row.seedIndex(), List.of()),
                    tagsByKey
            );
        }

        int menuMappedRestaurantCount = menuItemsBySeedIndex.size();

        return new RestaurantSeedImportResponse(
                restaurantsPath.toString(),
                menuItemsPath.toString(),
                tagsPath.toString(),
                restaurantTagsPath.toString(),
                restaurantRows.size(),
                menuItemRows.size(),
                tagRows.size(),
                restaurantTagRows.size(),
                menuMappedRestaurantCount,
                createdRestaurantCount,
                updatedRestaurantCount,
                createdTagCount,
                updatedTagCount,
                replacedMenuItemCount,
                replacedRestaurantTagCount
        );
    }

    private Restaurant createRestaurant(RestaurantSeedRow row) {
        return Restaurant.builder()
                .name(row.name())
                .address(row.address())
                .roadAddress(normalizeText(row.roadAddress()))
                .categoryName(normalizeCategoryName(row.categoryName()))
                .regionName(row.regionName())
                .regionCityName(normalizeText(row.regionCityName()))
                .regionDistrictName(normalizeText(row.regionDistrictName()))
                .regionCountyName(normalizeText(row.regionCountyName()))
                .regionTownName(normalizeText(row.regionTownName()))
                .regionFilterNames(normalizeRegionFilterNames(row))
                .lat(row.lat())
                .lng(row.lng())
                .imageUrl(row.imageUrl())
                .pcmapPlaceId(row.pcmapPlaceId())
                .menuUpdatedAt(resolveMenuUpdatedAt(row.menuUpdatedAt()))
                .build();
    }

    private void updateRestaurant(Restaurant restaurant, RestaurantSeedRow row) {
        restaurant.updateInfo(
                row.name(),
                row.address(),
                row.regionName(),
                row.lat(),
                row.lng(),
                row.imageUrl()
        );
        restaurant.updateSeedAddress(
                normalizeText(row.address()),
                normalizeText(row.roadAddress())
        );
        restaurant.updateCategoryName(normalizeCategoryName(row.categoryName()));
        restaurant.updateSeedRegionInfo(
                row.regionName(),
                normalizeText(row.regionCityName()),
                normalizeText(row.regionDistrictName()),
                normalizeText(row.regionCountyName()),
                normalizeText(row.regionTownName()),
                normalizeRegionFilterNames(row)
        );
        restaurant.updateSeedMetadata(
                row.pcmapPlaceId(),
                resolveMenuUpdatedAt(row.menuUpdatedAt())
        );
    }

    private Optional<Restaurant> findRestaurant(RestaurantSeedRow row) {
        if (row.pcmapPlaceId() != null && !row.pcmapPlaceId().isBlank()) {
            Optional<Restaurant> byPcmapPlaceId = restaurantRepository.findByPcmapPlaceId(row.pcmapPlaceId());
            if (byPcmapPlaceId.isPresent()) {
                return byPcmapPlaceId;
            }
        }

        return restaurantRepository.findByNameAndAddress(row.name(), row.address());
    }

    private int replaceMenuItems(Restaurant restaurant, List<RestaurantMenuItemSeedRow> rows) {
        restaurantMenuItemRepository.deleteAllByRestaurantId(restaurant.getId());
        restaurantMenuItemRepository.flush();

        List<RestaurantMenuItem> menuItems = rows.stream()
                .sorted((left, right) -> Integer.compare(
                        left.displayOrder() == null ? 0 : left.displayOrder(),
                        right.displayOrder() == null ? 0 : right.displayOrder()
                ))
                .map(row -> RestaurantMenuItem.builder()
                        .restaurant(restaurant)
                        .displayOrder(row.displayOrder())
                        .menuName(row.menuName())
                        .normalizedMenuName(row.normalizedMenuName())
                        .menuTagKey(row.menuTagKey())
                        .priceText(row.priceText())
                        .priceValue(normalizePriceValue(row.priceValue()))
                        .description(row.description())
                        .build())
                .toList();

        restaurantMenuItemRepository.saveAll(menuItems);
        return menuItems.size();
    }

    private int replaceRestaurantTags(
            Restaurant restaurant,
            List<RestaurantTagSeedRow> rows,
            Map<String, Tag> tagsByKey
    ) {
        restaurantTagRepository.deleteAllByRestaurantId(restaurant.getId());
        restaurantTagRepository.flush();

        List<RestaurantTag> restaurantTags = new ArrayList<>();

        for (RestaurantTagSeedRow row : rows) {
            Tag tag = tagsByKey.get(row.tagKey());
            if (tag == null) {
                tag = tagRepository.findByTagKey(row.tagKey())
                        .orElseThrow(() -> new IllegalStateException("?쒓렇瑜?李얠쓣 ???놁뒿?덈떎: " + row.tagKey()));
                tagsByKey.put(tag.getTagKey(), tag);
            }

            restaurantTags.add(RestaurantTag.builder()
                    .restaurant(restaurant)
                    .tag(tag)
                    .matchedMenuCount(row.matchedMenuCount() == null ? 0 : row.matchedMenuCount())
                    .isPrimary(row.isPrimary())
                    .build());
        }

        restaurantTagRepository.saveAll(restaurantTags);
        return restaurantTags.size();
    }

    private void synchronizeTags(Set<String> currentTagKeys) {
        List<Tag> allTags = tagRepository.findAll();
        List<Tag> tagsToDelete = new ArrayList<>();

        for (Tag tag : allTags) {
            if (currentTagKeys.contains(tag.getTagKey())) {
                continue;
            }

            if (restaurantTagRepository.existsByTagId(tag.getId())) {
                tag.updateInfo(
                        tag.getTagName(),
                        normalizeParentTagKey(tag.getParentTagKey()),
                        false
                );
                continue;
            }

            tagsToDelete.add(tag);
        }

        if (!tagsToDelete.isEmpty()) {
            tagRepository.deleteAll(tagsToDelete);
        }
    }

    private String normalizeParentTagKey(String parentTagKey) {
        if (parentTagKey == null || parentTagKey.isBlank()) {
            return null;
        }

        if (parentTagKey.startsWith("menu:") || parentTagKey.startsWith("category:")) {
            return parentTagKey;
        }

        return null;
    }

    private BigDecimal normalizePriceValue(BigDecimal priceValue) {
        if (priceValue == null) {
            return null;
        }
        if (priceValue.signum() < 0) {
            return null;
        }
        if (priceValue.compareTo(MAX_REASONABLE_MENU_PRICE) > 0) {
            return null;
        }
        return priceValue;
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeCategoryName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private List<String> normalizeRegionFilterNames(RestaurantSeedRow row) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (row.regionFilterNames() != null) {
            row.regionFilterNames().stream()
                    .map(this::normalizeText)
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(values::add);
        }

        if (values.isEmpty()) {
            addIfPresent(values, row.regionCityName());
            addIfPresent(values, row.regionDistrictName());
            addIfPresent(values, row.regionCountyName());
            addIfPresent(values, row.regionTownName());
            addIfPresent(values, row.regionName());
        }

        return new ArrayList<>(values);
    }

    private void addIfPresent(Set<String> values, String value) {
        String normalized = normalizeText(value);
        if (normalized != null) {
            values.add(normalized);
        }
    }

    private LocalDateTime resolveMenuUpdatedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();
        if ("NOW()".equalsIgnoreCase(normalized) || "CURRENT_TIMESTAMP".equalsIgnoreCase(normalized)) {
            return LocalDateTime.now();
        }

        try {
            return OffsetDateTime.parse(normalized).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(normalized);
            } catch (DateTimeParseException secondIgnored) {
                return null;
            }
        }
    }

    public record RestaurantSeedRow(
            @JsonProperty("seed_index") long seedIndex,
            @JsonProperty("address") String address,
            @JsonProperty("road_address") String roadAddress,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("lat") BigDecimal lat,
            @JsonProperty("lng") BigDecimal lng,
            @JsonProperty("name") String name,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("region_name") String regionName,
            @JsonProperty("region_city_name") String regionCityName,
            @JsonProperty("region_district_name") String regionDistrictName,
            @JsonProperty("region_county_name") String regionCountyName,
            @JsonProperty("region_town_name") String regionTownName,
            @JsonProperty("region_filter_names") List<String> regionFilterNames,
            @JsonProperty("pcmap_place_id") String pcmapPlaceId,
            @JsonProperty("menu_updated_at") String menuUpdatedAt
    ) {
    }

    public record RestaurantMenuItemSeedRow(
            @JsonProperty("restaurant_seed_index") long restaurantSeedIndex,
            @JsonProperty("display_order") Integer displayOrder,
            @JsonProperty("menu_name") String menuName,
            @JsonProperty("normalized_menu_name") String normalizedMenuName,
            @JsonProperty("menu_tag_key") String menuTagKey,
            @JsonProperty("price_text") String priceText,
            @JsonProperty("price_value") BigDecimal priceValue,
            @JsonProperty("description") String description
    ) {
    }

    public record TagSeedRow(
            @JsonProperty("seed_index") long seedIndex,
            @JsonProperty("tag_key") String tagKey,
            @JsonProperty("tag_name") String tagName,
            @JsonProperty("parent_tag_key") String parentTagKey,
            @JsonProperty("is_active") Boolean isActive
    ) {
    }

    public record RestaurantTagSeedRow(
            @JsonProperty("restaurant_seed_index") long restaurantSeedIndex,
            @JsonProperty("tag_key") String tagKey,
            @JsonProperty("matched_menu_count") Integer matchedMenuCount,
            @JsonProperty("is_primary") Boolean isPrimary
    ) {
    }
}
