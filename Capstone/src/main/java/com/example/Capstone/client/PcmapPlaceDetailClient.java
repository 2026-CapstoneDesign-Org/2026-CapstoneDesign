package com.example.Capstone.client;

import java.util.List;
import java.util.Optional;

public interface PcmapPlaceDetailClient {

    Optional<PcmapRestaurantDetail> fetchRestaurantDetail(String placeId);

    record PcmapRestaurantDetail(
            String placeId,
            String name,
            String categoryName,
            String address,
            String roadAddress,
            String imageUrl,
            String x,
            String y,
            String phoneNumber,
            String businessType,
            List<String> conveniences,
            String businessHoursRaw,
            List<PcmapMenuItem> menus
    ) {
    }

    record PcmapMenuItem(
            Integer displayOrder,
            String name,
            String priceText,
            String description,
            String rawTypeName
    ) {
    }
}
