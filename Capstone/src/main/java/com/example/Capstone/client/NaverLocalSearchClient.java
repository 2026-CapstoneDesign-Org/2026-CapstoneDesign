package com.example.Capstone.client;

import java.util.Optional;

public interface NaverLocalSearchClient {

    Optional<NaverLocalRestaurantCandidate> findBestRestaurantMatch(String restaurantName, String address);

    record NaverLocalRestaurantCandidate(
            String title,
            String category,
            String telephone,
            String address,
            String roadAddress,
            String mapx,
            String mapy
    ) {
    }
}
