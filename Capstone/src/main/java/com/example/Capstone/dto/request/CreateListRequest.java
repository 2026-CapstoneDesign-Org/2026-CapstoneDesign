package com.example.Capstone.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateListRequest(
        @NotBlank String title,
        String description,
        @NotBlank String regionName
) {}
