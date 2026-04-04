package com.example.Capstone.controller;

import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;


@RestController
@Tag(name = "Test", description = "test")
public class PingPongController {

    @Operation(summary = "test", description = "test")
    @ApiResponse(responseCode = "200", description = "test")
    @ApiResponse(responseCode = "404", description = "test")
    @GetMapping("/ping")
    public String getMethodName() {
        return new String("Pong");
    }
    
}
