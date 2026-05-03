package com.example.Capstone.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Capstone.dto.request.CreateReviewRequest;
import com.example.Capstone.dto.request.ReviewVoteRequest;
import com.example.Capstone.dto.request.UpdateReviewRequest;
import com.example.Capstone.dto.response.ReviewResponse;
import com.example.Capstone.service.ReviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Review", description = "리뷰 API")
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "리뷰 작성")
    @PostMapping("/restaurants/{id}/reviews")
    public ResponseEntity<ReviewResponse> createReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestBody @Valid CreateReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.createReview(userId, id, request));
    }

    @Operation(summary = "리뷰 목록")
    @GetMapping("/restaurants/{id}/reviews")
    public ResponseEntity<List<ReviewResponse>> getReviews(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.getReviews(id));
    }

    @Operation(summary = "리뷰 수정")
    @PatchMapping("/reviews/{id}")
    public ResponseEntity<Void> updateReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestBody @Valid UpdateReviewRequest request) {
        reviewService.updateReview(userId, id, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "리뷰 삭제")
    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> deleteReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        reviewService.deleteReview(userId, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "리뷰 평가")
    @PostMapping("/reviews/{id}/vote")
    public ResponseEntity<Void> vote(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestBody @Valid ReviewVoteRequest request) {
        reviewService.vote(userId, id, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "리뷰 평가 취소")
    @DeleteMapping("/reviews/{id}/vote")
    public ResponseEntity<Void> cancelVote(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        reviewService.cancelVote(userId, id);
        return ResponseEntity.noContent().build();
    }
}
