package com.example.Capstone.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "restaurant_tags",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_restaurant_tag_source",
                        columnNames = {"restaurant_id", "tag_id", "source_type"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class RestaurantTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @Column(nullable = false, length = 30)
    private String sourceType;

    @Column(length = 255)
    private String sourceText;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal weight;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(nullable = false)
    private Integer matchedMenuCount;

    @Column(nullable = false)
    private Boolean isPrimary = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private RestaurantTag(
            Restaurant restaurant,
            Tag tag,
            String sourceType,
            String sourceText,
            BigDecimal weight,
            BigDecimal confidence,
            Integer matchedMenuCount,
            Boolean isPrimary
    ) {
        this.restaurant = restaurant;
        this.tag = tag;
        this.sourceType = sourceType;
        this.sourceText = sourceText;
        this.weight = weight;
        this.confidence = confidence;
        this.matchedMenuCount = matchedMenuCount == null ? 0 : matchedMenuCount;
        this.isPrimary = isPrimary == null ? Boolean.FALSE : isPrimary;
    }
}
