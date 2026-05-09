package com.example.Capstone.service;

import com.example.Capstone.client.GeminiClient;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.Review;
import com.example.Capstone.domain.ReviewSummary;
import com.example.Capstone.dto.response.ReviewSummaryResponse;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.ReviewRepository;
import com.example.Capstone.repository.ReviewSummaryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReviewSummaryService {

    private final ReviewRepository reviewRepository;
    private final RestaurantRepository restaurantRepository;
    private final ReviewSummaryRepository reviewSummaryRepository;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    private static final int MIN_REVIEW_COUNT = 3;
    private static final int MAX_REVIEW_COUNT = 50;

    public ReviewSummaryResponse summarize(Long restaurantId) {
        Restaurant restaurant = restaurantRepository
                .findByIdAndIsDeletedFalseAndIsHiddenFalse(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("식당을 찾을 수 없습니다."));

        List<Review> reviews = reviewRepository
                .findAllByRestaurantIdAndIsDeletedFalseAndIsHiddenFalse(restaurantId);

        if (reviews.size() < MIN_REVIEW_COUNT) {
            throw new BusinessException(
                    "리뷰가 " + MIN_REVIEW_COUNT + "개 이상일 때 요약이 가능합니다.",
                    HttpStatus.BAD_REQUEST);
        }

        // 캐시 조회
        Optional<ReviewSummary> cached = reviewSummaryRepository.findByRestaurantId(restaurantId);

        // 캐시 있고 유효하면 바로 반환
        if (cached.isPresent() && !cached.get().getIsOutdated()) {
            log.info("캐시 히트 - restaurantId: {}", restaurantId);
            return toResponse(cached.get(), restaurant);
        }

        // 캐시 없거나 무효화된 경우 AI API 호출
        log.info("캐시 미스 - restaurantId: {}, AI API 호출", restaurantId);
        return generateAndCache(restaurant, reviews, cached.orElse(null));
    }

    // 캐시 무효화 (리뷰 추가 / 삭제 / 수정 시 호출)
    @Transactional
    public void invalidateCache(Long restaurantId) {
        reviewSummaryRepository.findByRestaurantId(restaurantId)
                .ifPresent(summary -> {
                    summary.invalidate();
                    log.info("캐시 무효화 - restaurantId: {}", restaurantId);
                });
    }

    @Transactional
    protected ReviewSummaryResponse generateAndCache(Restaurant restaurant,
                                                      List<Review> reviews,
                                                      ReviewSummary existingSummary) {
        List<Review> targetReviews = reviews.size() > MAX_REVIEW_COUNT
                ? reviews.subList(0, MAX_REVIEW_COUNT)
                : reviews;

        String prompt    = buildPrompt(restaurant.getName(), targetReviews);
        String aiResponse = geminiClient.generate(prompt);

        if (aiResponse == null) {
            throw new BusinessException("AI 요약 생성에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        ParsedSummary parsed = parseResponse(aiResponse);

        try {
            String positivesJson = objectMapper.writeValueAsString(parsed.positives());
            String negativesJson = objectMapper.writeValueAsString(parsed.negatives());

            if (existingSummary != null) {
                // 기존 캐시 업데이트
                existingSummary.update(
                        parsed.summary(),
                        positivesJson,
                        negativesJson,
                        parsed.sentiment(),
                        reviews.size()
                );
            } else {
                // 새 캐시 저장
                reviewSummaryRepository.save(ReviewSummary.builder()
                        .restaurant(restaurant)
                        .summary(parsed.summary())
                        .positives(positivesJson)
                        .negatives(negativesJson)
                        .sentiment(parsed.sentiment())
                        .reviewCount(reviews.size())
                        .build());
            }

        } catch (JsonProcessingException e) {
            log.error("캐시 저장 실패", e);
        }

        return new ReviewSummaryResponse(
                restaurant.getId(),
                restaurant.getName(),
                parsed.summary(),
                parsed.positives(),
                parsed.negatives(),
                parsed.sentiment(),
                reviews.size()
        );
    }

    private String buildPrompt(String restaurantName, List<Review> reviews) {
        String reviewTexts = reviews.stream()
                .map(r -> "- " + r.getContent())
                .collect(Collectors.joining("\n"));

        return """
                다음은 '%s' 식당에 대한 고객 리뷰들입니다.
                리뷰를 분석하여 아래 JSON 형식으로만 응답해주세요. JSON 외 다른 텍스트는 포함하지 마세요.
                
                리뷰 목록:
                %s
                
                응답 형식:
                {
                  "summary": "전체 리뷰를 2~3문장으로 요약",
                  "positives": ["긍정적인 키워드1", "긍정적인 키워드2", "긍정적인 키워드3"],
                  "negatives": ["부정적인 키워드1", "부정적인 키워드2"],
                  "sentiment": "긍정 또는 부정 또는 중립"
                }
                """.formatted(restaurantName, reviewTexts);
    }

    private ParsedSummary parseResponse(String aiResponse) {
        try {
            String cleaned = aiResponse
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            JsonNode node = objectMapper.readTree(cleaned);

            String summary   = node.get("summary").asText();
            String sentiment = node.get("sentiment").asText();

            List<String> positives = new ArrayList<>();
            List<String> negatives = new ArrayList<>();

            node.get("positives").forEach(n -> positives.add(n.asText()));
            node.get("negatives").forEach(n -> negatives.add(n.asText()));

            return new ParsedSummary(summary, positives, negatives, sentiment);

        } catch (Exception e) {
            log.error("Gemini 응답 파싱 실패: {}", aiResponse, e);
            throw new BusinessException("AI 응답 파싱에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ReviewSummaryResponse toResponse(ReviewSummary cached, Restaurant restaurant) {
        try {
            List<String> positives = objectMapper.readValue(
                    cached.getPositives(), new TypeReference<>() {});
            List<String> negatives = objectMapper.readValue(
                    cached.getNegatives(), new TypeReference<>() {});

            return new ReviewSummaryResponse(
                    restaurant.getId(),
                    restaurant.getName(),
                    cached.getSummary(),
                    positives,
                    negatives,
                    cached.getSentiment(),
                    cached.getReviewCount()
            );
        } catch (JsonProcessingException e) {
            log.error("캐시 파싱 실패", e);
            throw new BusinessException("캐시 데이터 파싱에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 내부 파싱 결과 전달용
    private record ParsedSummary(
            String summary,
            List<String> positives,
            List<String> negatives,
            String sentiment
    ) {}
}
