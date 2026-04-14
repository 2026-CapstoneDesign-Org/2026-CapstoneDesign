package com.example.Capstone.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.example.Capstone.domain.base.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "restaurant_tags",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_restaurant_tag",
                        columnNames = {"restaurant_id", "tag_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantTag extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @Column(nullable = false)
    private Integer matchedMenuCount;

    @Column(nullable = false)
    private Boolean isPrimary = false;

    @Builder
    private RestaurantTag(
            Restaurant restaurant,
            Tag tag,
            Integer matchedMenuCount,
            Boolean isPrimary
    ) {
        this.restaurant = restaurant;
        this.tag = tag;
        this.matchedMenuCount = matchedMenuCount == null ? 0 : matchedMenuCount;
        this.isPrimary = isPrimary == null ? Boolean.FALSE : isPrimary;
    }
}
