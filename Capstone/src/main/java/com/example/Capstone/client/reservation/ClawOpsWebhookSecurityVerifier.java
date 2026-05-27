package com.example.Capstone.client.reservation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ClawOpsWebhookSecurityVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ClawOpsProperties clawOpsProperties;
    private final ReservationProviderProperties providerProperties;
    private final Clock clock;

    @Autowired
    public ClawOpsWebhookSecurityVerifier(
            ClawOpsProperties clawOpsProperties,
            ReservationProviderProperties providerProperties
    ) {
        this(clawOpsProperties, providerProperties, Clock.systemUTC());
    }

    public ClawOpsWebhookSecurityVerifier(
            ClawOpsProperties clawOpsProperties,
            ReservationProviderProperties providerProperties,
            Clock clock
    ) {
        this.clawOpsProperties = clawOpsProperties;
        this.providerProperties = providerProperties;
        this.clock = clock;
    }

    public ReservationProviderWebhookSecurityResult verify(ReservationProviderWebhookPayload payload) {
        if (payload == null) {
            return ReservationProviderWebhookSecurityResult.rejected("payload가 없습니다.");
        }
        if (!"CLAWOPS".equalsIgnoreCase(payload.provider())) {
            return ReservationProviderWebhookSecurityResult.rejected("지원하지 않는 ClawOps provider입니다.");
        }
        if (!hasText(clawOpsProperties.getWebhookSigningKey())
                || !hasText(clawOpsProperties.getStatusCallbackUrl())) {
            return ReservationProviderWebhookSecurityResult.rejected("ClawOps webhook 검증 설정이 부족합니다.");
        }

        String signature = payload.header(clawOpsProperties.resolvedWebhookSignatureHeader());
        if (!hasText(signature)) {
            return ReservationProviderWebhookSecurityResult.rejected("ClawOps webhook signature header가 없습니다.");
        }

        Map<String, String> parameters = ClawOpsWebhookFormParser.parse(payload.rawPayload());
        String timestamp = parameter(parameters, "Timestamp");
        if (!isTimestampAllowed(timestamp)) {
            return ReservationProviderWebhookSecurityResult.rejected("ClawOps webhook timestamp 허용 범위를 벗어났습니다.");
        }

        String expectedSignature = sign(
                clawOpsProperties.getWebhookSigningKey(),
                clawOpsProperties.getStatusCallbackUrl(),
                parameters
        );
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.trim().getBytes(StandardCharsets.UTF_8)
        )) {
            return ReservationProviderWebhookSecurityResult.rejected("ClawOps webhook signature가 일치하지 않습니다.");
        }

        return ReservationProviderWebhookSecurityResult.success();
    }

    public static String sign(String signingKey, String requestUrl, Map<String, String> parameters) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String payloadToSign = canonicalPayload(requestUrl, parameters);
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(payloadToSign.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("ClawOps webhook signature 생성에 실패했습니다.", exception);
        }
    }

    private static String canonicalPayload(String requestUrl, Map<String, String> parameters) {
        StringBuilder builder = new StringBuilder(requestUrl == null ? "" : requestUrl);
        if (parameters == null || parameters.isEmpty()) {
            return builder.toString();
        }

        parameters.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> builder
                        .append(entry.getKey())
                        .append(entry.getValue() == null ? "" : entry.getValue()));
        return builder.toString();
    }

    private String parameter(Map<String, String> parameters, String name) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        if (parameters.containsKey(name)) {
            return parameters.get(name);
        }
        return parameters.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private boolean isTimestampAllowed(String timestamp) {
        if (!hasText(timestamp)) {
            return false;
        }
        try {
            Instant receivedAt = Instant.parse(timestamp.trim());
            Duration difference = Duration.between(receivedAt, Instant.now(clock)).abs();
            return difference.compareTo(Duration.ofSeconds(providerProperties.getWebhookMaxClockSkewSeconds())) <= 0;
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
