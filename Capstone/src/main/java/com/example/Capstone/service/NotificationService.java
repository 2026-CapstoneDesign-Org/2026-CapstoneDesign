package com.example.Capstone.service;

import com.example.Capstone.domain.Notification;
import com.example.Capstone.dto.response.NotificationResponse;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.NotificationRepository;
import com.example.Capstone.repository.UserFollowRepository;
import com.example.Capstone.websocket.NotificationWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserFollowRepository userFollowRepository;
    private final NotificationWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    // 알림 생성 및 전송
    @Transactional
    public void send(Long userId, Notification.NotificationType type,
                     String message, Long targetId, String targetType) {
        // DB 저장
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .message(message)
                .targetId(targetId)
                .targetType(targetType)
                .build();
        notificationRepository.save(notification);

        // WebSocket 실시간 전송
        if (webSocketHandler.isConnected(userId)) {
            try {
                NotificationResponse response = NotificationResponse.from(notification);
                String json = objectMapper.writeValueAsString(response);
                webSocketHandler.sendNotification(userId, json);
            } catch (Exception e) {
                log.error("WebSocket 알림 전송 실패 - userId: {}", userId, e);
            }
        }
    }

    // 알림 목록 조회
    public List<NotificationResponse> getNotifications(Long userId) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    // 읽지 않은 알림 수
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    // 단건 읽음 처리
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException("알림을 찾을 수 없습니다."));
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException("권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        notification.read();
    }

    // 전체 읽음 처리
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }


    // 팔로워 전체 알림 - 비동기 + 배치 처리
    @Async
    @Transactional
    public void sendToFollowers(Long userId, Notification.NotificationType type,
                                String message, Long targetId, String targetType) {

        List<Notification> notifications = userFollowRepository
                .findAllByFollowingId(userId)
                .stream()
                .map(follow -> Notification.builder()
                        .userId(follow.getFollower().getId())
                        .type(type)
                        .message(message)
                        .targetId(targetId)
                        .targetType(targetType)
                        .build())
                .toList();

        // 한번에 저장
        notificationRepository.saveAll(notifications);

        // WebSocket 실시간 전송
        notifications.forEach(n ->
                webSocketHandler.sendNotification(n.getUserId(),
                        toJson(n))
        );
    }

    private String toJson(Notification n) {
        try {
            return objectMapper.writeValueAsString(NotificationResponse.from(n));
        } catch (Exception e) {
            return "{}";
        }
    }
}