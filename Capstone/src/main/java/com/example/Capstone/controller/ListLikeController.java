package com.example.Capstone.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Capstone.service.ListLikeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/lists")
@RequiredArgsConstructor
@Tag(name = "ListLike", description = "리스트 좋아요 API")
public class ListLikeController {

    private final ListLikeService listLikeService;

    @Operation(summary = "리스트 좋아요")
    @PostMapping("/{id}/like")
    public ResponseEntity<Void> like(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        listLikeService.like(userId, id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "리스트 좋아요 취소")
    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> unlike(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        listLikeService.unlike(userId, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "리스트 좋아요 수")
    @GetMapping("/{id}/like/count")
    public ResponseEntity<Long> getLikeCount(@PathVariable Long id) {
        return ResponseEntity.ok(listLikeService.getLikeCount(id));
    }
}
