package com.example.Capstone.dto.request;

import com.example.Capstone.domain.Report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateReportRequest(
        @NotNull Report.TargetType targetType,
        @NotNull Long targetId,
        @NotBlank String reason
) {}
