package com.example.Capstone.dto.response;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.RestaurantMenuItem;
import com.example.Capstone.domain.RestaurantPhoto;
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
        List<String> conveniences,
        Boolean parkingAvailable,
        List<RestaurantTagResponse> additionalInfoTags,
        List<RestaurantMenuItemResponse> menus,
        List<ParkingLotResponse> nearbyParkingLots
) {
    private static final String RESTAURANT_IMAGE_SOURCE = "RESTAURANT_IMAGE";

    public static RestaurantDetailResponse from(
            Restaurant restaurant,
            List<RestaurantMenuItem> menuItems,
            List<RestaurantTag> restaurantTags,
            List<RestaurantPhoto> restaurantPhotos,
            List<ParkingLotResponse> nearbyParkingLots,
            RestaurantBusinessHoursResponse businessHours,
            RestaurantBusinessHoursDisplayResponse businessHoursDisplay,
            RestaurantCurrentBusinessStatusResponse currentBusinessStatus
    ) {
        List<RestaurantPhotoResponse> photos = resolvePhotos(restaurant, restaurantPhotos);

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
                restaurant.getConveniences() == null ? List.of() : restaurant.getConveniences(),
                restaurant.isParkingAvailable(),
                restaurantTags.stream()
                        .map(RestaurantTagResponse::from)
                        .toList(),
                menuItems.stream()
                        .map(RestaurantMenuItemResponse::from)
                        .toList(),
                nearbyParkingLots == null ? List.of() : nearbyParkingLots
        );
    }

    private static List<RestaurantPhotoResponse> resolvePhotos(
            Restaurant restaurant,
            List<RestaurantPhoto> restaurantPhotos
    ) {
        if (restaurantPhotos != null && !restaurantPhotos.isEmpty()) {
            return restaurantPhotos.stream()
                    .map(RestaurantPhotoResponse::from)
                    .toList();
        }

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
