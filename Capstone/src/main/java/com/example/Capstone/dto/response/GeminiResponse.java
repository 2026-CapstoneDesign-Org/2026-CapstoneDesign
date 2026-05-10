package com.example.Capstone.dto.response;

import java.util.List;

public record GeminiResponse(List<Candidate> candidates) {

    public record Candidate(Content content) {}
    public record Content(List<Part> parts) {}
    public record Part(String text) {}

    public String extractText() {
        return candidates.stream()
                .findFirst()
                .map(c -> c.content().parts())
                .flatMap(parts -> parts.stream().findFirst())
                .map(Part::text)
                .orElse("");
    }
}
