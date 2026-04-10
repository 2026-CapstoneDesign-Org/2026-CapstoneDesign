package com.example.Capstone.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "식당 seed preview import 결과")
public record RestaurantSeedImportResponse(
        @Schema(description = "실제로 사용한 식당 preview JSON 절대 경로")
        String restaurantsFilePath,
        @Schema(description = "실제로 사용한 카테고리 preview JSON 절대 경로")
        String categoriesFilePath,
        @Schema(description = "실제로 사용한 정규화 메뉴 preview JSON 절대 경로")
        String menuItemsFilePath,
        @Schema(description = "실제로 사용한 태그 preview JSON 절대 경로")
        String tagsFilePath,
        @Schema(description = "실제로 사용한 식당-태그 preview JSON 절대 경로")
        String restaurantTagsFilePath,
        @Schema(description = "읽어들인 식당 seed row 수")
        int totalRestaurantCount,
        @Schema(description = "읽어들인 카테고리 seed row 수")
        int totalCategoryCount,
        @Schema(description = "읽어들인 정규화 메뉴 seed row 수")
        int totalMenuItemCount,
        @Schema(description = "읽어들인 태그 seed row 수")
        int totalTagCount,
        @Schema(description = "읽어들인 식당-태그 seed row 수")
        int totalRestaurantTagCount,
        @Schema(description = "menu_json 이 존재해 메뉴 메타데이터가 반영된 식당 수")
        int menuMappedRestaurantCount,
        @Schema(description = "새로 생성된 식당 수")
        int createdRestaurantCount,
        @Schema(description = "기존 식당을 업데이트한 수")
        int updatedRestaurantCount,
        @Schema(description = "새로 생성된 태그 수")
        int createdTagCount,
        @Schema(description = "기존 태그를 업데이트한 수")
        int updatedTagCount,
        @Schema(description = "식당별로 교체 저장된 카테고리 row 수")
        int replacedCategoryCount,
        @Schema(description = "식당별로 교체 저장된 정규화 메뉴 row 수")
        int replacedMenuItemCount,
        @Schema(description = "식당별로 교체 저장된 식당-태그 row 수")
        int replacedRestaurantTagCount
) {}
