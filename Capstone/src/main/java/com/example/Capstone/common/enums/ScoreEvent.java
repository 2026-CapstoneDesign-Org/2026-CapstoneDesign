package com.example.Capstone.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScoreEvent {

    REVIEW_CREATED      ("리뷰 작성",            +0.4),
    FOLLOWED            ("팔로우 받음",           +0.2),
    LIST_LIKED          ("리스트 좋아요 받음",     +0.3),
    REVIEW_LIKED        ("리뷰 좋아요 받음",       +0.2),
    PROFILE_COMPLETED   ("프로필 항목 완성",       +0.5),
    REPRESENTATIVE_SET  ("대표 리스트 설정",       +1.0),

    REVIEW_DISLIKED     ("리뷰 싫어요 받음",       -0.5),
    AUTO_REPORTED       ("자동 신고",             -3.0),
    MANUAL_REPORTED     ("수동 신고 처리 완료",    -5.0),
    INACTIVE_PENALTY    ("비활동 패널티",          -0.1);

    private final String description;
    private final double basePoint;
}
