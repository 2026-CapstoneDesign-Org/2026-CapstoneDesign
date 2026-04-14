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
class RestaurantRankingRepositoryTest {

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserListRepository userListRepository;

    @Autowired
    private ListRestaurantRepository listRestaurantRepository;

    @Test
    @DisplayName("鍮꾧났媛?由ъ뒪?몃? ?ы븿?섍퀬 ?숈씪 ?ъ슜???숈씪 ?앸떦? 理쒓퀬 ?먯닔 1?뚮쭔 諛섏쁺?쒕떎")
    void includesPrivateListAndUsesBestScorePerUserRestaurant() {
        String region = unique("ranking-region-best");
        String category = unique("ranking-category-best");

        User user1 = saveUser("best-u1");
        User user2 = saveUser("best-u2");

        Restaurant topRestaurant = saveRestaurant("best-top", region, category);
        Restaurant otherRestaurant = saveRestaurant("best-other", region, category);

        UserList privateList = saveList(user1, region, false);
        UserList publicList = saveList(user1, region, true);
        UserList secondUserList = saveList(user2, region, true);

        saveListRestaurant(privateList, topRestaurant, "10.0", "10.0", "10.0");
        saveListRestaurant(publicList, topRestaurant, "5.0", "5.0", "5.0");
        saveListRestaurant(secondUserList, topRestaurant, "8.0", "8.0", "8.0");

        saveListRestaurant(publicList, otherRestaurant, "7.0", "7.0", "7.0");
        saveListRestaurant(secondUserList, otherRestaurant, "7.0", "7.0", "7.0");

        List<RestaurantRankingRow> rankings = restaurantRepository.findRestaurantRankings(region, category, 10, 5);

        RestaurantRankingRow topRow = rankings.stream()
                .filter(row -> row.restaurantId().equals(topRestaurant.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(2L, topRow.evaluationCount());
        assertTrue(topRow.averageAutoScore().compareTo(new BigDecimal("90.0")) == 0);
        assertEquals(topRestaurant.getId(), rankings.get(0).restaurantId());
    }

    @Test
    @DisplayName("region/category/hide/delete ?꾪꽣瑜??곸슜?섍퀬 理쒖쥌 ?숈젏? restaurant id ?ㅻ쫫李⑥닚?쇰줈 ?뺣젹?쒕떎")
    void appliesFiltersAndResolvesFinalTieByRestaurantId() {
        String region = unique("ranking-region-filter");
        String category = unique("ranking-category-filter");
        String otherRegion = unique("ranking-region-other");

        User activeUser1 = saveUser("filter-u1");
        User activeUser2 = saveUser("filter-u2");
        User hiddenUser = saveUser("filter-hidden");
        hiddenUser.hide();
        userRepository.save(hiddenUser);

        Restaurant rankedA = saveRestaurant("filter-a", region, category);
        Restaurant rankedB = saveRestaurant("filter-b", region, category);
        Restaurant wrongCategory = saveRestaurant("filter-category-out", region, unique("category-out"));
        Restaurant wrongRegion = saveRestaurant("filter-region-out", otherRegion, category);
        Restaurant hiddenRestaurant = saveRestaurant("filter-hidden-restaurant", region, category);
        hiddenRestaurant.hide();
        restaurantRepository.save(hiddenRestaurant);

        UserList list1 = saveList(activeUser1, region, true);
        UserList list2 = saveList(activeUser2, region, true);
        UserList hiddenUserList = saveList(hiddenUser, region, true);
        UserList hiddenList = saveList(activeUser1, region, true);
        hiddenList.hide();
        userListRepository.save(hiddenList);
        UserList deletedList = saveList(activeUser2, region, true);
        deletedList.delete();
        userListRepository.save(deletedList);

        saveListRestaurant(list1, rankedA, "9.0", "9.0", "9.0");
        saveListRestaurant(list2, rankedA, "7.0", "7.0", "7.0");
        saveListRestaurant(list1, rankedB, "9.0", "9.0", "9.0");
        saveListRestaurant(list2, rankedB, "7.0", "7.0", "7.0");

        saveListRestaurant(list1, wrongCategory, "10.0", "10.0", "10.0");
        saveListRestaurant(list1, wrongRegion, "10.0", "10.0", "10.0");
        saveListRestaurant(hiddenUserList, rankedA, "10.0", "10.0", "10.0");
        saveListRestaurant(hiddenList, rankedA, "10.0", "10.0", "10.0");
        saveListRestaurant(deletedList, rankedA, "10.0", "10.0", "10.0");
        saveListRestaurant(list1, hiddenRestaurant, "10.0", "10.0", "10.0");

        List<RestaurantRankingRow> rankings = restaurantRepository.findRestaurantRankings(region, category, 10, 5);

        assertEquals(2, rankings.size());
        assertEquals(rankedA.getId(), rankings.get(0).restaurantId());
        assertEquals(rankedB.getId(), rankings.get(1).restaurantId());
        assertEquals(2L, rankings.get(0).evaluationCount());
        assertEquals(2L, rankings.get(1).evaluationCount());
        assertTrue(rankings.get(0).averageAutoScore().compareTo(new BigDecimal("80.0")) == 0);
        assertTrue(rankings.get(1).averageAutoScore().compareTo(new BigDecimal("80.0")) == 0);
        assertTrue(rankings.get(0).adjustedScore().compareTo(rankings.get(1).adjustedScore()) == 0);
    }

    @Test
    @DisplayName("limit留뚰겮留??곸쐞 ??궧??諛섑솚?쒕떎")
    void respectsLimit() {
        String region = unique("ranking-region-limit");
        String category = unique("ranking-category-limit");

        User user1 = saveUser("limit-u1");
        User user2 = saveUser("limit-u2");

        Restaurant first = saveRestaurant("limit-first", region, category);
        Restaurant second = saveRestaurant("limit-second", region, category);
        Restaurant third = saveRestaurant("limit-third", region, category);

        UserList list1 = saveList(user1, region, true);
        UserList list2 = saveList(user2, region, true);

        saveListRestaurant(list1, first, "10.0", "10.0", "10.0");
        saveListRestaurant(list2, first, "9.0", "9.0", "9.0");

        saveListRestaurant(list1, second, "8.0", "8.0", "8.0");
        saveListRestaurant(list2, second, "8.0", "8.0", "8.0");

        saveListRestaurant(list1, third, "7.0", "7.0", "7.0");
        saveListRestaurant(list2, third, "7.0", "7.0", "7.0");

        List<RestaurantRankingRow> rankings = restaurantRepository.findRestaurantRankings(region, category, 2, 5);

        assertEquals(2, rankings.size());
        assertEquals(first.getId(), rankings.get(0).restaurantId());
        assertEquals(second.getId(), rankings.get(1).restaurantId());
    }

    @Test
    @DisplayName("?됯? ?섍? 留롮? ?앸떦??蹂댁젙 ?먯닔?먯꽌 ?좊━?댁쭏 ???덈떎")
    void reflectsAdjustedScoreByEvaluationCount() {
        String region = unique("ranking-region-adj");
        String category = unique("ranking-category-ad");

        User highAverageUser = saveUser("adj-hi");
        User user1 = saveUser("adj-u1");
        User user2 = saveUser("adj-u2");
        User user3 = saveUser("adj-u3");
        User user4 = saveUser("adj-u4");
        User user5 = saveUser("adj-u5");

        Restaurant lowCountHighAverage = saveRestaurant("adj-high", region, category);
        Restaurant highCountStable = saveRestaurant("adj-stable", region, category);
        Restaurant lowBaseline = saveRestaurant("adj-low", region, category);

        UserList highAverageList = saveList(highAverageUser, region, true);
        saveListRestaurant(highAverageList, lowCountHighAverage, "10.0", "10.0", "10.0");

        for (User user : List.of(user1, user2, user3, user4, user5)) {
            UserList list = saveList(user, region, true);
            saveListRestaurant(list, highCountStable, "9.5", "9.5", "9.5");
            saveListRestaurant(list, lowBaseline, "1.0", "1.0", "1.0");
        }

        List<RestaurantRankingRow> rankings = restaurantRepository.findRestaurantRankings(region, category, 10, 5);

        assertEquals(highCountStable.getId(), rankings.get(0).restaurantId());
        assertEquals(lowCountHighAverage.getId(), rankings.get(1).restaurantId());
        assertTrue(rankings.get(0).adjustedScore().compareTo(rankings.get(1).adjustedScore()) > 0);
        assertEquals(5L, rankings.get(0).evaluationCount());
        assertEquals(1L, rankings.get(1).evaluationCount());
        assertTrue(rankings.get(1).averageAutoScore().compareTo(rankings.get(0).averageAutoScore()) > 0);
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

    private ListRestaurant saveListRestaurant(
            UserList userList,
            Restaurant restaurant,
            String taste,
            String value,
            String mood
    ) {
        ListRestaurant listRestaurant = ListRestaurant.builder()
                .userList(userList)
                .restaurant(restaurant)
                .tasteScore(new BigDecimal(taste))
                .valueScore(new BigDecimal(value))
                .moodScore(new BigDecimal(mood))
                .build();
        return listRestaurantRepository.save(listRestaurant);
    }

    private String unique(String prefix) {
        String normalizedPrefix = prefix.length() > 20 ? prefix.substring(0, 20) : prefix;
        return normalizedPrefix + "-" + UUID.randomUUID().toString().substring(0, 6);
    }
}
