package com.gokchu.gokchu.search;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code search.list} 실제 호출(캐시 미스) 횟수를 하루 단위로 세서 상한을 넘으면 막는다.
 * YouTube 쿼터 자체는 일일 10,000유닛이고 검색 1회가 100유닛이라 이론상 하루 100회까지
 * 가능하지만, 그걸 전부 검색에 쓰면 링크 등록/검증(videos.list)에 쓸 유닛이 안 남을 수 있어서
 * 상한을 더 낮게(기본 50) 잡아 여유를 남긴다. 인스턴스 하나에서만 메모리로 세는 수준이라
 * 재시작하면 초기화됨 — 지금 배포 규모(EC2 한 대)에서는 충분.
 */
@Component
public class SearchQuotaLimiter {

    private final int dailyLimit;
    private final Clock clock;
    private volatile LocalDate windowDate;
    private final AtomicInteger count = new AtomicInteger(0);

    @Autowired
    public SearchQuotaLimiter(@Value("${youtube.search.daily-limit:50}") int dailyLimit) {
        this(dailyLimit, Clock.systemUTC());
    }

    SearchQuotaLimiter(int dailyLimit, Clock clock) {
        this.dailyLimit = dailyLimit;
        this.clock = clock;
        this.windowDate = LocalDate.now(clock);
    }

    /** 오늘 상한에 여유가 있으면 1회 소모하고 true, 이미 다 썼으면 false(카운트는 그대로). */
    public synchronized boolean tryConsume() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(windowDate)) {
            windowDate = today;
            count.set(0);
        }

        if (count.get() >= dailyLimit) {
            return false;
        }
        count.incrementAndGet();
        return true;
    }
}
