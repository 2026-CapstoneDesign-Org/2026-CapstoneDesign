package com.example.Capstone.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;        // 알림 받을 유저

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private NotificationType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column
    private Long targetId;      // 관련 리소스 ID

    @Column(length = 20)
    private String targetType;  // REVIEW / LIST / USER

    @Column(nullable = false)
    private Boolean isRead = false;

    @Column(nullable = false, insertable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public enum NotificationType {
        FOLLOW,         // 팔로우
        REVIEW_LIKE,    // 리뷰 좋아요
        LIST_LIKE,      // 리스트 좋아요
        FOLLOWING_NEW_REVIEW,
        FOLLOWING_NEW_LIST 
    }

    @Builder
    private Notification(Long userId, NotificationType type, String message,
                         Long targetId, String targetType) {
        this.userId     = userId;
        this.type       = type;
        this.message    = message;
        this.targetId   = targetId;
        this.targetType = targetType;
        this.isRead     = false;
    }

    public void read() {
        this.isRead = true;
    }
}