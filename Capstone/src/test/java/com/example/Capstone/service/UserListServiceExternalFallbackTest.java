package com.example.Capstone.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.Capstone.domain.ListRestaurant;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.User;
import com.example.Capstone.domain.UserList;
import com.example.Capstone.dto.request.AddExternalRestaurantRequest;
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
    private ExternalFallbackRestaurantRegistrationService externalFallbackRestaurantRegistrationService;

    @InjectMocks
    private UserListService userListService;

    @Test
    @DisplayName("external fallback result is re-checked and added to a list")
    void addExternalFallbackRestaurantCreatesRestaurantAndListItem() {
        Long userId = 1L;
        Long listId = 10L;
        UserList userList = ownedList(userId, listId, "Seoul Gangnam");
        Restaurant restaurant = restaurant(100L, "Seoul Gangnam");

        when(userListRepository.findByIdAndIsDeletedFalse(listId)).thenReturn(Optional.of(userList));
        when(externalFallbackRestaurantRegistrationService.registerVerifiedRestaurant(
                request("Gangnam noodle", "place-1"),
                "Seoul Gangnam"
        )).thenReturn(restaurant);
        when(listRestaurantRepository.findByUserListIdAndRestaurantId(listId, 100L))
                .thenReturn(Optional.empty());

        userListService.addExternalFallbackRestaurant(userId, listId, request("Gangnam noodle", "place-1"));

        ArgumentCaptor<ListRestaurant> listRestaurantCaptor = ArgumentCaptor.forClass(ListRestaurant.class);
        verify(externalFallbackRestaurantRegistrationService).registerVerifiedRestaurant(
                request("Gangnam noodle", "place-1"),
                "Seoul Gangnam"
        );
        verify(listRestaurantRepository).save(listRestaurantCaptor.capture());
        assertEquals(100L, listRestaurantCaptor.getValue().getRestaurant().getId());
        assertEquals(new BigDecimal("8.0"), listRestaurantCaptor.getValue().getTasteScore());
        assertEquals(new BigDecimal("7.0"), listRestaurantCaptor.getValue().getValueScore());
        assertEquals(new BigDecimal("6.0"), listRestaurantCaptor.getValue().getMoodScore());
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
