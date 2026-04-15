package com.example.Capstone.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.Capstone.client.PcmapSearchClient;
import com.example.Capstone.client.PcmapSearchClient.PcmapRestaurantCandidate;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.RestaurantMenuItem;
import com.example.Capstone.domain.RestaurantTag;
import com.example.Capstone.domain.Tag;
import com.example.Capstone.domain.User;
import com.example.Capstone.dto.response.SearchResponse;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PcmapSearchClient pcmapSearchClient;

    @InjectMocks
    private SearchService searchService;

    @Test
    @DisplayName("@ prefix query is interpreted as user search")
    void searchTreatsSingleAtPrefixAsUserIntent() {
        User user = user(1L, "tester");
        when(userRepository.searchVisibleUsers(eq("tester"), any(Pageable.class)))
                .thenReturn(List.of(user));

        SearchResponse response = searchService.search("@tester");

        assertEquals("USER", response.primaryType());
        assertEquals("@tester", response.interpretation().rawQuery());
        assertEquals("tester", response.interpretation().normalizedQuery());
        assertTrue(response.interpretation().explicitUserQuery());
        assertFalse(response.interpretation().explicitRegionQuery());
        assertEquals("tester", response.interpretation().userKeyword());
        assertNull(response.interpretation().regionKeyword());
        assertEquals(1, response.userCount());
        assertTrue(response.restaurants().isEmpty());
        assertEquals(1, response.users().size());
        verify(pcmapSearchClient, never()).searchRestaurants(any(), any(Integer.class));
    }

    @Test
    @DisplayName("restaurant name query is interpreted as restaurant search")
    void searchTreatsRestaurantNameAsRestaurantIntent() {
        Restaurant restaurant = restaurant(2L, "온더보더", "서울 중구", "서울 중구 을지로", "양식", "파스타", "파스타", null);

        when(restaurantRepository.searchVisibleRestaurantsByRegionSignal(eq("온더보더"), any(Pageable.class)))
                .thenReturn(List.of());
        when(restaurantRepository.searchVisibleRestaurantsBySearchKeyword(eq("온더보더"), any(Pageable.class)))
                .thenReturn(List.of(restaurant));
        when(userRepository.searchVisibleUsers(eq("온더보더"), any(Pageable.class)))
                .thenReturn(List.of());
        when(pcmapSearchClient.searchRestaurants(eq("온더보더"), any(Integer.class)))
                .thenReturn(List.of());

        SearchResponse response = searchService.search("온더보더");

        assertEquals("RESTAURANT", response.primaryType());
        assertNull(response.interpretation().regionKeyword());
        assertEquals("온더보더", response.interpretation().restaurantKeyword());
        assertEquals(1, response.restaurantCount());
        assertEquals("온더보더", response.restaurants().get(0).restaurantName());
        assertFalse(response.interpretation().fallbackUsed());
    }

    @Test
    @DisplayName("region only query is interpreted as region search")
    void searchTreatsRegionOnlyAsRegionIntent() {
        Restaurant restaurant = restaurant(3L, "성수 감자탕", "서울 성수", "서울 성수동", "한식", "감자탕", "감자탕", "성수동");

        when(restaurantRepository.searchVisibleRestaurantsByRegionSignal(eq("성수"), any(Pageable.class)))
                .thenReturn(List.of(restaurant));

        SearchResponse response = searchService.search("성수");

        assertEquals("REGION", response.primaryType());
        assertEquals("성수", response.interpretation().regionKeyword());
        assertNull(response.interpretation().restaurantKeyword());
        assertTrue(response.interpretation().genericBrowseQuery());
        assertEquals(1, response.regionCount());
        assertEquals("서울 성수", response.regions().get(0).regionName());
        assertEquals("성수동", response.regions().get(0).displayName());
        verify(pcmapSearchClient, never()).searchRestaurants(any(), any(Integer.class));
    }

    @Test
    @DisplayName("generic region query does not use fallback")
    void searchTreatsRegionAndGenericKeywordAsRegion() {
        Restaurant restaurant = restaurant(4L, "성수 카레", "서울 성수", "서울 성수동", "일식", "카레", "카레", "성수동");

        when(restaurantRepository.searchVisibleRestaurantsByRegionSignal(eq("성수 맛집"), any(Pageable.class)))
                .thenReturn(List.of());
        when(restaurantRepository.searchVisibleRestaurantsByRegionSignal(eq("성수"), any(Pageable.class)))
                .thenReturn(List.of(restaurant));

        SearchResponse response = searchService.search("성수 맛집");

        assertEquals("REGION", response.primaryType());
        assertEquals("성수", response.interpretation().regionKeyword());
        assertNull(response.interpretation().restaurantKeyword());
        assertTrue(response.interpretation().genericBrowseQuery());
        assertFalse(response.interpretation().fallbackUsed());
        verify(pcmapSearchClient, never()).searchRestaurants(any(), any(Integer.class));
    }

    @Test
    @DisplayName("region plus menu query is filtered by the matched region")
    void searchTreatsRegionAndMenuAsRestaurantIntent() {
        Restaurant seongsuDonkkaseu = restaurant(11L, "성수 점심집", "서울 성수", "서울 성수동", "일식", "돈까스", "돈까스", "성수동");
        Restaurant gangnamDonkkaseu = restaurant(12L, "강남 점심집", "서울 강남", "서울 강남구", "일식", "돈까스", "돈까스", "역삼동");

        when(restaurantRepository.searchVisibleRestaurantsByRegionSignal(eq("성수 돈까스"), any(Pageable.class)))
                .thenReturn(List.of());
        when(restaurantRepository.searchVisibleRestaurantsByRegionSignal(eq("성수"), any(Pageable.class)))
                .thenReturn(List.of(seongsuDonkkaseu));
        when(restaurantRepository.searchVisibleRestaurantsBySearchKeyword(eq("돈까스"), any(Pageable.class)))
                .thenReturn(List.of(seongsuDonkkaseu, gangnamDonkkaseu));

        SearchResponse response = searchService.search("성수 돈까스");

        assertEquals("RESTAURANT", response.primaryType());
        assertEquals("성수", response.interpretation().regionKeyword());
        assertEquals("돈까스", response.interpretation().restaurantKeyword());
        assertEquals(1, response.restaurants().size());
        assertEquals("성수 점심집", response.restaurants().get(0).restaurantName());
        assertEquals("MENU", response.restaurants().get(0).matchedBy());
    }

    @Test
    @DisplayName("category only query is matched by category")
    void searchTreatsCategoryOnlyAsRestaurantIntent() {
        Restaurant categoryRestaurant = restaurant(21L, "연남 살롱", "서울 마포", "서울 마포구", "브런치", "토스트", "토스트", "연남동");

        when(restaurantRepository.searchVisibleRestaurantsByRegionSignal(eq("브런치"), any(Pageable.class)))
                .thenReturn(List.of());
        when(restaurantRepository.searchVisibleRestaurantsBySearchKeyword(eq("브런치"), any(Pageable.class)))
                .thenReturn(List.of(categoryRestaurant));
        when(userRepository.searchVisibleUsers(eq("브런치"), any(Pageable.class)))
                .thenReturn(List.of());

        SearchResponse response = searchService.search("브런치");

        assertEquals("RESTAURANT", response.primaryType());
        assertNull(response.interpretation().regionKeyword());
        assertEquals("브런치", response.interpretation().restaurantKeyword());
        assertEquals("CATEGORY", response.restaurants().get(0).matchedBy());
    }

    @Test
    @DisplayName("region plus tag query is matched by tag after region filtering")
    void searchTreatsRegionAndTagAsRestaurantIntent() {
        Restaurant target = restaurant(31L, "역북 주점", "용인 처인구", "용인시 처인구 역북동", "주점", "시그니처 세트", "하이볼", "역북동");
        Restaurant otherRegion = restaurant(32L, "수지 주점", "용인 수지구", "용인시 수지구 풍덕천동", "주점", "시그니처 세트", "하이볼", "풍덕천동");

        when(restaurantRepository.searchVisibleRestaurantsByRegionSignal(eq("역북 하이볼"), any(Pageable.class)))
                .thenReturn(List.of());
        when(restaurantRepository.searchVisibleRestaurantsByRegionSignal(eq("역북"), any(Pageable.class)))
                .thenReturn(List.of(target));
        when(restaurantRepository.searchVisibleRestaurantsBySearchKeyword(eq("하이볼"), any(Pageable.class)))
                .thenReturn(List.of(target, otherRegion));

        SearchResponse response = searchService.search("역북 하이볼");

        assertEquals("RESTAURANT", response.primaryType());
        assertEquals("역북", response.interpretation().regionKeyword());
        assertEquals("하이볼", response.interpretation().restaurantKeyword());
        assertEquals(1, response.restaurants().size());
        assertEquals("TAG", response.restaurants().get(0).matchedBy());
        assertEquals("역북 주점", response.restaurants().get(0).restaurantName());
    }

    @Test
    @DisplayName("town query keeps canonical region path and town display")
    void searchRegionUsesTownDisplayNameWhenTownMatches() {
        Restaurant restaurant = restaurant(41L, "역북 돈까스", "용인 처인구", "용인시 처인구 역북동", "일식", "돈까스", "돈까스", "역북동");

        when(restaurantRepository.searchVisibleRestaurantsByRegionSignal(eq("역북동"), any(Pageable.class)))
                .thenReturn(List.of(restaurant));

        SearchResponse response = searchService.search("역북동");

        assertEquals("REGION", response.primaryType());
        assertEquals(1, response.regions().size());
        assertEquals("용인 처인구", response.regions().get(0).regionName());
        assertEquals("역북동", response.regions().get(0).displayName());
        assertTrue(response.regions().get(0).rankingPath().contains("regionName="));
    }

    @Test
    @DisplayName("road address query is matched by address")
    void searchTreatsRoadAddressAsAddressIntent() {
        Restaurant restaurant = restaurant(42L, "Road Bistro", "region-a", "Lot 12-3", "western", "pasta", "pasta", "town-a");
        ReflectionTestUtils.setField(restaurant, "roadAddress", "Road Street 7");

        when(restaurantRepository.searchVisibleRestaurantsByRegionSignal(eq("Road Street"), any(Pageable.class)))
                .thenReturn(List.of());
        when(restaurantRepository.searchVisibleRestaurantsBySearchKeyword(eq("Road Street"), any(Pageable.class)))
                .thenReturn(List.of(restaurant));
        when(userRepository.searchVisibleUsers(eq("Road Street"), any(Pageable.class)))
                .thenReturn(List.of());

        SearchResponse response = searchService.search("Road Street");

        assertEquals("RESTAURANT", response.primaryType());
        assertEquals("ADDRESS", response.restaurants().get(0).matchedBy());
        assertEquals("Road Street 7", response.restaurants().get(0).address());
    }

    @Test
    @DisplayName("fallback appends external candidates when internal results are insufficient")
    void searchUsesFallbackWhenInternalRestaurantCandidatesAreInsufficient() {
        Restaurant internalRestaurant = restaurant(51L, "강남 곱창집", "서울 강남", "서울 강남구", "곱창", "곱창", "곱창", "역삼동");

        when(restaurantRepository.searchVisibleRestaurantsByRegionSignal(eq("곱창"), any(Pageable.class)))
                .thenReturn(List.of());
        when(restaurantRepository.searchVisibleRestaurantsBySearchKeyword(eq("곱창"), any(Pageable.class)))
                .thenReturn(List.of(internalRestaurant));
        when(userRepository.searchVisibleUsers(eq("곱창"), any(Pageable.class)))
                .thenReturn(List.of());
        when(pcmapSearchClient.searchRestaurants(eq("곱창"), any(Integer.class)))
                .thenReturn(List.of(new PcmapRestaurantCandidate(
                        "external-1",
                        "강남 곱창 외부",
                        "곱창",
                        "서울 강남구",
                        "서울 강남구",
                        "서울 강남구",
                        "external-image",
                        "127.0",
                        "37.0"
                )));

        SearchResponse response = searchService.search("곱창");

        assertEquals("RESTAURANT", response.primaryType());
        assertTrue(response.interpretation().fallbackUsed());
        assertEquals(2, response.restaurants().size());
        assertEquals("INTERNAL", response.restaurants().get(0).source());
        assertEquals("EXTERNAL_FALLBACK", response.restaurants().get(1).source());
        assertEquals(new BigDecimal("37.0"), response.restaurants().get(1).lat());
        assertEquals(new BigDecimal("127.0"), response.restaurants().get(1).lng());
    }

    private User user(Long id, String nickname) {
        User user = User.builder()
                .provider("KAKAO")
                .providerUserId("provider-" + id)
                .nickname(nickname)
                .profileImageUrl("profile")
                .role(User.Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Restaurant restaurant(
            Long id,
            String name,
            String regionName,
            String address,
            String categoryName,
            String menuName,
            String tagName,
            String townName
    ) {
        Restaurant restaurant = Restaurant.builder()
                .name(name)
                .address(address)
                .roadAddress(address)
                .categoryName(categoryName)
                .regionName(regionName)
                .regionCityName(regionName)
                .regionDistrictName(regionName)
                .regionCountyName(regionName)
                .regionTownName(townName)
                .regionFilterNames(List.of(regionName, address, townName == null ? regionName : townName))
                .lat(new BigDecimal("37.0"))
                .lng(new BigDecimal("127.0"))
                .imageUrl("image")
                .pcmapPlaceId("place-" + id)
                .build();

        ReflectionTestUtils.setField(restaurant, "id", id);
        ReflectionTestUtils.setField(restaurant, "menuItems", new ArrayList<>(List.of(
                RestaurantMenuItem.builder()
                        .restaurant(restaurant)
                        .displayOrder(1)
                        .menuName(menuName)
                        .normalizedMenuName(menuName)
                        .build()
        )));

        Tag tag = Tag.builder()
                .tagKey("menu:" + tagName)
                .tagName(tagName)
                .isActive(true)
                .build();
        ReflectionTestUtils.setField(tag, "id", id);

        ReflectionTestUtils.setField(restaurant, "restaurantTags", new ArrayList<>(List.of(
                RestaurantTag.builder()
                        .restaurant(restaurant)
                        .tag(tag)
                        .matchedMenuCount(1)
                        .isPrimary(true)
                        .build()
        )));
        return restaurant;
    }
}
