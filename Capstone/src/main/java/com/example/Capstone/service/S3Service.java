package com.example.Capstone.service;

import com.example.Capstone.dto.response.PresignedUrlResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.presigned-url-expiration}")
    private Long expiration;

    public enum ImageType {
        PROFILE("profiles"),
        RESTAURANT("restaurants"),
        REVIEW("reviews");

        private final String folder;

        ImageType(String folder) { this.folder = folder; }
        public String getFolder() { return folder; }
    }

    // Presigned URL 발급
    public PresignedUrlResponse generatePresignedUrl(String filename, ImageType imageType) {
        String key = generateKey(filename, imageType);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(getContentType(filename))
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(r -> r
                .signatureDuration(Duration.ofSeconds(expiration))
                .putObjectRequest(putObjectRequest));

        String presignedUrl = presignedRequest.url().toString();
        String imageUrl     = generateImageUrl(key);

        log.info("Presigned URL 발급 - type: {}, key: {}", imageType, key);

        return new PresignedUrlResponse(presignedUrl, imageUrl);
    }

    // 고유 파일명 생성
    private String generateKey(String filename, ImageType imageType) {
        String uuid      = UUID.randomUUID().toString();
        String extension = getExtension(filename);
        return imageType.getFolder() + "/" + uuid + extension;
    }

    // 이미지 URL 생성
    private String generateImageUrl(String key) {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }

    // 확장자 추출
    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf(".");
        return dotIndex != -1 ? filename.substring(dotIndex) : "";
    }

    // Content-Type 추출
    private String getContentType(String filename) {
        String extension = getExtension(filename).toLowerCase();
        return switch (extension) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png"          -> "image/png";
            case ".gif"          -> "image/gif";
            case ".webp"         -> "image/webp";
            default              -> "application/octet-stream";
        };
    }
}
