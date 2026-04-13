package com.example.Capstone.recommendation.model.list;

public record ScoreVector(
        double taste,
        double value,
        double mood
) {
    public static ScoreVector zero() {
        return new ScoreVector(0.0, 0.0, 0.0);
    }
}
