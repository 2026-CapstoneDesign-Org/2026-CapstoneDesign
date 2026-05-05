package com.example.Capstone.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@Entity
@Table(name = "reliability_scores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ReliabilityScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private Double score = 42.0;

    @Column(nullable = false, length = 20)
    private String grade = "tier1";

    @Column(length = 30)
    private String honorTitle;

    @Column(length = 20)
    private String honorPeriod;

    @Column(nullable = false)
    private Double activityIndex = 0.0;

    @Column(nullable = false)
    private LocalDateTime lastActivityAt = LocalDateTime.now();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ReliabilityScore(User user) {
        this.user           = user;
        this.score          = 20.0;
        this.grade          = "tier1";
        this.honorTitle     = null;
        this.honorPeriod    = null;
        this.activityIndex  = 0.0;
        this.lastActivityAt = LocalDateTime.now();
    }

    public void updateScore(Double score) {
        this.score = Math.max(0.0, Math.min(100.0,
                Math.round(score * 10.0) / 10.0));
        this.grade = calcGrade(this.score);
    }

    public void addActivityIndex(Double value) {
        this.activityIndex += value;
        this.lastActivityAt = LocalDateTime.now();
    }

    public void updateHonorTitle(String honorTitle, String honorPeriod) {
        this.honorTitle  = honorTitle;
        this.honorPeriod = honorPeriod;
    }

    public void clearHonorTitle() {
        this.honorTitle  = null;
        this.honorPeriod = null;
    }

    private String calcGrade(Double score) {
        if (score >= 97.0) return "tier8";
        if (score >= 93.0) return "tier7";
        if (score >= 85.0) return "tier6";
        if (score >= 75.0) return "tier5";
        if (score >= 65.0) return "tier4";
        if (score >= 55.0) return "tier3";
        if (score >= 38.0) return "tier2";
        if (score >= 20.0) return "tier1";
        return "tier0";
    }
}
