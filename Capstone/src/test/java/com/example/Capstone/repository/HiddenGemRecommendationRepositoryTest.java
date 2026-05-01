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
class HiddenGemRecommendationRepositoryTest {

    @Autowired
    private HiddenGemRecommendationRepository hiddenGemRecommendationRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserListRepository userListRepository;

    @Autowired
    private ListRestaurantRepository listRestaurantRepository;

    @Test
    @DisplayName("동/읍/면 지역 필터와 숨김/삭제 필터를 적용하고 사용자-식당 최고 점수 1회만 반영한다")
    void findsCandidatesByTownNameWithVisibilityFiltersAndBestScorePerUserRestaurant() {
        String regionName = unique("hidden-region");
        String townName = unique("hidden-town");
        String otherTownName = unique("other-town");

        User user1 = saveUser("hidden-u1");
        User user2 = saveUser("hidden-u2");
        User hiddenUser = saveUser("hidden-user");
        hiddenUser.hide();
        userRepository.save(hiddenUser);
        User deletedUser = saveUser("deleted-user");
        deletedUser.delete();
        userRepository.save(deletedUser);

        Restaurant target = saveRestaurant("target", regionName, townName, "도로명-target");
        Restaurant competitor = saveRestaurant("competitor", regionName, townName, "");
        Restaurant wrongTown = saveRestaurant("wrong-town", regionName, otherTownName, "도로명-wrong");
        Restaurant hiddenRestaurant = saveRestaurant("hidden-restaurant", regionName, townName, "도로명-hidden");
        hiddenRestaurant.hide();
        restaurantRepository.save(hiddenRestaurant);
        Restaurant deletedRestaurant = saveRestaurant("deleted-restaurant", regionName, townName, "도로명-deleted");
        deletedRestaurant.delete();
        restaurantRepository.save(deletedRestaurant);

        UserList privateList = saveList(user1, regionName, false);
        UserList publicList = saveList(user1, regionName, true);
        UserList secondUserList = saveList(user2, regionName, true);
        UserList hiddenUserList = saveList(hiddenUser, regionName, true);
        UserList deletedUserList = saveList(deletedUser, regionName, true);
        UserList hiddenList = saveList(user1, regionName, true);
        hiddenList.hide();
        userListRepository.save(hiddenList);
        UserList deletedList = saveList(user2, regionName, true);
        deletedList.delete();
        userListRepository.save(deletedList);

        saveListRestaurant(privateList, target, "10.0", "10.0", "10.0");
        saveListRestaurant(publicList, target, "5.0", "5.0", "5.0");
        saveListRestaurant(secondUserList, target, "8.0", "8.0", "8.0");
        saveListRestaurant(publicList, competitor, "7.0", "7.0", "7.0");
        saveListRestaurant(secondUserList, competitor, "7.0", "7.0", "7.0");

        saveListRestaurant(publicList, wrongTown, "10.0", "10.0", "10.0");
        saveListRestaurant(hiddenUserList, target, "10.0", "10.0", "10.0");
        saveListRestaurant(deletedUserList, target, "10.0", "10.0", "10.0");
        saveListRestaurant(hiddenList, target, "10.0", "10.0", "10.0");
        saveListRestaurant(deletedList, target, "10.0", "10.0", "10.0");
        saveListRestaurant(publicList, hiddenRestaurant, "10.0", "10.0", "10.0");
        saveListRestaurant(publicList, deletedRestaurant, "10.0", "10.0", "10.0");

        List<HiddenGemRestaurantRow> rows = hiddenGemRecommendationRepository.findHiddenGemCandidates(townName, 3);

        assertEquals(2, rows.size());
        HiddenGemRestaurantRow targetRow = rows.stream()
                .filter(row -> row.restaurantId().equals(target.getId()))
                .findFirst()
                .orElseThrow();
        HiddenGemRestaurantRow competitorRow = rows.stream()
                .filter(row -> row.restaurantId().equals(competitor.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(target.getId(), rows.get(0).restaurantId());
        assertEquals("도로명-target", targetRow.address());
        assertEquals(townName, targetRow.regionTownName());
        assertEquals(2L, targetRow.evaluationCount());
        assertTrue(targetRow.averageAutoScore().compareTo(new BigDecimal("90.0")) == 0);
        assertEquals("지번주소-competitor", competitorRow.address());
        assertEquals(2L, competitorRow.evaluationCount());
        assertTrue(competitorRow.averageAutoScore().compareTo(new BigDecimal("70.0")) == 0);
        assertTrue(targetRow.adjustedScore().compareTo(competitorRow.adjustedScore()) > 0);
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

    private Restaurant saveRestaurant(String nameSuffix, String regionName, String townName, String roadAddress) {
        Restaurant restaurant = Restaurant.builder()
                .name(unique(nameSuffix))
                .address("지번주소-" + nameSuffix)
                .roadAddress(roadAddress)
                .categoryName("한식")
                .regionName(regionName)
                .regionTownName(townName)
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
                .description("설명")
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
