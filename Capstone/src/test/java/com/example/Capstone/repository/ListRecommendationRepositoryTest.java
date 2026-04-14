package com.example.Capstone.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.domain.ListRestaurant;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.User;
import com.example.Capstone.domain.UserList;

@SpringBootTest
@Transactional
@ActiveProfiles({ "db", "key" })
class ListRecommendationRepositoryTest {

    @Autowired
    private ListRecommendationRepository listRecommendationRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserListRepository userListRepository;

    @Autowired
    private ListRestaurantRepository listRestaurantRepository;

    @Test
    @DisplayName("same-region ?꾨낫??public/visible/???由ъ뒪?몃쭔 ?ы븿?섍퀬 fallback? ?ㅻⅨ 吏??怨듦컻 由ъ뒪?몃쭔 諛섑솚?쒕떎")
    void filtersCandidateListsByVisibilityAndRegion() {
        String region = unique("list-rec-region");
        String fallbackRegion = unique("list-rec-other");
        String category = unique("list-rec-cat");

        User currentUser = saveUser("current");
        User otherUser = saveUser("other");
        User hiddenOwner = saveUser("hidden-owner");
        hiddenOwner.hide();
        userRepository.save(hiddenOwner);

        List<Restaurant> sameRegionRestaurants = saveRestaurants(region, category, 12);
        List<Restaurant> fallbackRestaurants = saveRestaurants(fallbackRegion, category, 5);
        Restaurant hiddenRestaurant = saveRestaurant("hidden-restaurant", region, category);
        hiddenRestaurant.hide();
        restaurantRepository.save(hiddenRestaurant);

        UserList currentUserList = saveList(currentUser, region, true);
        UserList publicCandidate = saveList(otherUser, region, true);
        UserList privateCandidate = saveList(otherUser, region, false);
        UserList hiddenOwnerCandidate = saveList(hiddenOwner, region, true);
        UserList insufficientVisibleCandidate = saveList(otherUser, region, true);
        UserList fallbackCandidate = saveList(otherUser, fallbackRegion, true);

        saveRestaurantsOnList(currentUserList, sameRegionRestaurants.subList(0, 5));
        saveRestaurantsOnList(publicCandidate, sameRegionRestaurants.subList(0, 5));
        saveRestaurantsOnList(privateCandidate, sameRegionRestaurants.subList(1, 6));
        saveRestaurantsOnList(hiddenOwnerCandidate, sameRegionRestaurants.subList(2, 7));
        saveRestaurantsOnList(insufficientVisibleCandidate, sameRegionRestaurants.subList(7, 11));
        saveListRestaurant(insufficientVisibleCandidate, hiddenRestaurant);
        saveRestaurantsOnList(fallbackCandidate, fallbackRestaurants);

        List<ListRecommendationRepository.CandidateListSummaryRow> sameRegionRows =
                listRecommendationRepository.findSameRegionCandidateLists(currentUser.getId(), region, 10, 5, 5);
        List<ListRecommendationRepository.CandidateListSummaryRow> fallbackRows =
                listRecommendationRepository.findFallbackCandidateLists(
                        currentUser.getId(),
                        region,
                        Integer.MAX_VALUE,
                        5,
                        5
                );

        assertEquals(1, sameRegionRows.size());
        assertEquals(publicCandidate.getId(), sameRegionRows.get(0).listId());
        assertEquals(5L, sameRegionRows.get(0).restaurantCount());
        assertTrue(sameRegionRows.get(0).adjustedQualityScore().compareTo(BigDecimal.ZERO) > 0);

        assertTrue(fallbackRows.stream().anyMatch(row -> row.listId().equals(fallbackCandidate.getId())));
        assertTrue(fallbackRows.stream().noneMatch(row -> row.listId().equals(currentUserList.getId())));
        assertTrue(fallbackRows.stream().noneMatch(row -> row.listId().equals(publicCandidate.getId())));
        assertTrue(fallbackRows.stream().noneMatch(row -> row.listId().equals(privateCandidate.getId())));
        assertTrue(fallbackRows.stream().noneMatch(row -> row.listId().equals(hiddenOwnerCandidate.getId())));
        assertTrue(fallbackRows.stream().noneMatch(row -> row.listId().equals(insufficientVisibleCandidate.getId())));
    }

    @Test
    @DisplayName("owner interaction 議고쉶??overlap??理쒖냼 湲곗? ?댁긽???뚯쑀?먮쭔 諛섑솚?쒕떎")
    void returnsOnlyOwnersMeetingMinimumOverlap() {
        String region = unique("owner-overlap-region");
        String category = unique("owner-overlap-cat");

        User currentUser = saveUser("current-overlap");
        User matchedOwner = saveUser("matched-owner");
        User unmatchedOwner = saveUser("unmatched-owner");

        Restaurant restaurantA = saveRestaurant("rest-a", region, category);
        Restaurant restaurantB = saveRestaurant("rest-b", region, category);
        Restaurant restaurantC = saveRestaurant("rest-c", region, category);
        Restaurant restaurantD = saveRestaurant("rest-d", region, category);
        Restaurant restaurantE = saveRestaurant("rest-e", region, category);

        UserList currentUserList = saveList(currentUser, region, true);
        UserList matchedOwnerList = saveList(matchedOwner, region, true);
        UserList unmatchedOwnerList = saveList(unmatchedOwner, region, true);

        saveListRestaurant(currentUserList, restaurantA);
        saveListRestaurant(currentUserList, restaurantB);
        saveListRestaurant(currentUserList, restaurantC);
        saveListRestaurant(currentUserList, restaurantD);
        saveListRestaurant(currentUserList, restaurantE);

        saveListRestaurant(matchedOwnerList, restaurantA);
        saveListRestaurant(matchedOwnerList, restaurantB);
        saveListRestaurant(matchedOwnerList, restaurantC);
        saveListRestaurant(matchedOwnerList, restaurantD);
        saveListRestaurant(matchedOwnerList, restaurantE);

        saveListRestaurant(unmatchedOwnerList, restaurantA);
        saveListRestaurant(unmatchedOwnerList, restaurantD);
        saveListRestaurant(unmatchedOwnerList, restaurantE);
        Restaurant restaurantF = saveRestaurant("rest-f", region, category);
        Restaurant restaurantG = saveRestaurant("rest-g", region, category);
        saveListRestaurant(unmatchedOwnerList, restaurantF);
        saveListRestaurant(unmatchedOwnerList, restaurantG);

        List<ListRecommendationRepository.OwnerInteractionRow> rows =
                listRecommendationRepository.findCandidateOwnerInteractions(
                        currentUser.getId(),
                        List.of(matchedOwner.getId(), unmatchedOwner.getId()),
                        4
                );

        assertTrue(rows.stream().allMatch(row -> row.ownerId().equals(matchedOwner.getId())));
        assertEquals(5, rows.size());
    }

    private User saveUser(String suffix) {
        User user = User.builder()
                .provider("kakao")
                .providerUserId(unique("provider-" + suffix))
                .nickname(unique("nick-" + suffix))
                .profileImageUrl("profile")
                .role(User.Role.USER)
                .build();
        return userRepository.save(user);
    }

    private List<Restaurant> saveRestaurants(String regionName, String categoryName, int count) {
        java.util.ArrayList<Restaurant> restaurants = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            restaurants.add(saveRestaurant("restaurant-" + index, regionName, categoryName));
        }
        return restaurants;
    }

    private Restaurant saveRestaurant(String nameSuffix, String regionName, String categoryName) {
        Restaurant restaurant = Restaurant.builder()
                .name(unique(nameSuffix))
                .address("二쇱냼")
                .categoryName(categoryName)
                .regionName(regionName)
                .lat(new BigDecimal("37.1000000"))
                .lng(new BigDecimal("127.1000000"))
                .imageUrl("image")
                .build();
        return restaurantRepository.save(restaurant);
    }

    private UserList saveList(User user, String regionName, boolean isPublic) {
        UserList userList = UserList.builder()
                .user(user)
                .title(unique("list"))
                .description("?ㅻ챸")
                .regionName(regionName)
                .build();
        userList.setPublic(isPublic);
        return userListRepository.save(userList);
    }

    private void saveRestaurantsOnList(UserList userList, List<Restaurant> restaurants) {
        for (Restaurant restaurant : restaurants) {
            saveListRestaurant(userList, restaurant);
        }
    }

    private ListRestaurant saveListRestaurant(UserList userList, Restaurant restaurant) {
        ListRestaurant listRestaurant = ListRestaurant.builder()
                .userList(userList)
                .restaurant(restaurant)
                .tasteScore(new BigDecimal("9.0"))
                .valueScore(new BigDecimal("8.0"))
                .moodScore(new BigDecimal("7.0"))
                .build();
        return listRestaurantRepository.save(listRestaurant);
    }

    private String unique(String prefix) {
        String normalizedPrefix = prefix.length() > 20 ? prefix.substring(0, 20) : prefix;
        return normalizedPrefix + "-" + UUID.randomUUID().toString().substring(0, 6);
    }
}
