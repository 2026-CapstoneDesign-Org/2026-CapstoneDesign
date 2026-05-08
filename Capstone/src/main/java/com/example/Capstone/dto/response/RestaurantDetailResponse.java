package com.example.Capstone.dto.response;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.RestaurantMenuItem;
import com.example.Capstone.domain.RestaurantTag;

public record RestaurantDetailResponse(
        Long id,
        String name,
        String address,
        String roadAddress,
        String lotAddress,
        String regionName,
        BigDecimal lat,
        BigDecimal lng,
        String imageUrl,
        List<RestaurantPhotoResponse> photos,
        String phoneNumber,
        RestaurantBusinessHoursResponse businessHours,
        RestaurantBusinessHoursDisplayResponse businessHoursDisplay,
        RestaurantCurrentBusinessStatusResponse currentBusinessStatus,
        String categoryName,
        String primaryCategoryName,
        List<String> categories,
        List<RestaurantTagResponse> additionalInfoTags,
        List<RestaurantMenuItemResponse> menus
) {
    private static final String RESTAURANT_IMAGE_SOURCE = "RESTAURANT_IMAGE";

    public static RestaurantDetailResponse from(
            Restaurant restaurant,
            List<RestaurantMenuItem> menuItems,
            List<RestaurantTag> restaurantTags,
            RestaurantBusinessHoursResponse businessHours,
            RestaurantBusinessHoursDisplayResponse businessHoursDisplay,
            RestaurantCurrentBusinessStatusResponse currentBusinessStatus
    ) {
        List<RestaurantPhotoResponse> photos = resolvePhotos(restaurant);

        return new RestaurantDetailResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getDisplayAddress(),
                restaurant.getRoadAddress(),
                restaurant.getAddress(),
                restaurant.getRegionName(),
                restaurant.getLat(),
                restaurant.getLng(),
                restaurant.getImageUrl(),
                photos,
                restaurant.getPhoneNumber(),
                businessHours,
                businessHoursDisplay,
                currentBusinessStatus,
                restaurant.getCategoryName(),
                restaurant.getPrimaryCategoryName(),
                restaurant.getCategoryNames(),
                restaurantTags.stream()
                        .map(RestaurantTagResponse::from)
                        .toList(),
                menuItems.stream()
                        .map(RestaurantMenuItemResponse::from)
                        .toList()
        );
    }

    private static List<RestaurantPhotoResponse> resolvePhotos(Restaurant restaurant) {
        List<RestaurantPhotoResponse> photos = new ArrayList<>();
        if (restaurant.getImageUrl() != null && !restaurant.getImageUrl().isBlank()) {
            photos.add(new RestaurantPhotoResponse(
                    restaurant.getImageUrl(),
                    RESTAURANT_IMAGE_SOURCE,
                    0
            ));
        }
        return photos;
    }
}
