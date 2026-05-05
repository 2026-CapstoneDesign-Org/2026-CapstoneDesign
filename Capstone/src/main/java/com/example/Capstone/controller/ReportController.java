package com.example.Capstone.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Capstone.dto.request.CreateReportRequest;
import com.example.Capstone.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Report", description = "신고 API")
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "신고")
    @PostMapping
    public ResponseEntity<Void> report(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid CreateReportRequest request) {
        reportService.report(userId, request);
        return ResponseEntity.ok().build();
    }
}
