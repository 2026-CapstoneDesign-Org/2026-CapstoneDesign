package com.example.Capstone.client;

import com.example.Capstone.dto.request.GeminiRequest;
import com.example.Capstone.dto.response.GeminiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class GeminiClient {

    private final WebClient webClient;
    private final String apiKey;

    public GeminiClient(
            @Value("${gemini.api.url}") String apiUrl,
            @Value("${gemini.api.key}") String apiKey) {
        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .build();
        this.apiKey = apiKey;
    }

    public String generate(String prompt) {
        try {
            GeminiResponse response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("key", apiKey)
                            .build())
                    .header("Content-Type", "application/json")
                    .bodyValue(GeminiRequest.of(prompt))
                    .retrieve()
                    .bodyToMono(GeminiResponse.class)
                    .block();

            if (response == null) {
                log.error("Gemini API 응답 없음");
                return null;
            }

            return response.extractText();

        } catch (Exception e) {
            log.error("Gemini API 호출 실패: {}", e.getMessage());
            return null;
        }
    }
}
