package com.example.Capstone.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class NaverOAuth2TokenResponseConverter
        implements Converter<Map<String, Object>, OAuth2AccessTokenResponse> {

    @Override
    public OAuth2AccessTokenResponse convert(Map<String, Object> source) {
        log.info("Naver token response: {}", source);

        String accessToken = (String) source.get("access_token");
        String tokenType = (String) source.getOrDefault("token_type", "Bearer");

        return OAuth2AccessTokenResponse.withToken(accessToken)
                .tokenType(OAuth2AccessToken.TokenType.BEARER)
                .additionalParameters(source)
                .build();
    }
}