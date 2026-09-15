package com.gokchu.gokchu.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 로그인 실패를 IP별로 추적해서 짧은 시간에 반복 시도하는 걸 막는다.
 * 인스턴스 하나에서만 메모리에 들고 있는 수준이라 여러 대로 스케일아웃하면 안 먹힘 —
 * 지금 배포 규모(EC2 한 대)에서는 충분하고, 나중에 커지면 Redis 등으로 옮기면 됨.
 */
@Component
public class AdminLoginRateLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Attempts> attemptsByIp = new ConcurrentHashMap<>();

    @Autowired
    public AdminLoginRateLimiter(
            @Value("${admin.login-rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${admin.login-rate-limit.window-minutes:15}") long windowMinutes) {
        this(maxAttempts, Duration.ofMinutes(windowMinutes), Clock.systemUTC());
    }

    AdminLoginRateLimiter(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    public boolean isBlocked(String clientIp) {
        Attempts attempts = attemptsByIp.get(clientIp);
        if (attempts == null || isExpired(attempts)) {
            return false;
        }
        return attempts.count.get() >= maxAttempts;
    }

    public void recordFailure(String clientIp) {
        attemptsByIp.compute(clientIp, (ip, existing) -> {
            if (existing == null || isExpired(existing)) {
                return new Attempts(clock.instant(), new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
    }

    public void recordSuccess(String clientIp) {
        attemptsByIp.remove(clientIp);
    }

    private boolean isExpired(Attempts attempts) {
        return clock.instant().isAfter(attempts.windowStart.plus(window));
    }

    private record Attempts(Instant windowStart, AtomicInteger count) {
    }
}
