package com.example.Capstone.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.Capstone.service.support.RestaurantCategoryResolver;

class RestaurantCategoryResolverTest {

    @Test
    @DisplayName("세부 카테고리를 상위 카테고리로 정규화한다")
    void resolvesPrimaryCategory() {
        assertEquals("중식", RestaurantCategoryResolver.resolvePrimaryCategory("중식>중식당"));
        assertEquals("일식", RestaurantCategoryResolver.resolvePrimaryCategory("돈가스"));
        assertEquals("양식", RestaurantCategoryResolver.resolvePrimaryCategory("스파게티,파스타전문"));
        assertEquals("한식", RestaurantCategoryResolver.resolvePrimaryCategory("소고기구이"));
    }
}
