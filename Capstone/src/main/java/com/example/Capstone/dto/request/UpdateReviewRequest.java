package com.example.Capstone.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record UpdateReviewRequest(
        @NotBlank String content,
        List<String> imageUrls
) {}
