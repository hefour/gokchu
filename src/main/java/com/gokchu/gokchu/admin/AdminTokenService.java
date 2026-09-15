package com.gokchu.gokchu.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;

@Service
public class AdminTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secret;
    private final long ttlSeconds;
    private final Clock clock;

    @Autowired
    public AdminTokenService(
            @Value("${admin.token-secret}") String secret,
            @Value("${admin.token-ttl-seconds}") long ttlSeconds) {
        this(secret, ttlSeconds, Clock.systemUTC());
    }

    AdminTokenService(String secret, long ttlSeconds, Clock clock) {
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
        this.clock = clock;
    }

    public String issueToken() {
        String payload = Long.toString(clock.instant().getEpochSecond() + ttlSeconds);
        return payload + "." + sign(payload);
    }

    public boolean isValid(String token) {
        if (token == null) {
            return false;
        }

        int dot = token.indexOf('.');
        if (dot < 0) {
            return false;
        }

        String payload = token.substring(0, dot);
        String signature = token.substring(dot + 1);

        if (!constantTimeEquals(sign(payload), signature)) {
            return false;
        }

        try {
            long expiresAt = Long.parseLong(payload);
            return clock.instant().getEpochSecond() < expiresAt;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("토큰 서명에 실패했습니다.", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
