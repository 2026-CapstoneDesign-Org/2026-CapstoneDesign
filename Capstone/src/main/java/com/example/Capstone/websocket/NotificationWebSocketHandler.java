package com.example.Capstone.websocket;

import com.example.Capstone.common.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final JwtProvider jwtProvider;

    // userId → WebSocketSession
    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = extractUserId(session);
        if (userId != null) {
            sessions.put(userId, session);
            log.info("WebSocket 연결 - userId: {}", userId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = extractUserId(session);
        if (userId != null) {
            sessions.remove(userId);
            log.info("WebSocket 종료 - userId: {}", userId);
        }
    }

    // 특정 유저에게 알림 전송
    public void sendNotification(Long userId, String message) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
                log.info("알림 전송 - userId: {}, message: {}", userId, message);
            } catch (IOException e) {
                log.error("알림 전송 실패 - userId: {}", userId, e);
            }
        }
    }

    public boolean isConnected(Long userId) {
        WebSocketSession session = sessions.get(userId);
        return session != null && session.isOpen();
    }

    // WebSocket URL 파라미터에서 JWT 추출
    private Long extractUserId(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery();  // token=xxx
            if (query != null && query.startsWith("token=")) {
                String token = query.substring(6);
                if (jwtProvider.validateToken(token)) {
                    return jwtProvider.getUserId(token);
                }
            }
        } catch (Exception e) {
            log.error("WebSocket JWT 파싱 실패", e);
        }
        return null;
    }
}