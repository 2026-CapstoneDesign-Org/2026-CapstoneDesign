package com.example.Capstone.client;

import java.util.List;

public interface PcmapSearchClient {

    List<PcmapRestaurantCandidate> searchRestaurants(String keyword, int limit);

    record PcmapRestaurantCandidate(
            String placeId,
            String name,
            String category,
            String address,
            String roadAddress,
            String fullAddress,
            String imageUrl,
            String x,
            String y
    ) {
    }
}
