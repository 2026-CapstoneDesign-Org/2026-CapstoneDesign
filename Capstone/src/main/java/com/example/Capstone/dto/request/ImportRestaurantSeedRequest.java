package com.example.Capstone.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Restaurant seed preview import request")
public record ImportRestaurantSeedRequest(
        @Schema(
                description = "Restaurant preview JSON path. Uses the default preview file when omitted.",
                example = "seed-data/restaurants-seed-preview.json"
        )
        String restaurantsFilePath,
        @Schema(
                description = "Restaurant menu item preview JSON path.",
                example = "seed-data/restaurant-menu-items-seed-preview.json"
        )
        String menuItemsFilePath,
        @Schema(
                description = "Tag preview JSON path.",
                example = "seed-data/tags-seed-preview.json"
        )
        String tagsFilePath,
        @Schema(
                description = "Restaurant-tag preview JSON path.",
                example = "seed-data/restaurant-tags-seed-preview.json"
        )
        String restaurantTagsFilePath
) {
}
