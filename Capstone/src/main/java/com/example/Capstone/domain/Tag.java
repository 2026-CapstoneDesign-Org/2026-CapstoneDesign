package com.example.Capstone.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String tagKey;

    @Column(nullable = false, length = 100)
    private String tagName;

    @Column(nullable = false, length = 30)
    private String tagType;

    @Column(length = 100)
    private String parentTagKey;

    @Column(nullable = false)
    private Boolean isActive = true;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void updateInfo(String tagName, String tagType, String parentTagKey, Boolean isActive) {
        this.tagName = tagName;
        this.tagType = tagType;
        this.parentTagKey = parentTagKey;
        this.isActive = isActive == null ? Boolean.TRUE : isActive;
    }

    @Builder
    private Tag(String tagKey, String tagName, String tagType, String parentTagKey, Boolean isActive) {
        this.tagKey = tagKey;
        this.tagName = tagName;
        this.tagType = tagType;
        this.parentTagKey = parentTagKey;
        this.isActive = isActive == null ? Boolean.TRUE : isActive;
    }
}
