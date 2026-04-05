package com.example.Capstone.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record AddRestaurantRequest(
        @NotNull Long restaurantId,
        @NotNull @DecimalMin("1.0") @DecimalMax("10.0") BigDecimal tasteScore,
        @NotNull @DecimalMin("1.0") @DecimalMax("10.0") BigDecimal valueScore,
        @NotNull @DecimalMin("1.0") @DecimalMax("10.0") BigDecimal moodScore
) {}
