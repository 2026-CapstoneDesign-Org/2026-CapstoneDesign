package com.example.Capstone.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

@ExtendWith(MockitoExtension.class)
class ExternalFallbackRestaurantRegistrationServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RestaurantMenuItemRepository restaurantMenuItemRepository;

    @Mock
    private PcmapSearchClient pcmapSearchClient;

    @Mock
    private PcmapPlaceDetailClient pcmapPlaceDetailClient;

    @Mock
    private NaverLocalSearchClient naverLocalSearchClient;

    @InjectMocks
    private ExternalFallbackRestaurantRegistrationService service;

    @Test
    @DisplayName("verified external fallback restaurant is saved as visible calculation candidate")
    void registerVerifiedRestaurantCreatesRestaurantAndMenus() {
        PcmapRestaurantCandidate candidate = candidate("place-1", "서울시 강남구 테헤란로 1");
        PcmapRestaurantDetail detail = detail(
                "place-1",
                "외부국수",
                "음식점>한식",
                "서울시 강남구 테헤란로 1",
                "restaurant",
                List.of(new PcmapMenuItem(0, "잔치국수", "8,000원", "대표 메뉴", "Menu"))
        );

        when(pcmapSearchClient.searchRestaurants("강남 국수", 20)).thenReturn(List.of(candidate));
        when(pcmapPlaceDetailClient.fetchRestaurantDetail("place-1")).thenReturn(Optional.of(detail));
        when(restaurantRepository.findByPcmapPlaceId("place-1")).thenReturn(Optional.empty());
        when(restaurantRepository.findByNameAndAddress("외부국수", "서울시 강남구 테헤란로 1"))
                .thenReturn(Optional.empty());
        when(naverLocalSearchClient.findBestRestaurantMatch("외부국수", "서울시 강남구 테헤란로 1"))
                .thenReturn(Optional.of(new NaverLocalRestaurantCandidate(
                        "외부국수",
                        "음식점>한식",
                        "02-1234-5678",
                        "서울시 강남구 테헤란로 1",
                        "서울시 강남구 테헤란로 1",
                        "127.0",
                        "37.0"
                )));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> {
            Restaurant restaurant = invocation.getArgument(0);
            ReflectionTestUtils.setField(restaurant, "id", 100L);
            return restaurant;
        });

        Restaurant restaurant = service.registerVerifiedRestaurant(request("강남 국수", "place-1"), "서울시 강남구");

        ArgumentCaptor<Restaurant> restaurantCaptor = ArgumentCaptor.forClass(Restaurant.class);
        ArgumentCaptor<List<RestaurantMenuItem>> menuCaptor = ArgumentCaptor.forClass(List.class);
        verify(restaurantRepository).save(restaurantCaptor.capture());
        verify(restaurantMenuItemRepository).saveAll(menuCaptor.capture());

        Restaurant saved = restaurantCaptor.getValue();
        assertEquals("외부국수", saved.getName());
        assertEquals("서울시 강남구", saved.getRegionName());
        assertEquals("place-1", saved.getPcmapPlaceId());
        assertEquals("02-1234-5678", saved.getPhoneNumber());
        assertFalse(saved.getIsHidden());
        assertFalse(saved.getIsDeleted());
        assertEquals(100L, restaurant.getId());
        assertEquals("잔치국수", menuCaptor.getValue().get(0).getMenuName());
        assertEquals(new BigDecimal("8000"), menuCaptor.getValue().get(0).getPriceValue());
    }

    @Test
    @DisplayName("external fallback restaurant must match list region after detail validation")
    void registerVerifiedRestaurantRejectsDifferentRegion() {
        PcmapRestaurantCandidate candidate = candidate("place-1", "서울시 마포구 월드컵로 1");
        PcmapRestaurantDetail detail = detail(
                "place-1",
                "외부국수",
                "음식점>한식",
                "서울시 마포구 월드컵로 1",
                "restaurant",
                List.of(new PcmapMenuItem(0, "잔치국수", "8,000원", null, "Menu"))
        );

        when(pcmapSearchClient.searchRestaurants("마포 국수", 20)).thenReturn(List.of(candidate));
        when(pcmapPlaceDetailClient.fetchRestaurantDetail("place-1")).thenReturn(Optional.of(detail));

        assertThrows(BusinessException.class, () ->
                service.registerVerifiedRestaurant(request("마포 국수", "place-1"), "서울시 강남구"));
    }

    @Test
    @DisplayName("external fallback registration rejects non-food candidates")
    void registerVerifiedRestaurantRejectsNonFoodCandidate() {
        PcmapRestaurantCandidate candidate = candidate("place-1", "서울시 강남구 테헤란로 1", "스터디카페");
        PcmapRestaurantDetail detail = detail(
                "place-1",
                "외부스터디카페",
                "스터디카페",
                "서울시 강남구 테헤란로 1",
                null,
                List.of()
        );

        when(pcmapSearchClient.searchRestaurants("강남 카페", 20)).thenReturn(List.of(candidate));
        when(pcmapPlaceDetailClient.fetchRestaurantDetail("place-1")).thenReturn(Optional.of(detail));

        assertThrows(BusinessException.class, () ->
                service.registerVerifiedRestaurant(request("강남 카페", "place-1"), "서울시 강남구"));
    }

    private AddExternalRestaurantRequest request(String query, String placeId) {
        return new AddExternalRestaurantRequest(
                query,
                placeId,
                new BigDecimal("8.0"),
                new BigDecimal("7.0"),
                new BigDecimal("6.0")
        );
    }

    private PcmapRestaurantCandidate candidate(String placeId, String address) {
        return candidate(placeId, address, "음식점>한식");
    }

    private PcmapRestaurantCandidate candidate(String placeId, String address, String categoryName) {
        return new PcmapRestaurantCandidate(
                placeId,
                "외부국수",
                categoryName,
                address,
                address,
                address,
                "image",
                "127.0",
                "37.0"
        );
    }

    private PcmapRestaurantDetail detail(
            String placeId,
            String name,
            String categoryName,
            String address,
            String businessType,
            List<PcmapMenuItem> menus
    ) {
        return new PcmapRestaurantDetail(
                placeId,
                name,
                categoryName,
                address,
                address,
                "image",
                "127.0",
                "37.0",
                "02-1234-5678",
                businessType,
                List.of("주차"),
                "{\"source\":\"test\"}",
                menus
        );
    }
}
