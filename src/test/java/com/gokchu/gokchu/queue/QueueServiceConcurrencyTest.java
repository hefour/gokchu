package com.gokchu.gokchu.queue;

import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import com.gokchu.gokchu.queue.dto.NowPlayingResponse;
import com.gokchu.gokchu.store.Store;
import com.gokchu.gokchu.store.StoreRepository;
import com.gokchu.gokchu.support.PostgresTestcontainersConfig;
import com.gokchu.gokchu.tabletoken.TableToken;
import com.gokchu.gokchu.tabletoken.TableTokenRepository;
import com.gokchu.gokchu.video.YoutubeApiClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 PostgreSQL(Testcontainers)에서 complete/skip(신청곡, 폴백 모두)이 겹쳐도 곡이 하나만 넘어가는지 확인하는 회귀 테스트.
 * docs/concurrency-experiment.md에서 재현한 문제(곡 2개 이상 이동, PLAYING 중복)가 다시 생기지 않게 막는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueServiceConcurrencyTest {

    private static final int TRIALS = 20;
    private static final int CONCURRENT_REQUESTS = 20;
    private static final long TIMEOUT_SECONDS = 30;
    private static final String FALLBACK_A = "fallbackAAA";
    private static final String FALLBACK_B = "fallbackBBB";

    @MockitoBean
    YoutubeApiClient youtubeApiClient;

    @Autowired QueueService queueService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired StoreRepository storeRepository;
    @Autowired TableTokenRepository tableTokenRepository;

    private Long storeId;
    private Long tableTokenId;
    private UUID tableToken;

    @BeforeAll
    void setUpStore() {
        Store store = storeRepository.save(Store.builder().name("동시성 테스트 매장").regionCode("KR").build());
        TableToken table = tableTokenRepository.save(TableToken.builder().store(store).label("1번").build());
        storeId = store.getId();
        tableTokenId = table.getId();
        tableToken = table.getToken();
    }

    @Test
    void 스킵이_먼저_처리된_뒤_같은_곡의_complete가_늦게_와도_한_곡만_넘어간다() {
        List<Long> ids = resetQueue(storeId, tableTokenId, 3);

        queueService.skip(tableToken, ids.get(0));
        Optional<NowPlayingResponse> response = queueService.completeQueueItem(tableToken, ids.get(0));

        assertThat(advancedCount(storeId)).isEqualTo(1);
        assertThat(playingIds(storeId)).containsExactly(ids.get(1));
        assertThat(response).map(NowPlayingResponse::queueItemId).contains(ids.get(1));
    }

    @Test
    void 같은_곡의_complete가_두_번_와도_한_곡만_넘어간다() {
        List<Long> ids = resetQueue(storeId, tableTokenId, 3);

        queueService.completeQueueItem(tableToken, ids.get(0));
        queueService.completeQueueItem(tableToken, ids.get(0));

        assertThat(advancedCount(storeId)).isEqualTo(1);
        assertThat(playingIds(storeId)).containsExactly(ids.get(1));
    }

    @Test
    void complete와_skip이_동시에_몰려도_매번_한_곡만_넘어가고_PLAYING은_하나다() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        try {
            for (int trial = 0; trial < TRIALS; trial++) {
                List<Long> ids = resetQueue(storeId, tableTokenId, CONCURRENT_REQUESTS + 2);
                Long songA = ids.get(0);
                Queue<Throwable> unexpected = runConcurrently(executor,
                        () -> queueService.completeQueueItem(tableToken, songA),
                        () -> queueService.skip(tableToken, songA),
                        Set.of(ErrorCode.QUEUE_ITEM_ALREADY_FINISHED));

                assertThat(unexpected).as("trial %d 예상 밖 예외", trial).isEmpty();
                assertThat(advancedCount(storeId)).as("trial %d 넘어간 곡 수", trial).isEqualTo(1);
                assertThat(playingIds(storeId)).as("trial %d PLAYING", trial).containsExactly(ids.get(1));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 다른_매장의_큐항목_id로_complete를_보내면_아무것도_바꾸지_않는다() {
        Store otherStore = storeRepository.save(Store.builder().name("다른 매장").regionCode("KR").build());
        TableToken otherTable = tableTokenRepository.save(TableToken.builder().store(otherStore).label("1번").build());
        List<Long> otherIds = resetQueue(otherStore.getId(), otherTable.getId(), 2);
        List<Long> ids = resetQueue(storeId, tableTokenId, 2);

        queueService.completeQueueItem(tableToken, otherIds.get(0));

        assertThat(playingIds(otherStore.getId())).containsExactly(otherIds.get(0));
        assertThat(playingIds(storeId)).containsExactly(ids.get(0));
    }

    @Test
    void 신청곡이_재생중일_때_폴백_종료가_오면_아무것도_바꾸지_않는다() {
        List<Long> ids = resetQueue(storeId, tableTokenId, 2);

        Optional<NowPlayingResponse> response = queueService.completeFallback(tableToken, FALLBACK_A);

        assertThat(advancedCount(storeId)).isZero();
        assertThat(playingIds(storeId)).containsExactly(ids.get(0));
        assertThat(response).map(NowPlayingResponse::queueItemId).contains(ids.get(0));
    }

    @Test
    void 같은_폴백의_종료가_두_번_와도_폴백은_한_곡만_넘어간다() {
        resetWaitingQueue(0);
        setCurrentFallback(FALLBACK_A);
        jdbcTemplate.update("DELETE FROM fallback_tracks WHERE store_id = ?", storeId);
        jdbcTemplate.update("INSERT INTO fallback_tracks (store_id, video_id, title, thumbnail_url) VALUES "
                + "(?, ?, 'A', 'thumb'), (?, ?, 'B', 'thumb')", storeId, FALLBACK_A, storeId, FALLBACK_B);

        queueService.completeFallback(tableToken, FALLBACK_A);
        // B가 재생되는 동안 신청곡이 들어왔다. B가 끝날 때까지는 대기해야 한다.
        List<Long> waiting = insertWaitingWithoutReset(1);
        Optional<NowPlayingResponse> duplicate = queueService.completeFallback(tableToken, FALLBACK_A);

        // 중복 통보가 처리됐다면 B를 끊고 신청곡을 재생했을 것이다
        assertThat(currentFallback()).isEqualTo(FALLBACK_B);
        assertThat(playingIds(storeId)).isEmpty();
        assertThat(duplicate).map(NowPlayingResponse::videoId).contains(FALLBACK_B);
        assertThat(waiting).hasSize(1);
    }

    @Test
    void 폴백_종료와_관리자_폴백_스킵이_동시에_몰려도_대기곡은_하나만_재생된다() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        try {
            for (int trial = 0; trial < TRIALS; trial++) {
                List<Long> ids = resetWaitingQueue(CONCURRENT_REQUESTS + 2);
                setCurrentFallback(FALLBACK_A);
                Queue<Throwable> unexpected = runConcurrently(executor,
                        () -> queueService.completeFallback(tableToken, FALLBACK_A),
                        () -> queueService.skipFallback(tableToken, FALLBACK_A),
                        Set.of(ErrorCode.FALLBACK_NOT_PLAYING, ErrorCode.FALLBACK_TRACK_ALREADY_FINISHED));

                assertThat(unexpected).as("trial %d 예상 밖 예외", trial).isEmpty();
                assertThat(playingIds(storeId)).as("trial %d PLAYING", trial).containsExactly(ids.get(0));
                assertThat(currentFallback()).as("trial %d 폴백", trial).isNull();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 요청 절반은 {@code first}, 절반은 {@code second}를 한꺼번에 실행한다. 진 요청이 받는
     * {@code allowedLoserErrors} 외의 예외만 모아 반환한다.
     */
    private Queue<Throwable> runConcurrently(ExecutorService executor, Callable<?> first, Callable<?> second,
                                             Set<ErrorCode> allowedLoserErrors) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENT_REQUESTS);
        Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            Callable<?> action = i % 2 == 0 ? first : second;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    action.call();
                } catch (GokchuException e) {
                    if (!allowedLoserErrors.contains(e.getErrorCode())) {
                        unexpected.add(e);
                    }
                } catch (Throwable e) {
                    unexpected.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        return unexpected;
    }

    /** 매장 큐를 비우고 첫 곡(PLAYING) + 대기곡 (count-1)개를 신청 순서가 겹치지 않게 넣는다. 반환: 재생 순서대로 id. */
    private List<Long> resetQueue(Long targetStoreId, Long targetTableTokenId, int count) {
        return insertQueue(targetStoreId, targetTableTokenId, count, true);
    }

    /** 폴백 재생중인 상황용: 테스트 매장 큐를 대기곡 count개로만 채운다(PLAYING 없음). */
    private List<Long> resetWaitingQueue(int count) {
        return insertQueue(storeId, tableTokenId, count, false);
    }

    private List<Long> insertQueue(Long targetStoreId, Long targetTableTokenId, int count, boolean firstPlaying) {
        jdbcTemplate.update("DELETE FROM queue_items WHERE store_id = ?", targetStoreId);
        setCurrentFallback(targetStoreId, null);
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String status = i == 0 && firstPlaying ? "PLAYING" : "WAITING";
            rows.add(new Object[]{targetStoreId, targetTableTokenId, "vid" + i, "곡" + i, "thumb", status,
                    Timestamp.valueOf(base.plusSeconds(i))});
        }
        jdbcTemplate.batchUpdate("INSERT INTO queue_items (store_id, table_token_id, video_id, title, thumbnail_url, "
                + "status, requested_at) VALUES (?, ?, ?, ?, ?, ?, ?)", rows);
        return jdbcTemplate.queryForList(
                "SELECT id FROM queue_items WHERE store_id = ? ORDER BY requested_at", Long.class, targetStoreId);
    }

    private int advancedCount(Long targetStoreId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM queue_items WHERE store_id = ? AND status IN ('DONE', 'SKIPPED')",
                Integer.class, targetStoreId);
        return count == null ? 0 : count;
    }

    /** 현재 상태(폴백 등)를 건드리지 않고 테스트 매장에 대기곡만 추가한다. */
    private List<Long> insertWaitingWithoutReset(int count) {
        LocalDateTime base = LocalDateTime.now();
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update("INSERT INTO queue_items (store_id, table_token_id, video_id, title, thumbnail_url, "
                            + "status, requested_at) VALUES (?, ?, ?, ?, 'thumb', 'WAITING', ?)",
                    storeId, tableTokenId, "late" + i, "늦은 곡" + i, Timestamp.valueOf(base.plusSeconds(i)));
        }
        return jdbcTemplate.queryForList(
                "SELECT id FROM queue_items WHERE store_id = ? AND status = 'WAITING'", Long.class, storeId);
    }

    private void setCurrentFallback(String videoId) {
        setCurrentFallback(storeId, videoId);
    }

    private void setCurrentFallback(Long targetStoreId, String videoId) {
        String title = videoId == null ? null : "폴백 " + videoId;
        String thumbnail = videoId == null ? null : "thumb";
        jdbcTemplate.update("UPDATE stores SET current_fallback_video_id = ?, current_fallback_title = ?, "
                + "current_fallback_thumbnail_url = ? WHERE id = ?", videoId, title, thumbnail, targetStoreId);
    }

    private String currentFallback() {
        return jdbcTemplate.queryForObject(
                "SELECT current_fallback_video_id FROM stores WHERE id = ?", String.class, storeId);
    }

    private List<Long> playingIds(Long targetStoreId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM queue_items WHERE store_id = ? AND status = 'PLAYING' ORDER BY requested_at",
                Long.class, targetStoreId);
    }
}
