package com.example.Capstone.controller;

import com.example.Capstone.dto.response.PresignedUrlResponse;
import com.example.Capstone.service.S3Service;
import com.example.Capstone.service.S3Service.ImageType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
@Tag(name = "Upload", description = "이미지 업로드 API")
public class UploadController {

    private final S3Service s3Service;

    @Operation(
            summary = "이미지 업로드용 Presigned URL 발급",
            description = "S3 에 이미지를 직접 업로드하기 위한 Presigned URL 을 발급합니다."
    )
    @GetMapping("/presigned")
    public ResponseEntity<PresignedUrlResponse> getPresignedUrl(
            @RequestParam String filename,
            @RequestParam ImageType type) {
        return ResponseEntity.ok(s3Service.generatePresignedUrl(filename,type));
    }
}
