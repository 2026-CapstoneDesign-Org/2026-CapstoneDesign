package com.example.Capstone.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.example.Capstone.domain.base.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "list_restaurants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListRestaurant extends BaseTimeEntity {

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

    // 가중합 자동 계산 ((taste 60%, value 20%, mood 20%) * 10)
    private BigDecimal calcAutoScore(BigDecimal taste, BigDecimal value, BigDecimal mood) {
        return taste.multiply(new BigDecimal("0.6"))
                .add(value.multiply(new BigDecimal("0.2")))
                .add(mood.multiply(new BigDecimal("0.2")))
                .multiply(new BigDecimal("10"))
                .setScale(1, RoundingMode.HALF_UP);
    }
}
