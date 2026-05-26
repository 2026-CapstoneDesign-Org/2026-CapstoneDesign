package com.example.Capstone.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class ExternalApiValidationConfig {

    @Bean
    ApplicationRunner externalApiValidationRunner(Environment environment) {
        return args -> {
            if (!environment.getProperty("external-api.validation.enabled", Boolean.class, false)) {
                return;
            }

            List<String> invalidProperties = new ArrayList<>();
            requireRealValue(environment, invalidProperties, "jwt.secret");
            requireRealValue(environment, invalidProperties, "gemini.api.key");
            requireRealValue(environment, invalidProperties, "aws.s3.access-key");
            requireRealValue(environment, invalidProperties, "aws.s3.secret-key");
            requireRealValue(environment, invalidProperties, "aws.s3.bucket");
            requireRealValue(environment, invalidProperties, "spring.security.oauth2.client.registration.google.client-id");
            requireRealValue(environment, invalidProperties, "spring.security.oauth2.client.registration.google.client-secret");
            requireRealValue(environment, invalidProperties, "spring.security.oauth2.client.registration.kakao.client-id");
            requireRealValue(environment, invalidProperties, "spring.security.oauth2.client.registration.kakao.client-secret");
            requireRealValue(environment, invalidProperties, "spring.security.oauth2.client.registration.naver.client-id");
            requireRealValue(environment, invalidProperties, "spring.security.oauth2.client.registration.naver.client-secret");

            if (environment.getProperty("search.naver-local.enabled", Boolean.class, false)) {
                requireRealValue(environment, invalidProperties, "search.naver-local.client-id");
                requireRealValue(environment, invalidProperties, "search.naver-local.client-secret");
            }
            if (environment.getProperty("parking-lot.gyeonggi-api.enabled", Boolean.class, false)) {
                requireRealValue(environment, invalidProperties, "parking-lot.gyeonggi-api.key");
            }
            if (environment.getProperty("parking-lot.seoul-citydata.enabled", Boolean.class, false)) {
                requireRealValue(environment, invalidProperties, "parking-lot.seoul-citydata.key");
            }

            if (!invalidProperties.isEmpty()) {
                throw new IllegalStateException(
                        "External API validation failed. Missing or dummy properties: "
                                + String.join(", ", invalidProperties)
                );
            }
        };
    }

    private void requireRealValue(Environment environment, List<String> invalidProperties, String propertyName) {
        String value = environment.getProperty(propertyName, "");
        if (value.isBlank()
                || value.startsWith("dummy-")
                || value.startsWith("test-")
                || "test".equalsIgnoreCase(value)) {
            invalidProperties.add(propertyName);
        }
    }
}
