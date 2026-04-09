package com.example.Capstone.controller;

import com.example.Capstone.dto.response.FollowCountResponse;
import com.example.Capstone.dto.response.FollowUserResponse;
import com.example.Capstone.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Follow", description = "팔로우 API")
public class FollowController {

    private final FollowService followService;

    @Operation(summary = "팔로우")
    @PostMapping("/{id}/follow")
    public ResponseEntity<Void> follow(
            @AuthenticationPrincipal Long followerId,
            @PathVariable Long id) {
        followService.follow(followerId, id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "언팔로우")
    @DeleteMapping("/{id}/follow")
    public ResponseEntity<Void> unfollow(
            @AuthenticationPrincipal Long followerId,
            @PathVariable Long id) {
        followService.unfollow(followerId, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "팔로워 목록")
    @GetMapping("/{id}/followers")
    public ResponseEntity<List<FollowUserResponse>> getFollowers(
            @PathVariable Long id) {
        return ResponseEntity.ok(followService.getFollowers(id));
    }

    @Operation(summary = "팔로잉 목록")
    @GetMapping("/{id}/followings")
    public ResponseEntity<List<FollowUserResponse>> getFollowings(
            @PathVariable Long id) {
        return ResponseEntity.ok(followService.getFollowings(id));
    }

    @Operation(summary = "팔로워 / 팔로잉 수")
    @GetMapping("/{id}/follow/count")
    public ResponseEntity<FollowCountResponse> getFollowCount(
            @PathVariable Long id) {
        return ResponseEntity.ok(followService.getFollowCount(id));
    }

    @Operation(summary = "팔로우 여부 확인")
    @GetMapping("/{id}/follow/status")
    public ResponseEntity<Boolean> isFollowing(
            @AuthenticationPrincipal Long followerId,
            @PathVariable Long id) {
        return ResponseEntity.ok(followService.isFollowing(followerId, id));
    }
}