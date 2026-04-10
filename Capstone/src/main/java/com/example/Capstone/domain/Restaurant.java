package com.example.Capstone.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "restaurants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, length = 50)
    private String regionName;

    @Column(length = 50)
    private String regionCityName;

    @Column(length = 50)
    private String regionDistrictName;

    @Column(length = 50)
    private String regionCountyName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> regionFilterNames = new ArrayList<>();

    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 100, unique = true)
    private String pcmapPlaceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode menuJson;

    @Column
    private LocalDateTime menuUpdatedAt;

    @Column(nullable = false)
    private Boolean isHidden = false;

    @Column(nullable = false)
    private Boolean isDeleted = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RestaurantCategory> categories = new ArrayList<>();

    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RestaurantMenuItem> menuItems = new ArrayList<>();

    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RestaurantTag> restaurantTags = new ArrayList<>();

    public void updateInfo(
            String name,
            String address,
            String regionName,
            BigDecimal lat,
            BigDecimal lng,
            String imageUrl
    ) {
        this.name = name;
        this.address = address;
        this.regionName = regionName;
        this.lat = lat;
        this.lng = lng;
        this.imageUrl = imageUrl;
    }

    public void updateSeedRegionInfo(
            String regionName,
            String regionCityName,
            String regionDistrictName,
            String regionCountyName,
            List<String> regionFilterNames
    ) {
        this.regionName = regionName;
        this.regionCityName = regionCityName;
        this.regionDistrictName = regionDistrictName;
        this.regionCountyName = regionCountyName;
        this.regionFilterNames = regionFilterNames == null
                ? new ArrayList<>()
                : new ArrayList<>(regionFilterNames);
    }

    public void updateSeedMetadata(String pcmapPlaceId, JsonNode menuJson, LocalDateTime menuUpdatedAt) {
        this.pcmapPlaceId = pcmapPlaceId;
        this.menuJson = menuJson;
        this.menuUpdatedAt = menuUpdatedAt;
    }

    public void hide() {
        this.isHidden = true;
    }

    public void show() {
        this.isHidden = false;
    }

    public void delete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    @Builder
    private Restaurant(
            String name,
            String address,
            String regionName,
            String regionCityName,
            String regionDistrictName,
            String regionCountyName,
            List<String> regionFilterNames,
            BigDecimal lat,
            BigDecimal lng,
            String imageUrl,
            String pcmapPlaceId,
            JsonNode menuJson,
            LocalDateTime menuUpdatedAt
    ) {
        this.name = name;
        this.address = address;
        this.regionName = regionName;
        this.regionCityName = regionCityName;
        this.regionDistrictName = regionDistrictName;
        this.regionCountyName = regionCountyName;
        this.regionFilterNames = regionFilterNames == null
                ? new ArrayList<>()
                : new ArrayList<>(regionFilterNames);
        this.lat = lat;
        this.lng = lng;
        this.imageUrl = imageUrl;
        this.pcmapPlaceId = pcmapPlaceId;
        this.menuJson = menuJson;
        this.menuUpdatedAt = menuUpdatedAt;
        this.isHidden = false;
        this.isDeleted = false;
    }
}
