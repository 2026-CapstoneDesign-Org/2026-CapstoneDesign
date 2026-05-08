package com.example.Capstone.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.Capstone.config.SwaggerConfig;
import com.example.Capstone.dto.response.ParkingLotResponse;
import com.example.Capstone.service.ParkingLotService;

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
@Tag(name = "ParkingLot", description = "Restaurant nearby parking lot APIs.")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
public class RestaurantParkingLotController {

    private final ParkingLotService parkingLotService;

    @Operation(
            summary = "Get parking lots near a restaurant",
            description = "Returns saved parking lots sorted by straight-line distance from the restaurant coordinate."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Parking lots retrieved.",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ParkingLotResponse.class))
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid request or restaurant coordinate missing."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "404", description = "Restaurant not found.")
    })
    @GetMapping("/{restaurantId}/parking-lots")
    public ResponseEntity<List<ParkingLotResponse>> getParkingLotsByDistance(
            @Parameter(description = "Restaurant identifier.", example = "1")
            @PathVariable Long restaurantId,
            @Parameter(description = "Maximum number of rows. Defaults to 10, max 50.", example = "5")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Optional parking lot division filter, such as public parking.", example = "공영")
            @RequestParam(required = false) String parkingLotDivision
    ) {
        return ResponseEntity.ok(parkingLotService.getParkingLotsByDistance(
                restaurantId,
                limit,
                parkingLotDivision
        ));
    }
}
