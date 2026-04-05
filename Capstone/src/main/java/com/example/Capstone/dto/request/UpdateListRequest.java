package com.example.Capstone.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateListRequest(
    @NotBlank String title,
    String description
) {}
