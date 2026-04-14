package com.example.Capstone.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record UpdateCategoryRequest(
    @NotNull @NotEmpty List<@NotBlank String> categories
) {}
