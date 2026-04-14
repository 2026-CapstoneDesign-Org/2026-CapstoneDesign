package com.example.Capstone.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.example.Capstone.domain.base.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String tagKey;

    @Column(nullable = false, length = 100)
    private String tagName;

    @Column(length = 100)
    private String parentTagKey;

    @Column(nullable = false)
    private Boolean isActive = true;

    public void updateInfo(String tagName, String parentTagKey, Boolean isActive) {
        this.tagName = tagName;
        this.parentTagKey = parentTagKey;
        this.isActive = isActive == null ? Boolean.TRUE : isActive;
    }

    @Builder
    private Tag(String tagKey, String tagName, String parentTagKey, Boolean isActive) {
        this.tagKey = tagKey;
        this.tagName = tagName;
        this.parentTagKey = parentTagKey;
        this.isActive = isActive == null ? Boolean.TRUE : isActive;
    }
}
