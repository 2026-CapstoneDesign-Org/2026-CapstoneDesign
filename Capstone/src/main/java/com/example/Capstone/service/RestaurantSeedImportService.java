package com.example.Capstone.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.RestaurantCategory;
import com.example.Capstone.domain.RestaurantMenuItem;
import com.example.Capstone.domain.RestaurantTag;
import com.example.Capstone.domain.Tag;
import com.example.Capstone.dto.request.ImportRestaurantSeedRequest;
import com.example.Capstone.dto.response.RestaurantSeedImportResponse;
import com.example.Capstone.repository.RestaurantCategoryRepository;
import com.example.Capstone.repository.RestaurantMenuItemRepository;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.RestaurantTagRepository;
import com.example.Capstone.repository.TagRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantSeedImportService {

    private static final BigDecimal MAX_REASONABLE_MENU_PRICE = new BigDecimal("10000000");

    private static final Path DEFAULT_RESTAURANTS_FILE_PATH = Path.of("seed-data", "restaurants-seed-preview.json");
    private static final Path DEFAULT_CATEGORIES_FILE_PATH = Path.of("seed-data", "restaurant-categories-seed-preview.json");
    private static final Path DEFAULT_MENU_ITEMS_FILE_PATH = Path.of("seed-data", "restaurant-menu-items-seed-preview.json");
    private static final Path DEFAULT_TAGS_FILE_PATH = Path.of("seed-data", "tags-seed-preview.json");
    private static final Path DEFAULT_RESTAURANT_TAGS_FILE_PATH = Path.of("seed-data", "restaurant-tags-seed-preview.json");

    private final RestaurantRepository restaurantRepository;
    private final RestaurantCategoryRepository restaurantCategoryRepository;
    private final RestaurantMenuItemRepository restaurantMenuItemRepository;
    private final TagRepository tagRepository;
    private final RestaurantTagRepository restaurantTagRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public RestaurantSeedImportResponse importSeed(ImportRestaurantSeedRequest request) {
        Path restaurantsPath = resolvePath(
                request == null ? null : request.restaurantsFilePath(),
                DEFAULT_RESTAURANTS_FILE_PATH
        );
        Path categoriesPath = resolvePath(
                request == null ? null : request.categoriesFilePath(),
                DEFAULT_CATEGORIES_FILE_PATH
        );
        Path menuItemsPath = resolvePath(
                request == null ? null : request.menuItemsFilePath(),
                DEFAULT_MENU_ITEMS_FILE_PATH
        );
        Path tagsPath = resolvePath(
                request == null ? null : request.tagsFilePath(),
                DEFAULT_TAGS_FILE_PATH
        );
        Path restaurantTagsPath = resolvePath(
                request == null ? null : request.restaurantTagsFilePath(),
                DEFAULT_RESTAURANT_TAGS_FILE_PATH
        );

        List<RestaurantSeedRow> restaurantRows = readSeedRows(
                restaurantsPath,
                new TypeReference<List<RestaurantSeedRow>>() {}
        );
        List<RestaurantCategorySeedRow> categoryRows = readSeedRows(
                categoriesPath,
                new TypeReference<List<RestaurantCategorySeedRow>>() {}
        );
        List<RestaurantMenuItemSeedRow> menuItemRows = readSeedRows(
                menuItemsPath,
                new TypeReference<List<RestaurantMenuItemSeedRow>>() {}
        );
        List<TagSeedRow> tagRows = readSeedRows(
                tagsPath,
                new TypeReference<List<TagSeedRow>>() {}
        );
        List<RestaurantTagSeedRow> restaurantTagRows = readSeedRows(
                restaurantTagsPath,
                new TypeReference<List<RestaurantTagSeedRow>>() {}
        );

        Map<Long, List<RestaurantCategorySeedRow>> categoriesBySeedIndex = categoryRows.stream()
                .collect(Collectors.groupingBy(
                        RestaurantCategorySeedRow::restaurantSeedIndex,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
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
                updatedRestaurantCount++;
            } else {
                restaurant = restaurantRepository.save(createRestaurant(row));
                createdRestaurantCount++;
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
                tag.updateInfo(row.tagName(), row.tagType(), normalizedParentTagKey, row.isActive());
                tagsByKey.put(tag.getTagKey(), tag);
                updatedTagCount++;
            } else {
                Tag tag = tagRepository.save(Tag.builder()
                        .tagKey(row.tagKey())
                        .tagName(row.tagName())
                        .tagType(row.tagType())
                        .parentTagKey(normalizedParentTagKey)
                        .isActive(row.isActive())
                        .build());
                tagsByKey.put(tag.getTagKey(), tag);
                createdTagCount++;
            }
        }

        synchronizeTags(tagsByKey.keySet());

        int replacedCategoryCount = 0;
        int replacedMenuItemCount = 0;
        int replacedRestaurantTagCount = 0;

        for (RestaurantSeedRow row : restaurantRows) {
            Restaurant restaurant = restaurantsBySeedIndex.get(row.seedIndex());
            if (restaurant == null) {
                throw new EntityNotFoundException("seed index에 해당하는 식당을 찾을 수 없습니다: " + row.seedIndex());
            }

            replacedCategoryCount += replaceCategories(
                    restaurant,
                    categoriesBySeedIndex.getOrDefault(row.seedIndex(), List.of())
            );

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

        int menuMappedRestaurantCount = (int) restaurantRows.stream()
                .filter(row -> row.menuJson() != null && !row.menuJson().isNull())
                .count();

        return new RestaurantSeedImportResponse(
                restaurantsPath.toString(),
                categoriesPath.toString(),
                menuItemsPath.toString(),
                tagsPath.toString(),
                restaurantTagsPath.toString(),
                restaurantRows.size(),
                categoryRows.size(),
                menuItemRows.size(),
                tagRows.size(),
                restaurantTagRows.size(),
                menuMappedRestaurantCount,
                createdRestaurantCount,
                updatedRestaurantCount,
                createdTagCount,
                updatedTagCount,
                replacedCategoryCount,
                replacedMenuItemCount,
                replacedRestaurantTagCount
        );
    }

    private Restaurant createRestaurant(RestaurantSeedRow row) {
        return Restaurant.builder()
                .name(row.name())
                .address(row.address())
                .regionName(row.regionName())
                .regionCityName(normalizeRegionCityName(row))
                .regionDistrictName(normalizeRegionDistrictName(row))
                .regionCountyName(normalizeRegionCountyName(row))
                .regionFilterNames(normalizeRegionFilterNames(row))
                .lat(row.lat())
                .lng(row.lng())
                .imageUrl(row.imageUrl())
                .pcmapPlaceId(row.pcmapPlaceId())
                .menuJson(row.menuJson())
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
        restaurant.updateSeedRegionInfo(
                row.regionName(),
                normalizeRegionCityName(row),
                normalizeRegionDistrictName(row),
                normalizeRegionCountyName(row),
                normalizeRegionFilterNames(row)
        );
        restaurant.updateSeedMetadata(
                row.pcmapPlaceId(),
                row.menuJson(),
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

    private int replaceCategories(Restaurant restaurant, List<RestaurantCategorySeedRow> rows) {
        restaurantCategoryRepository.deleteAllByRestaurantId(restaurant.getId());
        restaurantCategoryRepository.flush();

        List<RestaurantCategory> categories = rows.stream()
                .map(RestaurantCategorySeedRow::categoryName)
                .filter(categoryName -> categoryName != null && !categoryName.isBlank())
                .distinct()
                .map(categoryName -> RestaurantCategory.builder()
                        .restaurant(restaurant)
                        .categoryName(categoryName)
                        .build())
                .toList();

        restaurantCategoryRepository.saveAll(categories);
        return categories.size();
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
                        .sourceMenuId(row.sourceMenuId())
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
                        .orElseThrow(() -> new IllegalStateException("태그를 찾을 수 없습니다: " + row.tagKey()));
                tagsByKey.put(tag.getTagKey(), tag);
            }

            restaurantTags.add(RestaurantTag.builder()
                    .restaurant(restaurant)
                    .tag(tag)
                    .sourceType(row.sourceType())
                    .sourceText(row.sourceText())
                    .weight(row.weight() == null ? BigDecimal.ZERO : row.weight())
                    .confidence(row.confidence() == null ? BigDecimal.ZERO : row.confidence())
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
                        tag.getTagType(),
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

    private String normalizeRegionCityName(RestaurantSeedRow row) {
        if (row.regionCountyName() != null && !row.regionCountyName().isBlank()) {
            return null;
        }

        return row.regionCityName();
    }

    private String normalizeRegionDistrictName(RestaurantSeedRow row) {
        if (row.regionCountyName() != null && !row.regionCountyName().isBlank()) {
            return null;
        }

        return row.regionDistrictName();
    }

    private String normalizeRegionCountyName(RestaurantSeedRow row) {
        return (row.regionCountyName() == null || row.regionCountyName().isBlank())
                ? null
                : row.regionCountyName();
    }

    private List<String> normalizeRegionFilterNames(RestaurantSeedRow row) {
        if (row.regionCountyName() != null && !row.regionCountyName().isBlank()) {
            return List.of(row.regionCountyName());
        }

        return (row.regionFilterNames() == null || row.regionFilterNames().isEmpty())
                ? List.of()
                : row.regionFilterNames().stream()
                        .filter(value -> value != null && !value.isBlank())
                        .distinct()
                        .toList();
    }

    private Path resolvePath(String filePath, Path defaultPath) {
        if (filePath == null || filePath.isBlank()) {
            return defaultPath;
        }

        return Path.of(filePath);
    }

    private <T> List<T> readSeedRows(Path path, TypeReference<List<T>> typeReference) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("seed 파일을 찾을 수 없습니다: " + path.toAbsolutePath());
        }

        try {
            return objectMapper.readValue(path.toFile(), typeReference);
        } catch (IOException exception) {
            throw new IllegalStateException("seed 파일을 읽을 수 없습니다: " + path.toAbsolutePath(), exception);
        }
    }

    private LocalDateTime resolveMenuUpdatedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException secondIgnored) {
                return null;
            }
        }
    }

    public record RestaurantSeedRow(
            @JsonProperty("seed_index") long seedIndex,
            @JsonProperty("address") String address,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("lat") BigDecimal lat,
            @JsonProperty("lng") BigDecimal lng,
            @JsonProperty("name") String name,
            @JsonProperty("region_name") String regionName,
            @JsonProperty("region_city_name") String regionCityName,
            @JsonProperty("region_district_name") String regionDistrictName,
            @JsonProperty("region_county_name") String regionCountyName,
            @JsonProperty("region_filter_names") List<String> regionFilterNames,
            @JsonProperty("pcmap_place_id") String pcmapPlaceId,
            @JsonProperty("menu_json") JsonNode menuJson,
            @JsonProperty("menu_updated_at") String menuUpdatedAt
    ) {}

    public record RestaurantCategorySeedRow(
            @JsonProperty("restaurant_seed_index") long restaurantSeedIndex,
            @JsonProperty("category_name") String categoryName
    ) {}

    public record RestaurantMenuItemSeedRow(
            @JsonProperty("restaurant_seed_index") long restaurantSeedIndex,
            @JsonProperty("display_order") Integer displayOrder,
            @JsonProperty("source_menu_id") String sourceMenuId,
            @JsonProperty("menu_name") String menuName,
            @JsonProperty("normalized_menu_name") String normalizedMenuName,
            @JsonProperty("menu_tag_key") String menuTagKey,
            @JsonProperty("price_text") String priceText,
            @JsonProperty("price_value") BigDecimal priceValue,
            @JsonProperty("description") String description
    ) {}

    public record TagSeedRow(
            @JsonProperty("seed_index") long seedIndex,
            @JsonProperty("tag_key") String tagKey,
            @JsonProperty("tag_name") String tagName,
            @JsonProperty("tag_type") String tagType,
            @JsonProperty("parent_tag_key") String parentTagKey,
            @JsonProperty("is_active") Boolean isActive
    ) {}

    public record RestaurantTagSeedRow(
            @JsonProperty("restaurant_seed_index") long restaurantSeedIndex,
            @JsonProperty("tag_key") String tagKey,
            @JsonProperty("source_type") String sourceType,
            @JsonProperty("source_text") String sourceText,
            @JsonProperty("weight") BigDecimal weight,
            @JsonProperty("confidence") BigDecimal confidence,
            @JsonProperty("matched_menu_count") Integer matchedMenuCount,
            @JsonProperty("is_primary") Boolean isPrimary
    ) {}
}
