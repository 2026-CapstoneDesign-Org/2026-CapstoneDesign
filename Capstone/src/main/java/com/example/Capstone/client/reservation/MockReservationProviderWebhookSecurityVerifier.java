package com.example.Capstone.client.reservation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MockReservationProviderWebhookSecurityVerifier implements ReservationProviderWebhookSecurityVerifier {

    public static final String SIGNATURE_HEADER = "X-Mock-Reservation-Signature";
    public static final String TIMESTAMP_HEADER = "X-Mock-Reservation-Timestamp";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String signatureSecret;
    private final Duration allowedClockSkew;
    private final Clock clock;

    @Autowired
    public MockReservationProviderWebhookSecurityVerifier(
            @Value("${reservation.webhook.mock.signature-secret:mock-reservation-webhook-secret}")
            String signatureSecret,
            @Value("${reservation.webhook.mock.allowed-clock-skew-seconds:300}")
            long allowedClockSkewSeconds
    ) {
        this(signatureSecret, Duration.ofSeconds(allowedClockSkewSeconds), Clock.systemUTC());
    }

    public MockReservationProviderWebhookSecurityVerifier(
            String signatureSecret,
            Duration allowedClockSkew,
            Clock clock
    ) {
        this.signatureSecret = signatureSecret;
        this.allowedClockSkew = allowedClockSkew;
        this.clock = clock;
    }

    @Override
    public ReservationProviderWebhookSecurityResult verify(ReservationProviderWebhookPayload payload) {
        if (payload == null) {
            return ReservationProviderWebhookSecurityResult.rejected("payload가 없습니다.");
        }
        if (!"MOCK".equalsIgnoreCase(payload.provider())) {
            return ReservationProviderWebhookSecurityResult.rejected("지원하지 않는 mock provider입니다.");
        }

        String timestamp = payload.header(TIMESTAMP_HEADER);
        String signature = payload.header(SIGNATURE_HEADER);
        if (timestamp == null || timestamp.isBlank() || signature == null || signature.isBlank()) {
            return ReservationProviderWebhookSecurityResult.rejected("mock webhook signature header가 없습니다.");
        }
        if (!isTimestampAllowed(timestamp)) {
            return ReservationProviderWebhookSecurityResult.rejected("mock webhook timestamp 허용 범위를 벗어났습니다.");
        }

        String expectedSignature = sign(signatureSecret, timestamp, payload.rawPayload());
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.trim().getBytes(StandardCharsets.UTF_8)
        )) {
            return ReservationProviderWebhookSecurityResult.rejected("mock webhook signature가 일치하지 않습니다.");
        }

        return ReservationProviderWebhookSecurityResult.success();
    }

    public static String sign(String secret, String timestamp, String rawPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String payloadToSign = timestamp + "." + (rawPayload == null ? "" : rawPayload);
            return HexFormat.of().formatHex(mac.doFinal(payloadToSign.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("mock webhook signature 생성에 실패했습니다.", exception);
        }
    }

    private boolean isTimestampAllowed(String timestamp) {
        try {
            Instant receivedAt = Instant.parse(timestamp.trim());
            Duration difference = Duration.between(receivedAt, Instant.now(clock)).abs();
            return difference.compareTo(allowedClockSkew) <= 0;
        } catch (Exception exception) {
            return false;
        }
    }
}
