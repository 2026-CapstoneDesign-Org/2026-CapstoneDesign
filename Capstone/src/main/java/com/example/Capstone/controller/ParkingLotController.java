package com.example.Capstone.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.Capstone.config.SwaggerConfig;
import com.example.Capstone.dto.request.CreateParkingLotRequest;
import com.example.Capstone.dto.request.UpdateParkingLotRequest;
import com.example.Capstone.dto.response.ParkingLotResponse;
import com.example.Capstone.service.ParkingLotService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/parking-lots")
@RequiredArgsConstructor
@Tag(name = "ParkingLotCrud", description = "Parking lot CRUD APIs.")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
public class ParkingLotController {

    private final ParkingLotService parkingLotService;

    @Operation(summary = "List parking lots")
    @GetMapping
    public ResponseEntity<List<ParkingLotResponse>> getParkingLots(
            @Parameter(description = "Optional parking lot division filter.", example = "공영")
            @RequestParam(required = false) String parkingLotDivision,
            @Parameter(description = "Maximum number of rows. Defaults to 50, max 200.", example = "50")
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(parkingLotService.getParkingLots(parkingLotDivision, limit));
    }

    @Operation(summary = "Get parking lot")
    @GetMapping("/{parkingLotId}")
    public ResponseEntity<ParkingLotResponse> getParkingLot(
            @PathVariable Long parkingLotId
    ) {
        return ResponseEntity.ok(parkingLotService.getParkingLot(parkingLotId));
    }

    @Operation(summary = "Create parking lot")
    @PostMapping
    public ResponseEntity<ParkingLotResponse> createParkingLot(
            @RequestBody CreateParkingLotRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(parkingLotService.createParkingLot(request));
    }

    @Operation(summary = "Update parking lot")
    @PatchMapping("/{parkingLotId}")
    public ResponseEntity<ParkingLotResponse> updateParkingLot(
            @PathVariable Long parkingLotId,
            @RequestBody UpdateParkingLotRequest request
    ) {
        return ResponseEntity.ok(parkingLotService.updateParkingLot(parkingLotId, request));
    }

    @Operation(summary = "Delete parking lot")
    @DeleteMapping("/{parkingLotId}")
    public ResponseEntity<Void> deleteParkingLot(
            @PathVariable Long parkingLotId
    ) {
        parkingLotService.deleteParkingLot(parkingLotId);
        return ResponseEntity.noContent().build();
    }
}
