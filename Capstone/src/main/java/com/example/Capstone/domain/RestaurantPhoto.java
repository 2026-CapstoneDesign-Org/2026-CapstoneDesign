package com.example.Capstone.domain;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "restaurant_photos",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_restaurant_photo_url",
                        columnNames = {"restaurant_id", "image_url"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantPhoto extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, length = 500)
    private String imageUrl;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(nullable = false)
    private Integer displayOrder;

    @Builder
    private RestaurantPhoto(
            Restaurant restaurant,
            String imageUrl,
            String source,
            Integer displayOrder
    ) {
        this.restaurant = restaurant;
        this.imageUrl = imageUrl;
        this.source = source;
        this.displayOrder = displayOrder == null ? 0 : displayOrder;
    }
}
