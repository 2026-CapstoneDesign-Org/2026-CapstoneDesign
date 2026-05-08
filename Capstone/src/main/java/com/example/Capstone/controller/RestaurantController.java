package com.example.Capstone.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.Capstone.config.SwaggerConfig;
import com.example.Capstone.dto.response.RestaurantDetailResponse;
import com.example.Capstone.dto.response.RestaurantResponse;
import com.example.Capstone.service.RestaurantService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/restaurants")
@RequiredArgsConstructor
@Tag(name = "Restaurant", description = "Restaurant lookup APIs.")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
public class RestaurantController {

    private final RestaurantService restaurantService;

    @Operation(
            summary = "Search restaurants by name",
            description = "Returns visible restaurants whose names contain the provided keyword."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Restaurants retrieved.",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = RestaurantResponse.class))
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid keyword."),
            @ApiResponse(responseCode = "401", description = "Authentication required.")
    })
    @GetMapping
    public ResponseEntity<List<RestaurantResponse>> searchRestaurants(
            @Parameter(description = "Restaurant name keyword.", example = "돈까스")
            @RequestParam String keyword
    ) {
        return ResponseEntity.ok(restaurantService.searchRestaurants(keyword));
    }

    @Operation(
            summary = "Get restaurant detail",
            description = "Returns detail data for the restaurant detail page, including home, menu, and photo fields."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Restaurant retrieved.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RestaurantDetailResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "404", description = "Restaurant not found.")
    })
    @GetMapping("/{id}")
    public ResponseEntity<RestaurantDetailResponse> getRestaurant(
            @Parameter(description = "Restaurant identifier.", example = "1")
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(restaurantService.getRestaurant(id));
    }
}
