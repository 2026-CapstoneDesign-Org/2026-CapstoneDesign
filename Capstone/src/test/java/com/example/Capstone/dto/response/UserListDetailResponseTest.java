package com.example.Capstone.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.Capstone.domain.ListRestaurant;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.UserList;

class UserListDetailResponseTest {

    @Test
    @DisplayName("리스트 상세 응답 식당은 autoScore 내림차순으로 정렬된다")
    void fromSortsRestaurantsByAutoScoreDescending() {
        UserList userList = UserList.builder()
                .user(null)
                .title("테스트 리스트")
                .description("설명")
                .regionName("서울")
                .build();

        userList.getListRestaurants().add(listRestaurant(userList, "중간", "7.0", "7.0", "7.0"));
        userList.getListRestaurants().add(listRestaurant(userList, "높음", "9.0", "9.0", "9.0"));
        userList.getListRestaurants().add(listRestaurant(userList, "낮음", "5.0", "5.0", "5.0"));

        UserListDetailResponse response = UserListDetailResponse.from(userList);

        assertThat(response.restaurants())
                .extracting(ListRestaurantResponse::autoScore)
                .containsExactly(
                        new BigDecimal("90.0"),
                        new BigDecimal("70.0"),
                        new BigDecimal("50.0")
                );
    }

    private ListRestaurant listRestaurant(
            UserList userList,
            String restaurantName,
            String tasteScore,
            String valueScore,
            String moodScore
    ) {
        return ListRestaurant.builder()
                .userList(userList)
                .restaurant(restaurant(restaurantName))
                .tasteScore(new BigDecimal(tasteScore))
                .valueScore(new BigDecimal(valueScore))
                .moodScore(new BigDecimal(moodScore))
                .build();
    }

    private Restaurant restaurant(String name) {
        return Restaurant.builder()
                .name(name)
                .address("서울 테스트로 1")
                .regionName("서울")
                .build();
    }
}
