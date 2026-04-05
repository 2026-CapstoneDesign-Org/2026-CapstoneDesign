package com.example.Capstone.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

public record UpdateRestaurantRequest(
        @NotBlank String name,
        @NotBlank String address,
        @NotBlank String regionName,
        BigDecimal lat,
        BigDecimal lng,
        String imageUrl
) {}
