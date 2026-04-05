package com.example.Capstone.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record UpdateCategoryRequest(
    @NotNull List<String> categories
) {}
