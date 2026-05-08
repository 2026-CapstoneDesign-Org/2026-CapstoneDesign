package com.example.Capstone.dto.response;

import com.example.Capstone.domain.RestaurantTag;

public record RestaurantTagResponse(
        Long tagId,
        String tagKey,
        String tagName,
        String parentTagKey,
        Boolean isPrimary,
        Integer matchedMenuCount
) {
    public static RestaurantTagResponse from(RestaurantTag restaurantTag) {
        return new RestaurantTagResponse(
                restaurantTag.getTag().getId(),
                restaurantTag.getTag().getTagKey(),
                restaurantTag.getTag().getTagName(),
                restaurantTag.getTag().getParentTagKey(),
                restaurantTag.getIsPrimary(),
                restaurantTag.getMatchedMenuCount()
        );
    }
}
