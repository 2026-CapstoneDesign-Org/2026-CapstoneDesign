package com.example.Capstone.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.example.Capstone.client.PcmapSearchClient;
import com.example.Capstone.client.PcmapSearchClient.PcmapRestaurantCandidate;
import com.example.Capstone.domain.ListRestaurant;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.User;
import com.example.Capstone.domain.UserList;
import com.example.Capstone.dto.request.AddExternalRestaurantRequest;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.ListRestaurantRepository;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.UserListRepository;
import com.example.Capstone.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserListServiceExternalFallbackTest {

    @Mock
    private UserListRepository userListRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private ListRestaurantRepository listRestaurantRepository;

    @Mock
    private PcmapSearchClient pcmapSearchClient;

    @Mock
    private NaverLocalSearchClient naverLocalSearchClient;

    @InjectMocks
    private UserListService userListService;

    @Test
    @DisplayName("external fallback result is re-checked and added to a list")
    void addExternalFallbackRestaurantCreatesRestaurantAndListItem() {
        Long userId = 1L;
        Long listId = 10L;
        UserList userList = ownedList(userId, listId, "Seoul Gangnam");
        PcmapRestaurantCandidate candidate = candidate("place-1", "Seoul Gangnam Road 1");

        when(userListRepository.findByIdAndIsDeletedFalse(listId)).thenReturn(Optional.of(userList));
        when(pcmapSearchClient.searchRestaurants("Gangnam noodle", 20)).thenReturn(List.of(candidate));
        when(restaurantRepository.findByPcmapPlaceId("place-1")).thenReturn(Optional.empty());
        when(naverLocalSearchClient.findBestRestaurantMatch("External Noodle", "Seoul Gangnam Road 1"))
                .thenReturn(Optional.of(new NaverLocalRestaurantCandidate(
                        "External Noodle",
                        "\uD30C\uC2A4\uD0C0",
                        "02-1234-5678",
                        "Seoul Gangnam Road 1",
                        "Seoul Gangnam Road 1",
                        "127.0",
                        "37.0"
                )));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> {
            Restaurant restaurant = invocation.getArgument(0);
            ReflectionTestUtils.setField(restaurant, "id", 100L);
            return restaurant;
        });
        when(listRestaurantRepository.findByUserListIdAndRestaurantId(listId, 100L))
                .thenReturn(Optional.empty());

        userListService.addExternalFallbackRestaurant(userId, listId, request("Gangnam noodle", "place-1"));

        ArgumentCaptor<Restaurant> restaurantCaptor = ArgumentCaptor.forClass(Restaurant.class);
        verify(naverLocalSearchClient).findBestRestaurantMatch("External Noodle", "Seoul Gangnam Road 1");
        verify(restaurantRepository).save(restaurantCaptor.capture());
        Restaurant capturedRestaurant = restaurantCaptor.getValue();
        assertEquals("\uD30C\uC2A4\uD0C0", capturedRestaurant.getCategoryName());
        assertEquals("\uC591\uC2DD", capturedRestaurant.getPrimaryCategoryName());
        assertEquals("02-1234-5678", capturedRestaurant.getPhoneNumber());
        assertEquals("place-1", capturedRestaurant.getPcmapPlaceId());
        verify(listRestaurantRepository).save(any(ListRestaurant.class));
    }

    @Test
    @DisplayName("external fallback result must match list region")
    void addExternalFallbackRestaurantRejectsDifferentRegion() {
        Long userId = 1L;
        Long listId = 10L;
        UserList userList = ownedList(userId, listId, "Seoul Gangnam");
        PcmapRestaurantCandidate candidate = candidate("place-1", "Seoul Mapo Road 1");

        when(userListRepository.findByIdAndIsDeletedFalse(listId)).thenReturn(Optional.of(userList));
        when(pcmapSearchClient.searchRestaurants("Mapo noodle", 20)).thenReturn(List.of(candidate));

        assertThrows(BusinessException.class, () ->
                userListService.addExternalFallbackRestaurant(userId, listId, request("Mapo noodle", "place-1")));
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
        return new PcmapRestaurantCandidate(
                placeId,
                "External Noodle",
                "Noodle",
                address,
                address,
                address,
                "image",
                "127.0",
                "37.0"
        );
    }

    private UserList ownedList(Long userId, Long listId, String regionName) {
        User user = User.builder()
                .provider("kakao")
                .providerUserId("provider-user")
                .nickname("tester")
                .profileImageUrl("profile")
                .role(User.Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);

        UserList userList = UserList.builder()
                .user(user)
                .title("list")
                .description("description")
                .regionName(regionName)
                .build();
        ReflectionTestUtils.setField(userList, "id", listId);
        return userList;
    }

    private Restaurant restaurant(Long restaurantId, String regionName) {
        Restaurant restaurant = Restaurant.builder()
                .name("External Noodle")
                .address("Seoul Gangnam Road 1")
                .regionName(regionName)
                .lat(new BigDecimal("37.0"))
                .lng(new BigDecimal("127.0"))
                .imageUrl("image")
                .pcmapPlaceId("place-1")
                .build();
        ReflectionTestUtils.setField(restaurant, "id", restaurantId);
        return restaurant;
    }
}
