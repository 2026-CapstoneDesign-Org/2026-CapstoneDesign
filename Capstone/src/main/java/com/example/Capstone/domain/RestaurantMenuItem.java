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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "restaurant_menu_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class RestaurantMenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(length = 100)
    private String sourceMenuId;

    @Column(nullable = false)
    private Integer displayOrder;

    @Column(nullable = false, length = 255)
    private String menuName;

    @Column(nullable = false, length = 255)
    private String normalizedMenuName;

    @Column(length = 100)
    private String menuTagKey;

    @Column(length = 50)
    private String priceText;

    @Column(precision = 12, scale = 2)
    private BigDecimal priceValue;

    @Column(columnDefinition = "text")
    private String description;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private RestaurantMenuItem(
            Restaurant restaurant,
            String sourceMenuId,
            Integer displayOrder,
            String menuName,
            String normalizedMenuName,
            String menuTagKey,
            String priceText,
            BigDecimal priceValue,
            String description
    ) {
        this.restaurant = restaurant;
        this.sourceMenuId = sourceMenuId;
        this.displayOrder = displayOrder == null ? 0 : displayOrder;
        this.menuName = menuName;
        this.normalizedMenuName = normalizedMenuName;
        this.menuTagKey = menuTagKey;
        this.priceText = priceText;
        this.priceValue = priceValue;
        this.description = description;
    }
}
