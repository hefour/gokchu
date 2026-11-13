package com.gokchu.gokchu.queue;

import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import com.gokchu.gokchu.fallback.FallbackTrack;
import com.gokchu.gokchu.fallback.FallbackTrackRepository;
import com.gokchu.gokchu.queue.dto.AddQueueItemRequest;
import com.gokchu.gokchu.queue.dto.NowPlayingResponse;
import com.gokchu.gokchu.queue.dto.NowPlayingSource;
import com.gokchu.gokchu.queue.dto.QueueItemResponse;
import com.gokchu.gokchu.store.Store;
import com.gokchu.gokchu.tabletoken.TableToken;
import com.gokchu.gokchu.tabletoken.TableTokenRepository;
import com.gokchu.gokchu.video.CachedVideo;
import com.gokchu.gokchu.video.VideoValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueueServiceTest {

    private static final Long STORE_ID = 1L;

    @Mock
    private TableTokenRepository tableTokenRepository;

    @Mock
    private QueueItemRepository queueItemRepository;

    @Mock
    private FallbackTrackRepository fallbackTrackRepository;

    @Mock
    private VideoValidationService videoValidationService;

    @InjectMocks
    private QueueService queueService;

    private Store store;
    private TableToken table;
    private UUID tableToken;

    @BeforeEach
    void setUp() {
        store = Store.builder().name("데모 매장").regionCode("KR").build();
        ReflectionTestUtils.setField(store, "id", STORE_ID);

        table = TableToken.builder().store(store).label("1번 테이블").build();
        ReflectionTestUtils.setField(table, "id", 10L);
        tableToken = table.getToken();

        when(tableTokenRepository.findByToken(tableToken)).thenReturn(Optional.of(table));
        when(queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(STORE_ID, QueueItemStatus.PLAYING))
                .thenReturn(Optional.empty());
        when(queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(STORE_ID, QueueItemStatus.WAITING))
                .thenReturn(Optional.empty());
        when(fallbackTrackRepository.findByStoreId(STORE_ID)).thenReturn(List.of());
    }

    private QueueItem queueItem(long id, QueueItemStatus status) {
        QueueItem item = QueueItem.builder()
                .store(store)
                .tableToken(table)
                .videoId("video" + id)
                .title("title" + id)
                .thumbnailUrl("thumb" + id)
                .build();
        ReflectionTestUtils.setField(item, "id", id);
        ReflectionTestUtils.setField(item, "status", status);
        return item;
    }

    /** 조건부 UPDATE가 {@code from -> to} 전환에 성공(1행)한 것으로 만든다. 스텁하지 않은 전환은 0행이다. */
    private void stubTransition(long id, QueueItemStatus from, QueueItemStatus to) {
        when(queueItemRepository.transitionStatus(id, STORE_ID, from, to)).thenReturn(1);
    }

    private FallbackTrack fallbackTrack(long id, String videoId) {
        FallbackTrack track = FallbackTrack.builder()
                .store(store).videoId(videoId).title("fallback" + id).thumbnailUrl("thumb" + id)
                .build();
        ReflectionTestUtils.setField(track, "id", id);
        return track;
    }

    private void stubVideoValidation() {
        CachedVideo video = CachedVideo.builder()
                .videoId("dQw4w9WgXcQ").title("t").thumbnailUrl("u")
                .embeddable(true).allowedRegions(Set.of()).blockedRegions(Set.of())
                .build();
        when(videoValidationService.validateForPlayback(anyString(), eq("KR"))).thenReturn(video);
    }

    @Test
    void addToQueue_아무것도_재생중이_아니면_새_곡이_바로_재생상태로_등록된다() {
        stubVideoValidation();
        when(queueItemRepository.save(any(QueueItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QueueItemResponse response = queueService.addToQueue(tableToken, new AddQueueItemRequest("dQw4w9WgXcQ"));

        assertThat(response.status()).isEqualTo(QueueItemStatus.PLAYING);
    }

    @Test
    void addToQueue_다른_신청곡이_재생중이면_새_곡은_대기상태로_등록된다() {
        stubVideoValidation();
        when(queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(STORE_ID, QueueItemStatus.PLAYING))
                .thenReturn(Optional.of(queueItem(1L, QueueItemStatus.PLAYING)));
        when(queueItemRepository.save(any(QueueItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QueueItemResponse response = queueService.addToQueue(tableToken, new AddQueueItemRequest("dQw4w9WgXcQ"));

        assertThat(response.status()).isEqualTo(QueueItemStatus.WAITING);
    }

    @Test
    void addToQueue_폴백이_재생중이면_새_곡은_대기상태로_등록된다() {
        stubVideoValidation();
        store.startFallback(fallbackTrack(1L, "fb1"));
        when(queueItemRepository.save(any(QueueItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QueueItemResponse response = queueService.addToQueue(tableToken, new AddQueueItemRequest("dQw4w9WgXcQ"));

        assertThat(response.status()).isEqualTo(QueueItemStatus.WAITING);
    }

    @Test
    void addToQueue_존재하지_않는_테이블이면_예외가_발생한다() {
        UUID unknownToken = UUID.randomUUID();
        when(tableTokenRepository.findByToken(unknownToken)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queueService.addToQueue(unknownToken, new AddQueueItemRequest("dQw4w9WgXcQ")))
                .isInstanceOf(GokchuException.class)
                .extracting(e -> ((GokchuException) e).getErrorCode())
                .isEqualTo(ErrorCode.TABLE_TOKEN_NOT_FOUND);
    }

    @Test
    void getNowPlaying_신청곡이_재생중이면_그것을_반환한다() {
        when(queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(STORE_ID, QueueItemStatus.PLAYING))
                .thenReturn(Optional.of(queueItem(1L, QueueItemStatus.PLAYING)));

        Optional<NowPlayingResponse> response = queueService.getNowPlaying(tableToken);

        assertThat(response).isPresent();
        assertThat(response.get().source()).isEqualTo(NowPlayingSource.QUEUE);
        assertThat(response.get().queueItemId()).isEqualTo(1L);
    }

    @Test
    void getNowPlaying_이미_폴백이_재생중이면_그대로_유지된다() {
        store.startFallback(fallbackTrack(1L, "fb1"));

        Optional<NowPlayingResponse> response = queueService.getNowPlaying(tableToken);

        assertThat(response).isPresent();
        assertThat(response.get().source()).isEqualTo(NowPlayingSource.FALLBACK);
        assertThat(response.get().videoId()).isEqualTo("fb1");
    }

    @Test
    void getNowPlaying_아무것도_재생된적_없고_폴백트랙이_있으면_새로_시작한다() {
        when(fallbackTrackRepository.findByStoreId(STORE_ID)).thenReturn(List.of(fallbackTrack(1L, "fb1")));

        Optional<NowPlayingResponse> response = queueService.getNowPlaying(tableToken);

        assertThat(response).isPresent();
        assertThat(response.get().source()).isEqualTo(NowPlayingSource.FALLBACK);
        assertThat(response.get().videoId()).isEqualTo("fb1");
        assertThat(store.hasFallbackPlaying()).isTrue();
    }

    @Test
    void getNowPlaying_아무것도_없고_폴백트랙도_없으면_빈값을_반환한다() {
        Optional<NowPlayingResponse> response = queueService.getNowPlaying(tableToken);

        assertThat(response).isEmpty();
    }

    @Test
    void completeNowPlaying_끝난_신청곡이_아직_재생중이면_DONE으로_바꾸고_대기곡으로_전환한다() {
        QueueItem next = queueItem(2L, QueueItemStatus.WAITING);
        stubTransition(1L, QueueItemStatus.PLAYING, QueueItemStatus.DONE);
        stubTransition(2L, QueueItemStatus.WAITING, QueueItemStatus.PLAYING);
        when(queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(STORE_ID, QueueItemStatus.WAITING))
                .thenReturn(Optional.of(next));

        Optional<NowPlayingResponse> response = queueService.completeNowPlaying(tableToken, 1L);

        verify(queueItemRepository).transitionStatus(1L, STORE_ID, QueueItemStatus.PLAYING, QueueItemStatus.DONE);
        verify(queueItemRepository).transitionStatus(2L, STORE_ID, QueueItemStatus.WAITING, QueueItemStatus.PLAYING);
        assertThat(response).isPresent();
        assertThat(response.get().source()).isEqualTo(NowPlayingSource.QUEUE);
        assertThat(response.get().queueItemId()).isEqualTo(2L);
    }

    @Test
    void completeNowPlaying_대기곡이_없고_폴백트랙이_있으면_폴백으로_전환한다() {
        stubTransition(1L, QueueItemStatus.PLAYING, QueueItemStatus.DONE);
        when(fallbackTrackRepository.findByStoreId(STORE_ID)).thenReturn(List.of(fallbackTrack(1L, "fb1")));

        Optional<NowPlayingResponse> response = queueService.completeNowPlaying(tableToken, 1L);

        assertThat(response).isPresent();
        assertThat(response.get().source()).isEqualTo(NowPlayingSource.FALLBACK);
        assertThat(response.get().videoId()).isEqualTo("fb1");
    }

    @Test
    void completeNowPlaying_대기곡도_폴백트랙도_없으면_빈값을_반환한다() {
        stubTransition(1L, QueueItemStatus.PLAYING, QueueItemStatus.DONE);

        Optional<NowPlayingResponse> response = queueService.completeNowPlaying(tableToken, 1L);

        assertThat(response).isEmpty();
    }

    @Test
    void completeNowPlaying_이미_처리된_곡이면_다음곡을_올리지_않고_현재_재생곡을_반환한다() {
        // 스킵이 먼저 처리돼 곡2가 재생중인데, 곡1의 complete가 뒤늦게 도착한 상황
        QueueItem nowPlaying = queueItem(2L, QueueItemStatus.PLAYING);
        when(queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(STORE_ID, QueueItemStatus.PLAYING))
                .thenReturn(Optional.of(nowPlaying));

        Optional<NowPlayingResponse> response = queueService.completeNowPlaying(tableToken, 1L);

        verify(queueItemRepository, never()).transitionStatus(
                anyLong(), eq(STORE_ID), eq(QueueItemStatus.WAITING), eq(QueueItemStatus.PLAYING));
        verify(fallbackTrackRepository, never()).findByStoreId(any());
        assertThat(response).isPresent();
        assertThat(response.get().queueItemId()).isEqualTo(2L);
    }

    @Test
    void completeNowPlaying_승격하려던_대기곡이_그새_스킵되면_다음_대기곡을_올린다() {
        QueueItem skippedMeanwhile = queueItem(2L, QueueItemStatus.WAITING);
        QueueItem next = queueItem(3L, QueueItemStatus.WAITING);
        stubTransition(1L, QueueItemStatus.PLAYING, QueueItemStatus.DONE);
        stubTransition(3L, QueueItemStatus.WAITING, QueueItemStatus.PLAYING);
        when(queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(STORE_ID, QueueItemStatus.WAITING))
                .thenReturn(Optional.of(skippedMeanwhile), Optional.of(next));

        Optional<NowPlayingResponse> response = queueService.completeNowPlaying(tableToken, 1L);

        assertThat(response).isPresent();
        assertThat(response.get().queueItemId()).isEqualTo(3L);
    }

    @Test
    void completeNowPlaying_폴백재생중_대기곡이_생기면_폴백을_끝내고_대기곡으로_전환한다() {
        store.startFallback(fallbackTrack(1L, "fb1"));
        QueueItem waiting = queueItem(2L, QueueItemStatus.WAITING);
        stubTransition(2L, QueueItemStatus.WAITING, QueueItemStatus.PLAYING);
        when(queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(STORE_ID, QueueItemStatus.WAITING))
                .thenReturn(Optional.of(waiting));

        Optional<NowPlayingResponse> response = queueService.completeNowPlaying(tableToken, null);

        assertThat(store.hasFallbackPlaying()).isFalse();
        assertThat(response).isPresent();
        assertThat(response.get().source()).isEqualTo(NowPlayingSource.QUEUE);
        assertThat(response.get().queueItemId()).isEqualTo(2L);
    }

    @Test
    void completeNowPlaying_폴백재생중_대기곡이_없으면_다른_폴백트랙으로_넘어간다() {
        FallbackTrack finished = fallbackTrack(1L, "fb1");
        FallbackTrack other = fallbackTrack(2L, "fb2");
        store.startFallback(finished);
        when(fallbackTrackRepository.findByStoreId(STORE_ID)).thenReturn(List.of(finished, other));

        Optional<NowPlayingResponse> response = queueService.completeNowPlaying(tableToken, null);

        assertThat(response).isPresent();
        assertThat(response.get().source()).isEqualTo(NowPlayingSource.FALLBACK);
        assertThat(response.get().videoId()).isEqualTo("fb2");
    }

    @Test
    void completeNowPlaying_폴백종료가_왔는데_신청곡이_재생중이면_아무것도_바꾸지_않는다() {
        QueueItem playing = queueItem(1L, QueueItemStatus.PLAYING);
        when(queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(STORE_ID, QueueItemStatus.PLAYING))
                .thenReturn(Optional.of(playing));

        Optional<NowPlayingResponse> response = queueService.completeNowPlaying(tableToken, null);

        verify(queueItemRepository, never()).transitionStatus(any(), any(), any(), any());
        assertThat(response).isPresent();
        assertThat(response.get().queueItemId()).isEqualTo(1L);
    }

    @Test
    void skip_재생중인_곡을_스킵하면_다음_대기곡이_재생상태로_승격한다() {
        QueueItem playing = queueItem(1L, QueueItemStatus.PLAYING);
        QueueItem next = queueItem(2L, QueueItemStatus.WAITING);
        when(queueItemRepository.findById(1L)).thenReturn(Optional.of(playing));
        stubTransition(1L, QueueItemStatus.PLAYING, QueueItemStatus.SKIPPED);
        stubTransition(2L, QueueItemStatus.WAITING, QueueItemStatus.PLAYING);
        when(queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(STORE_ID, QueueItemStatus.WAITING))
                .thenReturn(Optional.of(next));

        Optional<NowPlayingResponse> response = queueService.skip(tableToken, 1L);

        verify(queueItemRepository).transitionStatus(1L, STORE_ID, QueueItemStatus.PLAYING, QueueItemStatus.SKIPPED);
        assertThat(response).isPresent();
        assertThat(response.get().queueItemId()).isEqualTo(2L);
    }

    @Test
    void skip_재생중인_곡을_스킵했는데_대기곡이_없으면_폴백으로_전환한다() {
        QueueItem playing = queueItem(1L, QueueItemStatus.PLAYING);
        when(queueItemRepository.findById(1L)).thenReturn(Optional.of(playing));
        stubTransition(1L, QueueItemStatus.PLAYING, QueueItemStatus.SKIPPED);
        when(fallbackTrackRepository.findByStoreId(STORE_ID)).thenReturn(List.of(fallbackTrack(1L, "fb1")));

        Optional<NowPlayingResponse> response = queueService.skip(tableToken, 1L);

        assertThat(response).isPresent();
        assertThat(response.get().source()).isEqualTo(NowPlayingSource.FALLBACK);
    }

    @Test
    void skip_대기중인_곡을_스킵하면_현재_재생상태는_바뀌지_않는다() {
        QueueItem waiting = queueItem(2L, QueueItemStatus.WAITING);
        QueueItem playing = queueItem(1L, QueueItemStatus.PLAYING);
        when(queueItemRepository.findById(2L)).thenReturn(Optional.of(waiting));
        stubTransition(2L, QueueItemStatus.WAITING, QueueItemStatus.SKIPPED);
        when(queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(STORE_ID, QueueItemStatus.PLAYING))
                .thenReturn(Optional.of(playing));

        Optional<NowPlayingResponse> response = queueService.skip(tableToken, 2L);

        verify(queueItemRepository).transitionStatus(2L, STORE_ID, QueueItemStatus.WAITING, QueueItemStatus.SKIPPED);
        verify(queueItemRepository, never()).transitionStatus(
                anyLong(), eq(STORE_ID), eq(QueueItemStatus.WAITING), eq(QueueItemStatus.PLAYING));
        assertThat(response).isPresent();
        assertThat(response.get().queueItemId()).isEqualTo(1L);
    }

    @Test
    void skip_이미_종료된_큐항목이면_예외가_발생한다() {
        // 두 조건부 UPDATE가 모두 0행 = 먼저 온 complete나 skip이 이미 처리함
        QueueItem done = queueItem(1L, QueueItemStatus.DONE);
        when(queueItemRepository.findById(1L)).thenReturn(Optional.of(done));

        assertThatThrownBy(() -> queueService.skip(tableToken, 1L))
                .isInstanceOf(GokchuException.class)
                .extracting(e -> ((GokchuException) e).getErrorCode())
                .isEqualTo(ErrorCode.QUEUE_ITEM_ALREADY_FINISHED);
    }

    @Test
    void skip_존재하지_않는_큐항목이면_예외가_발생한다() {
        when(queueItemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queueService.skip(tableToken, 999L))
                .isInstanceOf(GokchuException.class)
                .extracting(e -> ((GokchuException) e).getErrorCode())
                .isEqualTo(ErrorCode.QUEUE_ITEM_NOT_FOUND);
    }

    @Test
    void skipFallback_재생중인_폴백을_건너뛰면_다른_폴백트랙으로_넘어간다() {
        FallbackTrack current = fallbackTrack(1L, "fb1");
        FallbackTrack other = fallbackTrack(2L, "fb2");
        store.startFallback(current);
        when(fallbackTrackRepository.findByStoreId(STORE_ID)).thenReturn(List.of(current, other));

        Optional<NowPlayingResponse> response = queueService.skipFallback(tableToken);

        assertThat(response).isPresent();
        assertThat(response.get().source()).isEqualTo(NowPlayingSource.FALLBACK);
        assertThat(response.get().videoId()).isEqualTo("fb2");
    }

    @Test
    void skipFallback_폴백트랙이_하나뿐이면_같은_트랙을_다시_재생한다() {
        FallbackTrack onlyTrack = fallbackTrack(1L, "fb1");
        store.startFallback(onlyTrack);
        when(fallbackTrackRepository.findByStoreId(STORE_ID)).thenReturn(List.of(onlyTrack));

        Optional<NowPlayingResponse> response = queueService.skipFallback(tableToken);

        assertThat(response).isPresent();
        assertThat(response.get().videoId()).isEqualTo("fb1");
    }

    @Test
    void skipFallback_재생중인_폴백이_없으면_예외가_발생한다() {
        assertThatThrownBy(() -> queueService.skipFallback(tableToken))
                .isInstanceOf(GokchuException.class)
                .extracting(e -> ((GokchuException) e).getErrorCode())
                .isEqualTo(ErrorCode.FALLBACK_NOT_PLAYING);
    }

    @Test
    void skipFallback_신청곡이_재생중이면_예외가_발생한다() {
        when(queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(STORE_ID, QueueItemStatus.PLAYING))
                .thenReturn(Optional.of(queueItem(1L, QueueItemStatus.PLAYING)));

        assertThatThrownBy(() -> queueService.skipFallback(tableToken))
                .isInstanceOf(GokchuException.class)
                .extracting(e -> ((GokchuException) e).getErrorCode())
                .isEqualTo(ErrorCode.FALLBACK_NOT_PLAYING);
    }

    @Test
    void skip_다른_매장_큐항목이면_예외가_발생한다() {
        Store otherStore = Store.builder().name("다른 매장").regionCode("KR").build();
        ReflectionTestUtils.setField(otherStore, "id", 2L);
        QueueItem otherStoreItem = QueueItem.builder()
                .store(otherStore).tableToken(table)
                .videoId("v").title("t").thumbnailUrl("u")
                .build();
        ReflectionTestUtils.setField(otherStoreItem, "id", 1L);
        when(queueItemRepository.findById(1L)).thenReturn(Optional.of(otherStoreItem));

        assertThatThrownBy(() -> queueService.skip(tableToken, 1L))
                .isInstanceOf(GokchuException.class)
                .extracting(e -> ((GokchuException) e).getErrorCode())
                .isEqualTo(ErrorCode.QUEUE_ITEM_NOT_FOUND);
    }
}
