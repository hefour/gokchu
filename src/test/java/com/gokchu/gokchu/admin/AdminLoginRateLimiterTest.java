package com.gokchu.gokchu.admin;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AdminLoginRateLimiterTest {

    @Test
    void isBlocked_실패_기록이_없으면_차단되지_않는다() {
        AdminLoginRateLimiter limiter = new AdminLoginRateLimiter(3, Duration.ofMinutes(15), Clock.systemUTC());

        assertThat(limiter.isBlocked("1.2.3.4")).isFalse();
    }

    @Test
    void isBlocked_최대_시도횟수_미만이면_차단되지_않는다() {
        AdminLoginRateLimiter limiter = new AdminLoginRateLimiter(3, Duration.ofMinutes(15), Clock.systemUTC());

        limiter.recordFailure("1.2.3.4");
        limiter.recordFailure("1.2.3.4");

        assertThat(limiter.isBlocked("1.2.3.4")).isFalse();
    }

    @Test
    void isBlocked_최대_시도횟수에_도달하면_차단된다() {
        AdminLoginRateLimiter limiter = new AdminLoginRateLimiter(3, Duration.ofMinutes(15), Clock.systemUTC());

        limiter.recordFailure("1.2.3.4");
        limiter.recordFailure("1.2.3.4");
        limiter.recordFailure("1.2.3.4");

        assertThat(limiter.isBlocked("1.2.3.4")).isTrue();
    }

    @Test
    void isBlocked_다른_IP는_영향받지_않는다() {
        AdminLoginRateLimiter limiter = new AdminLoginRateLimiter(1, Duration.ofMinutes(15), Clock.systemUTC());

        limiter.recordFailure("1.2.3.4");

        assertThat(limiter.isBlocked("5.6.7.8")).isFalse();
    }

    @Test
    void recordSuccess_성공하면_실패_기록이_초기화된다() {
        AdminLoginRateLimiter limiter = new AdminLoginRateLimiter(1, Duration.ofMinutes(15), Clock.systemUTC());

        limiter.recordFailure("1.2.3.4");
        limiter.recordSuccess("1.2.3.4");

        assertThat(limiter.isBlocked("1.2.3.4")).isFalse();
    }

    @Test
    void isBlocked_시간창이_지나면_다시_시도할_수_있다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AdminLoginRateLimiter limiter = new AdminLoginRateLimiter(1, Duration.ofMinutes(15), clock);

        limiter.recordFailure("1.2.3.4");
        assertThat(limiter.isBlocked("1.2.3.4")).isTrue();

        clock.advance(Duration.ofMinutes(20));

        assertThat(limiter.isBlocked("1.2.3.4")).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
