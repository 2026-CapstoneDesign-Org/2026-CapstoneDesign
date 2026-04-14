package com.example.Capstone.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Restaurant seed preview import result")
public record RestaurantSeedImportResponse(
        @Schema(description = "Resolved restaurant preview JSON path")
        String restaurantsFilePath,
        @Schema(description = "Resolved menu item preview JSON path")
        String menuItemsFilePath,
        @Schema(description = "Resolved tag preview JSON path")
        String tagsFilePath,
        @Schema(description = "Resolved restaurant-tag preview JSON path")
        String restaurantTagsFilePath,
        @Schema(description = "Loaded restaurant seed row count")
        int totalRestaurantCount,
        @Schema(description = "Loaded menu item seed row count")
        int totalMenuItemCount,
        @Schema(description = "Loaded tag seed row count")
        int totalTagCount,
        @Schema(description = "Loaded restaurant-tag seed row count")
        int totalRestaurantTagCount,
        @Schema(description = "Restaurant count that included menu item data")
        int menuMappedRestaurantCount,
        @Schema(description = "Created restaurant count")
        int createdRestaurantCount,
        @Schema(description = "Updated restaurant count")
        int updatedRestaurantCount,
        @Schema(description = "Created tag count")
        int createdTagCount,
        @Schema(description = "Updated tag count")
        int updatedTagCount,
        @Schema(description = "Replaced menu item row count")
        int replacedMenuItemCount,
        @Schema(description = "Replaced restaurant-tag row count")
        int replacedRestaurantTagCount
) {
}
