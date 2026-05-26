package com.example.Capstone.dto.response;

import com.example.Capstone.domain.Notification;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        Notification.NotificationType type,
        String message,
        Long targetId,
        String targetType,
        Boolean isRead,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getTargetId(),
                notification.getTargetType(),
                notification.getIsRead(),
                notification.getCreatedAt()
        );
    }
}