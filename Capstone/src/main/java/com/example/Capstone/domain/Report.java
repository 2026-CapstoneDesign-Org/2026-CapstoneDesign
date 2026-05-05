package com.example.Capstone.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private User reporter;          // null 이면 자동 신고

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TargetType targetType;  // REVIEW / USER / LIST

    @Column(nullable = false)
    private Long targetId;

    @Column(nullable = false, length = 100)
    private String reason;

    @Column(nullable = false)
    private Boolean isAuto = false; // 자동 신고 여부

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ReportStatus status;    // PENDING / RESOLVED / DISMISSED

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum TargetType { REVIEW, USER, LIST }
    public enum ReportStatus { PENDING, RESOLVED, DISMISSED }

    // 수동 신고
    @Builder(builderMethodName = "manualReport")
    private Report(User reporter, TargetType targetType, Long targetId, String reason) {
        this.reporter   = reporter;
        this.targetType = targetType;
        this.targetId   = targetId;
        this.reason     = reason;
        this.isAuto     = false;
        this.status     = ReportStatus.PENDING;
    }

    // 자동 신고
    @Builder(builderMethodName = "autoReport")
    private Report(TargetType targetType, Long targetId, String reason) {
        this.reporter   = null;
        this.targetType = targetType;
        this.targetId   = targetId;
        this.reason     = reason;
        this.isAuto     = true;
        this.status     = ReportStatus.PENDING;
    }

    public void resolve()  { this.status = ReportStatus.RESOLVED; }
    public void dismiss()  { this.status = ReportStatus.DISMISSED; }
}
