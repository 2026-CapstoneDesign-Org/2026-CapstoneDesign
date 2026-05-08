package com.example.Capstone.dto.response;

import java.math.BigDecimal;

import com.example.Capstone.domain.RestaurantMenuItem;

public record RestaurantMenuItemResponse(
        Long id,
        Integer displayOrder,
        String menuName,
        String description,
        String priceText,
        BigDecimal priceValue
) {
    public static RestaurantMenuItemResponse from(RestaurantMenuItem menuItem) {
        return new RestaurantMenuItemResponse(
                menuItem.getId(),
                menuItem.getDisplayOrder(),
                menuItem.getMenuName(),
                menuItem.getDescription(),
                menuItem.getPriceText(),
                menuItem.getPriceValue()
        );
    }
}
