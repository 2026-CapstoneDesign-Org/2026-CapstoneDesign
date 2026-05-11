package com.example.Capstone.dto.response;

public record PresignedUrlResponse(
        String presignedUrl,    // S3 에 업로드할 URL
        String imageUrl         // 업로드 후 접근할 이미지 URL
) {}
