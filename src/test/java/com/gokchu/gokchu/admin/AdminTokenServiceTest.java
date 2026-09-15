package com.gokchu.gokchu.admin;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTokenServiceTest {

    @Test
    void issueToken_발급한_토큰은_유효하다() {
        AdminTokenService service = new AdminTokenService("secret", 3600, Clock.systemUTC());

        String token = service.issueToken();

        assertThat(service.isValid(token)).isTrue();
    }

    @Test
    void isValid_만료된_토큰이면_false() {
        Clock issueClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        AdminTokenService issuer = new AdminTokenService("secret", 10, issueClock);
        String token = issuer.issueToken();

        Clock laterClock = Clock.fixed(Instant.parse("2026-01-01T00:00:20Z"), ZoneOffset.UTC);
        AdminTokenService validator = new AdminTokenService("secret", 10, laterClock);

        assertThat(validator.isValid(token)).isFalse();
    }

    @Test
    void isValid_변조된_토큰이면_false() {
        AdminTokenService service = new AdminTokenService("secret", 3600, Clock.systemUTC());
        String token = service.issueToken();

        assertThat(service.isValid(token + "tampered")).isFalse();
    }

    @Test
    void isValid_다른_시크릿으로_발급된_토큰이면_false() {
        AdminTokenService issuer = new AdminTokenService("secret-a", 3600, Clock.systemUTC());
        AdminTokenService validator = new AdminTokenService("secret-b", 3600, Clock.systemUTC());

        String token = issuer.issueToken();

        assertThat(validator.isValid(token)).isFalse();
    }

    @Test
    void isValid_형식이_잘못된_토큰이면_false() {
        AdminTokenService service = new AdminTokenService("secret", 3600, Clock.systemUTC());

        assertThat(service.isValid("not-a-valid-token")).isFalse();
    }

    @Test
    void isValid_null이면_false() {
        AdminTokenService service = new AdminTokenService("secret", 3600, Clock.systemUTC());

        assertThat(service.isValid(null)).isFalse();
    }
}
