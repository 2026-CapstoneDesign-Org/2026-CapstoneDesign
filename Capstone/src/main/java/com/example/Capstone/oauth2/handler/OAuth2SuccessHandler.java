package com.example.Capstone.oauth2.handler;

import com.example.Capstone.repository.RefreshTokenRepository;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.example.Capstone.common.jwt.JwtProvider;
import com.example.Capstone.domain.RefreshToken;
import com.example.Capstone.domain.User;
import com.example.Capstone.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    @Override
    @SuppressWarnings("unchecked")
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication) throws IOException {

            OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
            Map<String, Object> attributes = authToken.getPrincipal().getAttributes();
            String registrationId = authToken.getAuthorizedClientRegistrationId();

            String provider;
            String providerUserId;

            switch (registrationId) {
                case "google" -> {
                    provider       = "GOOGLE";
                    providerUserId = (String) attributes.get("sub");
                }
                case "kakao" -> {
                    provider       = "KAKAO";
                    providerUserId = String.valueOf(attributes.get("id"));
                }
                case "naver" -> {
                    Map<String, Object> response_ = (Map<String, Object>) attributes.get("response");
                    provider       = "NAVER";
                    providerUserId = (String) response_.get("id");
                }
                default -> throw new OAuth2AuthenticationException("지원하지 않는 플랫폼");
            }

            User user = userRepository
                .findByProviderAndProviderUserId(provider, providerUserId)
                .orElseThrow();

            String accessToken  = jwtProvider.generateAccessToken(user.getId(), user.getRole().name());
            
            String refreshToken = jwtProvider.generateRefreshToken(user.getId());
            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(604800);

            refreshTokenRepository.findByUserId(user.getId())
                .ifPresentOrElse(
                        rt -> rt.updateToken(refreshToken, expiresAt),  // 기존 토큰 갱신
                        () -> refreshTokenRepository.save(RefreshToken.builder()
                                .userId(user.getId())
                                .token(refreshToken)
                                .expiresAt(expiresAt)
                                .build())
                );

            boolean needsProfile = user.getGender() == null || user.getBirthYear() == null;

            if (needsProfile) {
                getRedirectStrategy().sendRedirect(request, response,
                    "exp+wagu://auth/callback?accessToken=" + accessToken + "&refreshToken" + refreshToken + "&needsProfile=true");
            } else {
                getRedirectStrategy().sendRedirect(request, response,
                    "exp+wagu://auth/callback?accessToken=" + accessToken + "&refreshToken" + refreshToken + "&needsProfile=false");
            }
    }
}
