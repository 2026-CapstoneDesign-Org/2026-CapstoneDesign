package com.example.Capstone.oauth2;
import java.util.Collections;
import java.util.Map;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.common.util.NicknameGenerator;
import com.example.Capstone.domain.User;
import com.example.Capstone.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User>{

    private final UserRepository userRepository;
    private final NicknameGenerator nicknameGenerator;

    @Override
    @SuppressWarnings("unchecked")
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
            
        OAuth2User oAuth2User = new DefaultOAuth2UserService().loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String provider;
        String providerUserId;

        switch (registrationId) {
            case "google" -> {
                provider = "GOOGLE";
                providerUserId = (String) attributes.get("sub");
            }
            case "kakao"  -> {
                provider       = "KAKAO";
                providerUserId = String.valueOf(attributes.get("id"));
            }
            case "naver"  -> {
                Map<String, Object> response = (Map<String, Object>) attributes.get("response");
                provider       = "NAVER";
                providerUserId = (String) response.get("id");
            }
            default -> throw new OAuth2AuthenticationException("지원하지 않는 플랫폼: " + registrationId);
        };

        userRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .orElseGet(() -> {
                    String nickname = generateUniqueNickname();

                    User newUser = User.builder()
                            .provider(provider)
                            .providerUserId(providerUserId)
                            .nickname(nickname)
                            .profileImageUrl("기본 프로필 url") // 기본 이미지 url 제공해야함
                            .role(User.Role.USER)
                            .build();

                    User saved = userRepository.saveAndFlush(newUser);
                    return saved;
                });

        return new DefaultOAuth2User(
            Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
            attributes,
            request.getClientRegistration()
                    .getProviderDetails()
                    .getUserInfoEndpoint()
                    .getUserNameAttributeName()
        );
    }

    // 닉네임 중복 방지
    private String generateUniqueNickname() {
        String nickname;
        do {
            nickname = nicknameGenerator.generate();
        } while (userRepository.existsByNickname(nickname));
        return nickname;
    }
}
