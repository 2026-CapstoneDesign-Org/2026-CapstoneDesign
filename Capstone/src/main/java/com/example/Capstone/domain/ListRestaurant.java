package com.example.Capstone.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "list_restaurants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ListRestaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_id", nullable = false)
    private UserList userList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal tasteScore;

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal valueScore;

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal moodScore;

    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal autoScore;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ListRestaurant(UserList userList, Restaurant restaurant,
                           BigDecimal tasteScore, BigDecimal valueScore,
                           BigDecimal moodScore) {
        this.userList    = userList;
        this.restaurant  = restaurant;
        this.tasteScore  = tasteScore;
        this.valueScore  = valueScore;
        this.moodScore   = moodScore;
        this.autoScore   = calcAutoScore(tasteScore, valueScore, moodScore);
    }

    public void updateScore(BigDecimal tasteScore, BigDecimal valueScore, BigDecimal moodScore) {
        this.tasteScore = tasteScore;
        this.valueScore = valueScore;
        this.moodScore  = moodScore;
        this.autoScore  = calcAutoScore(tasteScore, valueScore, moodScore);
    }

    // 가중합 자동 계산 (taste 40%, value 30%, mood 30%)
    private BigDecimal calcAutoScore(BigDecimal taste, BigDecimal value, BigDecimal mood) {
        return taste.multiply(new BigDecimal("4.0"))
                .add(value.multiply(new BigDecimal("3.0")))
                .add(mood.multiply(new BigDecimal("3.0")));
    }
}
