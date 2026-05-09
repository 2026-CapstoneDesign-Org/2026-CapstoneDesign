package com.example.Capstone.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_summaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ReviewSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false, unique = true)
    private Restaurant restaurant;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String positives;       // JSON 배열 문자열

    @Column(nullable = false, columnDefinition = "TEXT")
    private String negatives;       // JSON 배열 문자열

    @Column(nullable = false, length = 10)
    private String sentiment;

    @Column(nullable = false)
    private Integer reviewCount;

    @Column(nullable = false)
    private Boolean isOutdated = false;  // true 면 재생성 필요

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ReviewSummary(Restaurant restaurant, String summary, String positives,
                          String negatives, String sentiment, Integer reviewCount) {
        this.restaurant  = restaurant;
        this.summary     = summary;
        this.positives   = positives;
        this.negatives   = negatives;
        this.sentiment   = sentiment;
        this.reviewCount = reviewCount;
        this.isOutdated  = false;
        this.createdAt   = LocalDateTime.now();
        this.updatedAt   = LocalDateTime.now();
    }

    public void update(String summary, String positives, String negatives,
                       String sentiment, Integer reviewCount) {
        this.summary     = summary;
        this.positives   = positives;
        this.negatives   = negatives;
        this.sentiment   = sentiment;
        this.reviewCount = reviewCount;
        this.isOutdated  = false;
        this.updatedAt   = LocalDateTime.now();
    }

    // 캐시 무효화
    public void invalidate() {
        this.isOutdated = true;
    }
}
