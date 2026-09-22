package com.gokchu.gokchu.search;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SearchQuotaLimiterTest {

    @Test
    void tryConsume_상한_미만이면_계속_허용한다() {
        SearchQuotaLimiter limiter = new SearchQuotaLimiter(3, Clock.systemUTC());

        assertThat(limiter.tryConsume()).isTrue();
        assertThat(limiter.tryConsume()).isTrue();
        assertThat(limiter.tryConsume()).isTrue();
    }

    @Test
    void tryConsume_상한에_도달하면_거부한다() {
        SearchQuotaLimiter limiter = new SearchQuotaLimiter(2, Clock.systemUTC());

        limiter.tryConsume();
        limiter.tryConsume();

        assertThat(limiter.tryConsume()).isFalse();
    }

    @Test
    void tryConsume_날짜가_바뀌면_카운트가_초기화된다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T23:00:00Z"));
        SearchQuotaLimiter limiter = new SearchQuotaLimiter(1, clock);

        assertThat(limiter.tryConsume()).isTrue();
        assertThat(limiter.tryConsume()).isFalse();

        clock.advanceToNextDay();

        assertThat(limiter.tryConsume()).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceToNextDay() {
            instant = instant.plus(2, ChronoUnit.HOURS);
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
