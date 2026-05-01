package com.example.Capstone.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.example.Capstone.common.jwt.JwtProvider;
import com.example.Capstone.domain.ListRestaurant;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.User;
import com.example.Capstone.domain.UserList;
import com.example.Capstone.dto.response.HiddenGemRestaurantResponse;
import com.example.Capstone.exception.ErrorResponse;
import com.example.Capstone.repository.ListRestaurantRepository;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.UserListRepository;
import com.example.Capstone.repository.UserRepository;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles({ "db", "key" })
class HiddenGemRecommendationE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserListRepository userListRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private ListRestaurantRepository listRestaurantRepository;

    @Test
    @DisplayName("JWT 인증 HTTP 요청으로 동/읍/면 기준 숨은 맛집 추천 결과를 조회한다")
    void getsHiddenGemRecommendationsOverHttpWithJwt() {
        String regionName = unique("e2e-region");
        String townName = unique("e2e-town");
        User user1 = saveUser("e2e-u1");
        User user2 = saveUser("e2e-u2");
        Restaurant hiddenGem = saveRestaurant("e2e-hidden-gem", regionName, townName, "도로명-숨은맛집");
        Restaurant lowerScore = saveRestaurant("e2e-lower-score", regionName, townName, "");
        Restaurant oneCountOutlier = saveRestaurant("e2e-one-count", regionName, townName, "도로명-한명평가");

        UserList user1List = saveList(user1, regionName, false);
        UserList user2List = saveList(user2, regionName, true);

        saveListRestaurant(user1List, hiddenGem, "10.0", "10.0", "10.0");
        saveListRestaurant(user2List, hiddenGem, "8.0", "8.0", "8.0");
        saveListRestaurant(user1List, lowerScore, "7.0", "7.0", "7.0");
        saveListRestaurant(user2List, lowerScore, "7.0", "7.0", "7.0");
        saveListRestaurant(user1List, oneCountOutlier, "10.0", "10.0", "10.0");

        ResponseEntity<HiddenGemRestaurantResponse> response = restTemplate.exchange(
                "/recommendations/restaurants/hidden-gems?regionTownName={regionTownName}",
                HttpMethod.GET,
                authenticatedEntity(user1.getId()),
                HiddenGemRestaurantResponse.class,
                townName
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(townName, response.getBody().regionTownName());
        assertEquals(10, response.getBody().limit());
        assertEquals(2, response.getBody().items().size());
        assertEquals(1, response.getBody().items().get(0).rank());
        assertEquals(hiddenGem.getId(), response.getBody().items().get(0).restaurantId());
        assertEquals("도로명-숨은맛집", response.getBody().items().get(0).address());
        assertEquals(2L, response.getBody().items().get(0).evaluationCount());
        assertEquals(lowerScore.getId(), response.getBody().items().get(1).restaurantId());
        assertEquals("지번주소-e2e-lower-score", response.getBody().items().get(1).address());
        assertTrue(response.getBody().items().stream()
                .noneMatch(item -> item.restaurantId().equals(oneCountOutlier.getId())));
    }

    @Test
    @DisplayName("JWT 인증 HTTP 요청에서 regionTownName이 비어 있으면 400 에러를 반환한다")
    void returnsBadRequestForEmptyRegionTownNameOverHttpWithJwt() {
        User user = saveUser("e2e-bad-request");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/recommendations/restaurants/hidden-gems?regionTownName=",
                HttpMethod.GET,
                authenticatedEntity(user.getId()),
                ErrorResponse.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", response.getBody().code());
        assertEquals("regionTownName은 필수입니다.", response.getBody().message());
    }

    private HttpEntity<Void> authenticatedEntity(Long userId) {
        String token = jwtProvider.generateAccessToken(userId, User.Role.USER.name());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
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
