package com.example.Capstone.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.Capstone.dto.response.SearchResponse;
import com.example.Capstone.service.SearchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Unified search API for restaurant, user, and region entry points.")
@SecurityRequirement(name = "Bearer Authentication")
public class SearchController {

    private final SearchService searchService;

    @Operation(
            summary = "Search restaurants, users, and regions",
            description = "Interprets a single query across restaurant, user, and region search flows. "
                    + "Region plus category, menu, or tag combinations are supported. "
                    + "When internal restaurant matches are insufficient, the service may merge external "
                    + "NAVER Pcmap fallback results."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Search completed.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SearchResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "The query is blank or malformed."),
            @ApiResponse(responseCode = "401", description = "Authentication required.")
    })
    @GetMapping
    public ResponseEntity<SearchResponse> search(
            @Parameter(
                    description = "Search query. Examples: restaurant name, region plus menu, or user nickname.",
                    example = "역북 돈까스"
            )
            @RequestParam String query
    ) {
        return ResponseEntity.ok(searchService.search(query));
    }
}
