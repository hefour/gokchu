package com.gokchu.gokchu.queue.experiment;

import com.gokchu.gokchu.common.exception.GokchuException;
import com.gokchu.gokchu.store.Store;
import com.gokchu.gokchu.store.StoreRepository;
import com.gokchu.gokchu.support.PostgresTestcontainersConfig;
import com.gokchu.gokchu.tabletoken.TableToken;
import com.gokchu.gokchu.tabletoken.TableTokenRepository;
import com.gokchu.gokchu.video.YoutubeApiClient;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "재생 완료 통보"와 "스킵"이 동시에 들어올 때 곡이 2개 넘어가는 문제를 재현하고, 해결책 2개를 비교한다.
 *
 * <p>한 번의 시행(trial): A(PLAYING) 뒤에 대기곡 B, C, ...를 깔아둔 상태에서 동시 요청 N개를 한꺼번에 쏜다.
 * 절반은 "A 재생 완료"(TV), 절반은 "A 스킵"(관리자)이다. 모두 "A를 끝내라"는 같은 의도이므로
 * 올바른 결과는 항상 "A만 끝나고(1곡 이동) B 하나만 PLAYING"이다.
 *
 * <p>실행: {@code ./gradlew concurrencyExperiment} (기본 test 태스크에서는 제외됨)
 */
@Tag("experiment")
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NowPlayingConcurrencyExperimentTest {

    private static final int TRIALS = 100;
    private static final int WARMUP_TRIALS = 5;
    private static final int[] CONCURRENCY_LEVELS = {2, 10, 100, 500};
    private static final int EXTRA_WAITING_ITEMS = 5;
    private static final Path REPORT = Path.of("build", "reports", "concurrency-experiment.md");

    // YouTube 호출이 절대 나가지 않게 막는다(이 실험은 큐 상태 전환만 다루고 영상 검증 경로를 타지 않음).
    @MockitoBean
    YoutubeApiClient youtubeApiClient;

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired StoreRepository storeRepository;
    @Autowired TableTokenRepository tableTokenRepository;
    @Autowired BaselineTransitions baseline;
    @Autowired ConditionalUpdateTransitions conditionalUpdate;
    @Autowired PessimisticLockTransitions pessimisticLock;
    @Autowired ProductionTransitions production;

    private Long storeId;
    private Long tableTokenId;
    private UUID tableToken;
    private final List<String> report = new ArrayList<>();

    @BeforeAll
    void setUpStore() {
        Store store = storeRepository.save(Store.builder().name("실험 매장").regionCode("KR").build());
        TableToken table = tableTokenRepository.save(TableToken.builder().store(store).label("실험 테이블").build());
        storeId = store.getId();
        tableTokenId = table.getId();
        tableToken = table.getToken();
        report.add("# 동시성 실험 원본 결과\n");
        report.add("- 실행 시각: " + LocalDateTime.now());
        report.add("- 시행 횟수: 조합당 " + TRIALS + "회 (워밍업 " + WARMUP_TRIALS + "회 제외)");
        report.add("- DB 커넥션 풀(Hikari maximumPoolSize): " + ((HikariDataSource) dataSource).getMaximumPoolSize());
        report.add("- PostgreSQL: " + jdbcTemplate.queryForObject("show server_version", String.class)
                + ", 격리수준 " + jdbcTemplate.queryForObject("show transaction_isolation", String.class) + "\n");
    }

    @AfterAll
    void writeReport() throws IOException {
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, String.join("\n", report) + "\n", StandardCharsets.UTF_8);
        System.out.println("\n" + String.join("\n", report));
    }

    @Test
    @DisplayName("순차 실행: 스킵이 먼저 처리되고 재생완료가 뒤늦게 도착하면 몇 곡이 넘어가는가")
    void sequentialSkipThenComplete() {
        report.add("## 1. 순차 실행 (스킵 → 뒤늦은 재생완료), 동시성 없음\n");
        report.add("| 방식 | 넘어간 곡 수 | PLAYING 곡 |");
        report.add("|---|---|---|");
        for (NowPlayingTransitions approach : approaches()) {
            List<Long> ids = resetQueue(3);
            Long songA = ids.get(0);
            approach.skip(tableToken, songA);
            approach.complete(tableToken, songA);
            QueueSnapshot snapshot = snapshot(ids.get(1));
            report.add("| " + approach.name() + " | " + snapshot.advanced() + " | " + snapshot.playingLabel(ids) + " |");
        }
        report.add("");
    }

    @Test
    @DisplayName("동시 실행: 재생완료 N/2 + 스킵 N/2 를 동시에 보내 100회 반복")
    void concurrentCompleteAndSkip() throws Exception {
        report.add("## 2. 동시 실행 (재생완료 N/2 + 스킵 N/2, 같은 곡 A 대상)\n");
        report.add("| 방식 | 동시 요청 N | 2곡 이상 넘어간 시행 | PLAYING 중복 시행 | 정상 시행 | 최대 이동 곡 수 | 최대 동시 PLAYING "
                + "| 409 거절(합계) | 기타 예외 | 시행당 평균(ms) | 시행당 p95(ms) | 총 처리 시간(ms) |");
        report.add("|---|---|---|---|---|---|---|---|---|---|---|---|");
        for (int concurrency : CONCURRENCY_LEVELS) {
            for (NowPlayingTransitions approach : approaches()) {
                ExperimentResult result = runTrials(approach, concurrency);
                report.add(result.toMarkdownRow());
                if (approach != baseline) {
                    assertThat(result.overAdvancedTrials()).as(approach.name() + " N=" + concurrency).isZero();
                    assertThat(result.duplicatePlayingTrials()).as(approach.name() + " N=" + concurrency).isZero();
                }
            }
        }
        report.add("");
    }

    private List<NowPlayingTransitions> approaches() {
        return List.of(baseline, conditionalUpdate, pessimisticLock, production);
    }

    private ExperimentResult runTrials(NowPlayingTransitions approach, int concurrency) throws Exception {
        ExperimentResult result = new ExperimentResult(approach.name(), concurrency);
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            for (int i = 0; i < WARMUP_TRIALS + TRIALS; i++) {
                TrialOutcome outcome = runSingleTrial(approach, concurrency, executor);
                if (i >= WARMUP_TRIALS) {
                    result.record(outcome);
                }
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.MINUTES);
        }
        return result;
    }

    /**
     * 모든 스레드를 ready 래치로 출발선에 세운 뒤 start 래치 하나로 동시에 풀어준다.
     * 스레드 생성/스케줄링 지연이 측정에 섞이지 않도록 시간은 start 직후부터 done까지만 잰다.
     */
    private TrialOutcome runSingleTrial(NowPlayingTransitions approach, int concurrency, ExecutorService executor)
            throws InterruptedException {
        List<Long> ids = resetQueue(concurrency + EXTRA_WAITING_ITEMS);
        Long songA = ids.get(0);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrency);
        AtomicInteger rejected = new AtomicInteger();
        Map<String, Integer> unexpected = new ConcurrentHashMap<>();

        for (int i = 0; i < concurrency; i++) {
            boolean isComplete = i % 2 == 0;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (isComplete) {
                        approach.complete(tableToken, songA);
                    } else {
                        approach.skip(tableToken, songA);
                    }
                } catch (GokchuException e) {
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    unexpected.merge(e.getClass().getSimpleName(), 1, Integer::sum);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        long startedAt = System.nanoTime();
        start.countDown();
        done.await();
        long elapsedNanos = System.nanoTime() - startedAt;

        return new TrialOutcome(snapshot(ids.get(1)), elapsedNanos, rejected.get(), Map.copyOf(unexpected));
    }

    /** 매장 큐를 비우고 A(PLAYING) + 대기곡 (count-1)개를 requested_at이 겹치지 않게 넣는다. 반환: id 목록(재생 순서). */
    private List<Long> resetQueue(int count) {
        jdbcTemplate.update("DELETE FROM queue_items WHERE store_id = ?", storeId);
        jdbcTemplate.update("UPDATE stores SET current_fallback_video_id = NULL, current_fallback_title = NULL, "
                + "current_fallback_thumbnail_url = NULL WHERE id = ?", storeId);
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String status = i == 0 ? "PLAYING" : "WAITING";
            rows.add(new Object[]{storeId, tableTokenId, "vid" + i, "곡" + i, "thumb", status,
                    Timestamp.valueOf(base.plusSeconds(i))});
        }
        jdbcTemplate.batchUpdate("INSERT INTO queue_items (store_id, table_token_id, video_id, title, thumbnail_url, "
                + "status, requested_at) VALUES (?, ?, ?, ?, ?, ?, ?)", rows);
        return jdbcTemplate.queryForList(
                "SELECT id FROM queue_items WHERE store_id = ? ORDER BY requested_at", Long.class, storeId);
    }

    private QueueSnapshot snapshot(Long expectedNextId) {
        Integer advanced = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM queue_items WHERE store_id = ? AND status IN ('DONE', 'SKIPPED')",
                Integer.class, storeId);
        List<Long> playing = jdbcTemplate.queryForList(
                "SELECT id FROM queue_items WHERE store_id = ? AND status = 'PLAYING' ORDER BY requested_at",
                Long.class, storeId);
        return new QueueSnapshot(advanced, playing, expectedNextId);
    }

    record QueueSnapshot(int advanced, List<Long> playingIds, Long expectedNextId) {

        boolean isCorrect() {
            return advanced == 1 && playingIds.equals(List.of(expectedNextId));
        }

        String playingLabel(List<Long> orderedIds) {
            return playingIds.stream()
                    .map(id -> "곡" + orderedIds.indexOf(id))
                    .toList()
                    .toString();
        }
    }

    record TrialOutcome(QueueSnapshot snapshot, long elapsedNanos, int rejected, Map<String, Integer> unexpected) {
    }

    static final class ExperimentResult {
        private final String name;
        private final int concurrency;
        private final List<Long> elapsedNanos = new ArrayList<>();
        private final Map<String, Integer> unexpected = new ConcurrentHashMap<>();
        private int overAdvancedTrials;
        private int duplicatePlayingTrials;
        private int correctTrials;
        private int maxAdvanced;
        private int maxPlaying;
        private int rejected;

        ExperimentResult(String name, int concurrency) {
            this.name = name;
            this.concurrency = concurrency;
        }

        void record(TrialOutcome outcome) {
            QueueSnapshot s = outcome.snapshot();
            if (s.advanced() >= 2) {
                overAdvancedTrials++;
            }
            if (s.playingIds().size() > 1) {
                duplicatePlayingTrials++;
            }
            if (s.isCorrect()) {
                correctTrials++;
            }
            maxAdvanced = Math.max(maxAdvanced, s.advanced());
            maxPlaying = Math.max(maxPlaying, s.playingIds().size());
            rejected += outcome.rejected();
            outcome.unexpected().forEach((k, v) -> unexpected.merge(k, v, Integer::sum));
            elapsedNanos.add(outcome.elapsedNanos());
        }

        int overAdvancedTrials() {
            return overAdvancedTrials;
        }

        int duplicatePlayingTrials() {
            return duplicatePlayingTrials;
        }

        String toMarkdownRow() {
            List<Long> sorted = elapsedNanos.stream().sorted().toList();
            long total = sorted.stream().mapToLong(Long::longValue).sum();
            long p95 = sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
            String others = unexpected.isEmpty() ? "0" : unexpected.toString();
            return String.format("| %s | %d | %d | %d | %d | %d | %d | %d | %s | %.2f | %.2f | %.0f |",
                    name, concurrency, overAdvancedTrials, duplicatePlayingTrials, correctTrials, maxAdvanced, maxPlaying,
                    rejected, others, toMillis(total) / sorted.size(), toMillis(p95), toMillis(total));
        }

        private static double toMillis(long nanos) {
            return nanos / 1_000_000.0;
        }
    }
}
