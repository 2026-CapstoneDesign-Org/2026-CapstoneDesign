package com.example.Capstone.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRestaurantRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 500) String address,
        @NotBlank @Size(max = 255) String regionName,
        @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") BigDecimal lat,
        @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") BigDecimal lng,
        @Size(max = 2048) String imageUrl
) {}
