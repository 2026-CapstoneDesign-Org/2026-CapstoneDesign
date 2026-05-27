package com.example.Capstone.client.reservation;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ClawOpsSidecarInternalSignature {

    public static final String TIMESTAMP_HEADER = "X-Request-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Internal-Signature";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private ClawOpsSidecarInternalSignature() {
    }

    public static String sign(String signingKey, String timestamp, String rawBody) {
        if (!hasText(signingKey) || !hasText(timestamp)) {
            throw new IllegalArgumentException("signingKey와 timestamp는 필수입니다.");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(baseString(timestamp, rawBody).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("internal HMAC signature를 생성할 수 없습니다.", exception);
        }
    }

    public static boolean matches(String expectedSignature, String providedSignature) {
        if (!hasText(expectedSignature) || !hasText(providedSignature)) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                providedSignature.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    public static boolean isTimestampAllowed(String timestamp, long allowedSkewSeconds, Clock clock) {
        if (!hasText(timestamp)) {
            return false;
        }
        try {
            Instant requestInstant = Instant.parse(timestamp.trim());
            long skewSeconds = Math.max(0, allowedSkewSeconds);
            Duration drift = Duration.between(requestInstant, Instant.now(clock)).abs();
            return drift.compareTo(Duration.ofSeconds(skewSeconds)) <= 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String baseString(String timestamp, String rawBody) {
        return timestamp.trim() + "\n" + (rawBody == null ? "" : rawBody);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
