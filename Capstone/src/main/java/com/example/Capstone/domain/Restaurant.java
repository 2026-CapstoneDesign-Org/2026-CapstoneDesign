package com.example.Capstone.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import com.example.Capstone.domain.base.BaseTimeEntity;
import com.example.Capstone.domain.converter.StringListJsonConverter;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "restaurants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Restaurant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(length = 255)
    private String roadAddress;

    @Column(length = 100)
    private String categoryName;

    @Column(length = 50)
    private String primaryCategoryName;

    @Column(nullable = false, length = 50)
    private String regionName;

    @Column(length = 50)
    private String regionCityName;

    @Column(length = 50)
    private String regionDistrictName;

    @Column(length = 50)
    private String regionCountyName;

    @Column(length = 50)
    private String regionTownName;

    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "text")
    private List<String> regionFilterNames = new ArrayList<>();

    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 50)
    private String phoneNumber;

    @Column(columnDefinition = "text")
    private String businessHoursRaw;

    @Column(length = 100, unique = true)
    private String pcmapPlaceId;

    @Column
    private LocalDateTime menuUpdatedAt;

    @Column(nullable = false)
    private Boolean isHidden = false;

    @Column(nullable = false)
    private Boolean isDeleted = false;

    @Column
    private LocalDateTime deletedAt;

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

    public void updateSeedAddress(String address, String roadAddress) {
        this.address = address;
        this.roadAddress = roadAddress;
    }

    public void updateCategoryName(String categoryName) {
        this.categoryName = categoryName == null || categoryName.isBlank()
                ? null
                : categoryName.trim();
    }

    public void updatePrimaryCategoryName(String primaryCategoryName) {
        this.primaryCategoryName = primaryCategoryName == null || primaryCategoryName.isBlank()
                ? null
                : primaryCategoryName.trim();
    }

    public void updateContactAndBusinessHours(
            String phoneNumber,
            String businessHoursRaw
    ) {
        this.phoneNumber = phoneNumber == null || phoneNumber.isBlank()
                ? null
                : phoneNumber.trim();
        this.businessHoursRaw = businessHoursRaw == null || businessHoursRaw.isBlank()
                ? null
                : businessHoursRaw.trim();
    }

    public void updateSeedRegionInfo(
            String regionName,
            String regionCityName,
            String regionDistrictName,
            String regionCountyName,
            String regionTownName,
            List<String> regionFilterNames
    ) {
        this.regionName = regionName;
        this.regionCityName = regionCityName;
        this.regionDistrictName = regionDistrictName;
        this.regionCountyName = regionCountyName;
        this.regionTownName = regionTownName;
        this.regionFilterNames = regionFilterNames == null
                ? new ArrayList<>()
                : new ArrayList<>(regionFilterNames);
    }

    public void updateSeedMetadata(String pcmapPlaceId, LocalDateTime menuUpdatedAt) {
        this.pcmapPlaceId = pcmapPlaceId;
        this.menuUpdatedAt = menuUpdatedAt;
    }

    public List<String> getCategoryNames() {
        if (categoryName == null || categoryName.isBlank()) {
            return List.of();
        }
        return List.of(categoryName);
    }

    public String getDisplayAddress() {
        if (roadAddress != null && !roadAddress.isBlank()) {
            return roadAddress;
        }
        return address;
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
            String roadAddress,
            String categoryName,
            String primaryCategoryName,
            String regionName,
            String regionCityName,
            String regionDistrictName,
            String regionCountyName,
            String regionTownName,
            List<String> regionFilterNames,
            BigDecimal lat,
            BigDecimal lng,
            String imageUrl,
            String phoneNumber,
            String businessHoursRaw,
            String pcmapPlaceId,
            LocalDateTime menuUpdatedAt
    ) {
        this.name = name;
        this.address = address;
        this.roadAddress = roadAddress;
        this.categoryName = categoryName;
        this.primaryCategoryName = primaryCategoryName;
        this.regionName = regionName;
        this.regionCityName = regionCityName;
        this.regionDistrictName = regionDistrictName;
        this.regionCountyName = regionCountyName;
        this.regionTownName = regionTownName;
        this.regionFilterNames = regionFilterNames == null
                ? new ArrayList<>()
                : new ArrayList<>(regionFilterNames);
        this.lat = lat;
        this.lng = lng;
        this.imageUrl = imageUrl;
        this.phoneNumber = phoneNumber;
        this.businessHoursRaw = businessHoursRaw;
        this.pcmapPlaceId = pcmapPlaceId;
        this.menuUpdatedAt = menuUpdatedAt;
        this.isHidden = false;
        this.isDeleted = false;
    }
}
