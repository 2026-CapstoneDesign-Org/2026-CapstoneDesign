package com.example.Capstone.external;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.example.Capstone.client.GeminiClient;
import com.example.Capstone.client.GyeonggiParkingPlaceClientImpl;
import com.example.Capstone.client.NaverLocalSearchClientImpl;
import com.example.Capstone.client.PcmapPlaceDetailClientImpl;
import com.example.Capstone.client.PcmapSearchClient;
import com.example.Capstone.client.PcmapSearchClientImpl;
import com.example.Capstone.client.SeoulCityDataParkingClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@Tag("external")
class ExternalApiSmokeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    @Test
    void requiredExternalApiEnvironmentIsPresent() {
        List<String> missing = new ArrayList<>();
        require(missing, "JWT_SECRET");
        require(missing, "GOOGLE_CLIENT_ID");
        require(missing, "GOOGLE_CLIENT_SECRET");
        require(missing, "KAKAO_CLIENT_ID");
        require(missing, "KAKAO_CLIENT_SECRET");
        require(missing, "NAVER_CLIENT_ID");
        require(missing, "NAVER_CLIENT_SECRET");
        requireOneOf(missing, "NAVER_SEARCH_CLIENT_ID or NAVER_CLIENT_ID", "NAVER_SEARCH_CLIENT_ID", "NAVER_CLIENT_ID");
        requireOneOf(missing, "NAVER_SEARCH_CLIENT_SECRET or NAVER_CLIENT_SECRET", "NAVER_SEARCH_CLIENT_SECRET", "NAVER_CLIENT_SECRET");
        require(missing, "GG_PARKING_PLACE_API_KEY");
        require(missing, "SEOUL_CITYDATA_API_KEY");
        require(missing, "GEMINI_API_KEY");
        require(missing, "AWS_S3_ACCESS_KEY");
        require(missing, "AWS_S3_SECRET_KEY");
        require(missing, "AWS_S3_BUCKET");

        assertTrue(missing.isEmpty(), "Missing external API environment variables: " + String.join(", ", missing));
    }

    @Test
    void naverLocalSearchApiReturnsRestaurantCandidate() {
        NaverLocalSearchClientImpl client = new NaverLocalSearchClientImpl(
                OBJECT_MAPPER,
                true,
                requiredFirst("NAVER_SEARCH_CLIENT_ID", "NAVER_CLIENT_ID"),
                requiredFirst("NAVER_SEARCH_CLIENT_SECRET", "NAVER_CLIENT_SECRET")
        );

        var match = client.findBestRestaurantMatch(
                "\uC2A4\uD0C0\uBC85\uC2A4 \uC11C\uC6B8\uC5ED\uC0AC\uC810",
                "\uC11C\uC6B8 \uC6A9\uC0B0\uAD6C \uD55C\uAC15\uB300\uB85C 405"
        );

        assertTrue(match.isPresent(), "Naver Local Search returned no verified restaurant candidate");
        assertFalse(match.get().title().isBlank());
    }

    @Test
    void naverPcmapFallbackReturnsSearchAndDetail() {
        PcmapSearchClient searchClient = new PcmapSearchClientImpl(
                OBJECT_MAPPER,
                true,
                "126.970626",
                "37.554678",
                3,
                3000,
                60000,
                0,
                envOrDefault("NAVER_COOKIE", "")
        );
        PcmapPlaceDetailClientImpl detailClient = new PcmapPlaceDetailClientImpl(
                OBJECT_MAPPER,
                true,
                3000,
                envOrDefault("NAVER_COOKIE", "")
        );

        var candidates = searchClient.searchRestaurants("\uC11C\uC6B8\uC5ED \uB9DB\uC9D1", 3);

        assertFalse(candidates.isEmpty(), "Naver Pcmap search returned no candidates");
        var detail = detailClient.fetchRestaurantDetail(candidates.get(0).placeId());
        assertTrue(detail.isPresent(), "Naver Pcmap detail returned no data for placeId=" + candidates.get(0).placeId());
    }

    @Test
    void gyeonggiParkingPlaceApiReturnsRows() {
        GyeonggiParkingPlaceClientImpl client = new GyeonggiParkingPlaceClientImpl(
                OBJECT_MAPPER,
                true,
                "https://openapi.gg.go.kr/ParkingPlace",
                requiredEnv("GG_PARKING_PLACE_API_KEY"),
                10,
                1,
                0
        );

        var places = client.fetchAllParkingPlaces();

        assertFalse(places.isEmpty(), "Gyeonggi ParkingPlace API returned no rows");
        assertTrue(places.stream().anyMatch(place -> place.parkingLotName() != null && !place.parkingLotName().isBlank()));
    }

    @Test
    void seoulCityDataApiReturnsRealtimeParkingRows() {
        SeoulCityDataParkingClient client = new SeoulCityDataParkingClient(
                true,
                "http://openapi.seoul.go.kr:8088",
                requiredEnv("SEOUL_CITYDATA_API_KEY"),
                0
        );

        var places = client.fetchRealtimeParkingPlaces(
                new BigDecimal("37.57340269"),
                new BigDecimal("126.97588429")
        );

        assertFalse(places.isEmpty(), "Seoul CityData API returned no realtime parking rows");
        assertTrue(places.stream().anyMatch(place -> place.currentParkingCount() != null));
    }

    @Test
    void geminiApiGeneratesText() {
        GeminiClient client = new GeminiClient(
                envOrDefault("GEMINI_API_URL", "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"),
                requiredEnv("GEMINI_API_KEY")
        );

        String response = client.generate("Reply with exactly this text: capstone-ok");

        assertTrue(response != null && !response.isBlank(), "Gemini API returned no text");
    }

    @Test
    void s3PresignedUrlAcceptsPutWhenEnabled() throws Exception {
        Assumptions.assumeTrue(
                Boolean.parseBoolean(envOrDefault("AWS_S3_SMOKE_WRITE", "false")),
                "Set AWS_S3_SMOKE_WRITE=true to run the S3 write smoke test"
        );

        String region = envOrDefault("AWS_S3_REGION", "ap-northeast-2");
        String bucket = requiredEnv("AWS_S3_BUCKET");
        String key = "smoke-tests/capstone-" + UUID.randomUUID() + ".txt";
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(requiredEnv("AWS_S3_ACCESS_KEY"), requiredEnv("AWS_S3_SECRET_KEY"))
        );

        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build()) {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("text/plain")
                    .build();
            PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(request -> request
                    .signatureDuration(Duration.ofMinutes(5))
                    .putObjectRequest(putObjectRequest)
            );

            HttpRequest request = HttpRequest.newBuilder(URI.create(presignedRequest.url().toString()))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "text/plain")
                    .PUT(HttpRequest.BodyPublishers.ofString("capstone external api smoke test"))
                    .build();
            HttpResponse<Void> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.discarding());

            assertTrue(response.statusCode() >= 200 && response.statusCode() < 300,
                    "S3 presigned PUT failed with status " + response.statusCode());
        } finally {
            try (S3Client s3Client = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(credentialsProvider)
                    .build()) {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build());
            } catch (Exception ignored) {
            }
        }
    }

    private static void require(List<String> missing, String name) {
        if (isBlank(System.getenv(name))) {
            missing.add(name);
        }
    }

    private static void requireOneOf(List<String> missing, String label, String... names) {
        for (String name : names) {
            if (!isBlank(System.getenv(name))) {
                return;
            }
        }
        missing.add(label);
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        Assumptions.assumeTrue(!isBlank(value), "Missing required environment variable: " + name);
        return value;
    }

    private static String requiredFirst(String... names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (!isBlank(value)) {
                return value;
            }
        }
        Assumptions.assumeTrue(false, "Missing required environment variable. Expected one of: " + String.join(", ", names));
        return "";
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return isBlank(value) ? defaultValue : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
