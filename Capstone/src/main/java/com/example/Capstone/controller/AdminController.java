package com.example.Capstone.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Capstone.config.SwaggerConfig;
import com.example.Capstone.dto.request.ApplyReservationMockResultRequest;
import com.example.Capstone.dto.request.ClawOpsRealCallPreflightRequest;
import com.example.Capstone.dto.request.CreateRestaurantRequest;
import com.example.Capstone.dto.request.UpdateCategoryRequest;
import com.example.Capstone.dto.request.UpdateRestaurantRequest;
import com.example.Capstone.dto.response.ClawOpsRealCallPreflightResponse;
import com.example.Capstone.dto.response.ReservationResponse;
import com.example.Capstone.dto.response.RestaurantResponse;
import com.example.Capstone.service.AdminService;
import com.example.Capstone.service.ClawOpsRealCallPreflightService;
import com.example.Capstone.service.ReservationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administrative APIs for restaurant, reservation, and visibility management.")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final ReservationService reservationService;
    private final ClawOpsRealCallPreflightService clawOpsRealCallPreflightService;

    @Operation(
            summary = "Create restaurant",
            description = "Creates a restaurant record managed by administrators."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Restaurant created.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RestaurantResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid restaurant payload."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "403", description = "Admin role required.")
    })
    @PostMapping("/restaurants")
    public ResponseEntity<RestaurantResponse> createRestaurant(
            @RequestBody @Valid CreateRestaurantRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.createRestaurant(request));
    }

    @Operation(
            summary = "Update restaurant",
            description = "Updates editable restaurant fields for an existing restaurant."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Restaurant updated.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RestaurantResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid update payload."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "403", description = "Admin role required."),
            @ApiResponse(responseCode = "404", description = "Restaurant not found.")
    })
    @PatchMapping("/restaurants/{id}")
    public ResponseEntity<RestaurantResponse> updateRestaurant(
            @PathVariable Long id,
            @RequestBody @Valid UpdateRestaurantRequest request
    ) {
        return ResponseEntity.ok(adminService.updateRestaurant(id, request));
    }

    @Operation(
            summary = "Update restaurant categories",
            description = "Updates the category payload used by the admin restaurant workflow."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categories updated."),
            @ApiResponse(responseCode = "400", description = "Invalid category payload."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "403", description = "Admin role required."),
            @ApiResponse(responseCode = "404", description = "Restaurant not found.")
    })
    @PatchMapping("/restaurants/{id}/categories")
    public ResponseEntity<Void> updateCategories(
            @PathVariable Long id,
            @RequestBody @Valid UpdateCategoryRequest request
    ) {
        adminService.updateCategories(id, request);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Hide restaurant",
            description = "Marks a restaurant as hidden from externally visible queries."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Restaurant hidden."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "403", description = "Admin role required."),
            @ApiResponse(responseCode = "404", description = "Restaurant not found.")
    })
    @PatchMapping("/restaurants/{id}/hide")
    public ResponseEntity<Void> hideRestaurant(@PathVariable Long id) {
        adminService.hideRestaurant(id);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Hide user",
            description = "Marks a user as hidden from externally visible queries."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User hidden."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "403", description = "Admin role required."),
            @ApiResponse(responseCode = "404", description = "User not found.")
    })
    @PatchMapping("/users/{id}/hide")
    public ResponseEntity<Void> hideUser(@PathVariable Long id) {
        adminService.hideUser(id);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Hide list",
            description = "Marks a user list as hidden from externally visible queries."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List hidden."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "403", description = "Admin role required."),
            @ApiResponse(responseCode = "404", description = "List not found.")
    })
    @PatchMapping("/lists/{id}/hide")
    public ResponseEntity<Void> hideList(@PathVariable Long id) {
        adminService.hideList(id);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Apply mock reservation result",
            description = "Applies a mock AI call result to a reservation. This endpoint is for admin/test operation only."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Mock reservation result applied.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReservationResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid mock result or state transition."),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "403", description = "Admin role required."),
            @ApiResponse(responseCode = "404", description = "Reservation not found.")
    })
    @PostMapping("/reservations/{reservationId}/mock-result")
    public ResponseEntity<ReservationResponse> applyReservationMockResult(
            @PathVariable Long reservationId,
            @RequestBody @Valid ApplyReservationMockResultRequest request
    ) {
        return ResponseEntity.ok(reservationService.applyMockResultByAdmin(reservationId, request));
    }

    @Operation(
            summary = "Preflight ClawOps real-call candidate",
            description = "Checks whether a reservation or test phone number satisfies the dev-only ClawOps real-call gate. "
                    + "This endpoint never calls ClawOps and never starts a phone call."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Preflight completed without external API calls.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ClawOpsRealCallPreflightResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Authentication required."),
            @ApiResponse(responseCode = "403", description = "Admin role required.")
    })
    @PostMapping("/reservations/clawops-real-call/preflight")
    public ResponseEntity<ClawOpsRealCallPreflightResponse> preflightClawOpsRealCall(
            @RequestBody ClawOpsRealCallPreflightRequest request
    ) {
        return ResponseEntity.ok(clawOpsRealCallPreflightService.preflight(request));
    }
}
