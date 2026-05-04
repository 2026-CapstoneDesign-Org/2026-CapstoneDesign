package com.example.Capstone.controller;

import com.example.Capstone.dto.response.ReliabilityScoreResponse;
import com.example.Capstone.service.ReliabilityScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Reliability", description = "신뢰도 API")
public class ReliabilityController {

    private final ReliabilityScoreService scoreService;

    @Operation(summary = "신뢰도 점수 조회")
    @GetMapping("/{id}/reliability")
    public ResponseEntity<ReliabilityScoreResponse> getScore(@PathVariable Long id) {
        return ResponseEntity.ok(scoreService.getScore(id));
    }
}
