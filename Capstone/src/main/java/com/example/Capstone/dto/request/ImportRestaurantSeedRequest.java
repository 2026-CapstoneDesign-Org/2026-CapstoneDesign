package com.example.Capstone.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "식당 seed preview import 요청")
public record ImportRestaurantSeedRequest(
        @Schema(
                description = "식당 preview JSON 경로. 비우면 기본 경로 seed-data/restaurants-seed-preview.json 을 사용한다.",
                example = "seed-data/restaurants-seed-preview.json"
        )
        String restaurantsFilePath,
        @Schema(
                description = "카테고리 preview JSON 경로. 비우면 기본 경로 seed-data/restaurant-categories-seed-preview.json 을 사용한다.",
                example = "seed-data/restaurant-categories-seed-preview.json"
        )
        String categoriesFilePath,
        @Schema(
                description = "정규화 메뉴 preview JSON 경로. 비우면 기본 경로 seed-data/restaurant-menu-items-seed-preview.json 을 사용한다.",
                example = "seed-data/restaurant-menu-items-seed-preview.json"
        )
        String menuItemsFilePath,
        @Schema(
                description = "태그 preview JSON 경로. 비우면 기본 경로 seed-data/tags-seed-preview.json 을 사용한다.",
                example = "seed-data/tags-seed-preview.json"
        )
        String tagsFilePath,
        @Schema(
                description = "식당-태그 preview JSON 경로. 비우면 기본 경로 seed-data/restaurant-tags-seed-preview.json 을 사용한다.",
                example = "seed-data/restaurant-tags-seed-preview.json"
        )
        String restaurantTagsFilePath
) {}
