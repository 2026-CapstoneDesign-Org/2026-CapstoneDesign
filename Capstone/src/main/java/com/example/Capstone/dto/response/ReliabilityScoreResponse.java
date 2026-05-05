package com.example.Capstone.dto.response;

import com.example.Capstone.domain.ReliabilityScore;
import java.time.LocalDateTime;

public record ReliabilityScoreResponse(
        Long userId,
        String nickname,
        Double score,
        String grade,
        String honorTitle,
        String honorPeriod,
        LocalDateTime updatedAt
) {
    public static ReliabilityScoreResponse from(ReliabilityScore rs) {
        return new ReliabilityScoreResponse(
                rs.getUser().getId(),
                rs.getUser().getNickname(),
                rs.getScore(),
                rs.getGrade(),
                rs.getHonorTitle(),
                rs.getHonorPeriod(),
                rs.getUpdatedAt()
        );
    }
}
