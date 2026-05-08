package com.example.Capstone.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.Capstone.dto.request.ImportRestaurantSeedRequest;
import com.example.Capstone.repository.RestaurantPhotoRepository;
import com.example.Capstone.repository.RestaurantRepository;

@SpringBootTest
@ActiveProfiles({ "db", "key" })
class RestaurantSeedImportServiceTest {

    private static final String TEST_PLACE_ID = "TEST-DETAIL-PHOTO-1";

    @Autowired
    private RestaurantSeedImportService restaurantSeedImportService;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantPhotoRepository restaurantPhotoRepository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void cleanBeforeEach() {
        cleanupTestData();
    }

    @AfterEach
    void cleanAfterEach() {
        cleanupTestData();
    }

    @Test
    @DisplayName("restaurant seed import stores conveniences and up to 10 restaurant photos")
    void importsConveniencesAndRestaurantPhotos() throws Exception {
        Path restaurantsPath = tempDir.resolve("restaurants-seed-preview.json");
        Path menuItemsPath = tempDir.resolve("restaurant-menu-items-seed-preview.json");
        Path tagsPath = tempDir.resolve("tags-seed-preview.json");
        Path restaurantTagsPath = tempDir.resolve("restaurant-tags-seed-preview.json");

        Files.writeString(restaurantsPath, """
                [
                  {
                    "seed_index": 1,
                    "address": "Test lot address",
                    "road_address": "Test road address",
                    "image_url": "https://example.com/main.jpg",
                    "lat": 37.2320262,
                    "lng": 127.1868096,
                    "name": "TEST_DETAIL_PHOTO_RESTAURANT",
                    "category_name": "Korean",
                    "primary_category_name": "Korean",
                    "region_name": "Test Region",
                    "region_city_name": "Test City",
                    "region_district_name": "Test District",
                    "region_filter_names": ["Test Region"],
                    "conveniences": ["\\uC8FC\\uCC28", "\\uD3EC\\uC7A5"],
                    "photo_urls": [
                      "https://example.com/main.jpg",
                      "https://example.com/photo-1.jpg",
                      "https://example.com/photo-2.jpg",
                      "https://example.com/photo-3.jpg",
                      "https://example.com/photo-4.jpg",
                      "https://example.com/photo-5.jpg",
                      "https://example.com/photo-6.jpg",
                      "https://example.com/photo-7.jpg",
                      "https://example.com/photo-8.jpg",
                      "https://example.com/photo-9.jpg",
                      "https://example.com/photo-10.jpg",
                      "https://example.com/photo-11.jpg"
                    ],
                    "phone_number": "031-123-4567",
                    "business_hours_raw": "{\\"source\\":\\"pcmap_business_hours\\",\\"days\\":[]}",
                    "pcmap_place_id": "TEST-DETAIL-PHOTO-1",
                    "menu_updated_at": "2026-05-08T00:00:00+09:00"
                  }
                ]
                """, StandardCharsets.UTF_8);
        Files.writeString(menuItemsPath, """
                [
                  {
                    "restaurant_seed_index": 1,
                    "display_order": 0,
                    "menu_name": "Test Menu",
                    "normalized_menu_name": "Test Menu",
                    "menu_tag_key": null,
                    "price_text": "9,000",
                    "price_value": 9000,
                    "description": "Test menu description"
                  }
                ]
                """, StandardCharsets.UTF_8);
        Files.writeString(tagsPath, "[]", StandardCharsets.UTF_8);
        Files.writeString(restaurantTagsPath, "[]", StandardCharsets.UTF_8);

        var importResponse = restaurantSeedImportService.importSeed(new ImportRestaurantSeedRequest(
                restaurantsPath.toString(),
                menuItemsPath.toString(),
                tagsPath.toString(),
                restaurantTagsPath.toString(),
                false
        ));
        var restaurant = restaurantRepository.findByPcmapPlaceId(TEST_PLACE_ID).orElseThrow();
        var detailResponse = restaurantService.getRestaurant(restaurant.getId());

        assertEquals(10, importResponse.replacedRestaurantPhotoCount());
        assertEquals(10, restaurantPhotoRepository
                .findTop10ByRestaurantIdOrderByDisplayOrderAscIdAsc(restaurant.getId())
                .size());
        assertEquals(10, detailResponse.photos().size());
        assertEquals("https://example.com/main.jpg", detailResponse.photos().get(0).imageUrl());
        assertEquals("https://example.com/photo-9.jpg", detailResponse.photos().get(9).imageUrl());
        assertEquals("031-123-4567", detailResponse.phoneNumber());
        assertEquals("Test Menu", detailResponse.menus().get(0).menuName());
        assertTrue(detailResponse.conveniences().contains("주차"));
        assertTrue(detailResponse.parkingAvailable());
    }

    private void cleanupTestData() {
        restaurantRepository.findByPcmapPlaceId(TEST_PLACE_ID)
                .ifPresent(restaurant -> restaurantRepository.delete(restaurant));
    }
}
