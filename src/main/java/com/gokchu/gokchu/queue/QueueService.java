package com.gokchu.gokchu.queue;

import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import com.gokchu.gokchu.fallback.FallbackTrack;
import com.gokchu.gokchu.fallback.FallbackTrackRepository;
import com.gokchu.gokchu.queue.dto.AddQueueItemRequest;
import com.gokchu.gokchu.queue.dto.NowPlayingResponse;
import com.gokchu.gokchu.queue.dto.QueueItemResponse;
import com.gokchu.gokchu.store.Store;
import com.gokchu.gokchu.tabletoken.TableToken;
import com.gokchu.gokchu.tabletoken.TableTokenRepository;
import com.gokchu.gokchu.video.CachedVideo;
import com.gokchu.gokchu.video.VideoValidationService;
import com.gokchu.gokchu.video.YoutubeLinkParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class QueueService {

    private static final int MAX_PROMOTE_ATTEMPTS = 10;

    private final TableTokenRepository tableTokenRepository;
    private final QueueItemRepository queueItemRepository;
    private final FallbackTrackRepository fallbackTrackRepository;
    private final VideoValidationService videoValidationService;

    @Transactional
    public QueueItemResponse addToQueue(UUID tableToken, AddQueueItemRequest request) {
        TableToken table = findTable(tableToken);
        Store store = table.getStore();
        Long storeId = store.getId();

        String videoId = YoutubeLinkParser.extractVideoId(request.link());
        CachedVideo video = videoValidationService.validateForPlayback(videoId, store.getRegionCode());

        QueueItem queueItem = QueueItem.builder()
                .store(store)
                .tableToken(table)
                .videoId(video.getVideoId())
                .title(video.getTitle())
                .thumbnailUrl(video.getThumbnailUrl())
                .build();
        QueueItem saved = queueItemRepository.save(queueItem);

        if (findPlayingQueueItem(storeId).isEmpty() && !store.hasFallbackPlaying()) {
            saved.play();
        }

        return QueueItemResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<QueueItemResponse> getQueue(UUID tableToken) {
        TableToken table = findTable(tableToken);

        return queueItemRepository
                .findByStoreIdAndStatusInOrderByRequestedAtAsc(
                        table.getStore().getId(),
                        List.of(QueueItemStatus.WAITING, QueueItemStatus.PLAYING)
                )
                .stream()
                .map(QueueItemResponse::from)
                .toList();
    }

    /**
     * 매장의 현재 재생 상태를 조회한다. 신청곡도, 진행중인 폴백도 없다면(예: 매장을 처음 열었을 때)
     * 폴백 재생목록에서 한 곡을 골라 재생을 시작시킨다.
     */
    @Transactional
    public Optional<NowPlayingResponse> getNowPlaying(UUID tableToken) {
        TableToken table = findTable(tableToken);
        Store store = table.getStore();
        Long storeId = store.getId();

        Optional<QueueItem> playing = findPlayingQueueItem(storeId);
        if (playing.isPresent()) {
            return Optional.of(NowPlayingResponse.ofQueueItem(playing.get()));
        }
        if (store.hasFallbackPlaying()) {
            return Optional.of(NowPlayingResponse.ofFallback(store));
        }
        return startFallback(store, null);
    }

    /**
     * TV가 "이 곡이 끝났다"고 알리면 다음으로 넘어간다. 대기중인 신청곡이 있으면 그걸 재생하고,
     * 없으면 폴백으로(또는 폴백 안에서 다음 곡으로) 넘어간다.
     *
     * <p>끝난 곡의 id를 받아 "그 곡이 아직 PLAYING일 때만" DONE으로 바꾼다. 같은 곡의 complete가
     * 중복으로 오거나 스킵과 겹쳐도 곡은 하나만 넘어간다(멱등). 이미 처리된 요청이면 아무것도 바꾸지 않고
     * 지금 재생 상태를 돌려준다. 근거는 docs/concurrency-experiment.md.
     *
     * @param finishedQueueItemId 끝난 신청곡 id. 끝난 게 폴백 트랙이면 null
     */
    @Transactional
    public Optional<NowPlayingResponse> completeNowPlaying(UUID tableToken, Long finishedQueueItemId) {
        Store store = findTable(tableToken).getStore();
        if (finishedQueueItemId != null) {
            return completeQueueItem(store, finishedQueueItemId);
        }
        return completeFallback(store);
    }

    /**
     * 신청곡을 SKIPPED로 바꾸고, 재생중이던 곡이었으면 다음 곡으로 넘어간다. 상태 확인과 변경을 조건부
     * UPDATE 한 문장으로 해서, 같은 곡을 두고 complete와 겹쳐도 다음 곡은 한 번만 올라간다.
     */
    @Transactional
    public Optional<NowPlayingResponse> skip(UUID tableToken, Long queueItemId) {
        Store store = findTable(tableToken).getStore();
        findQueueItem(store.getId(), queueItemId);

        if (transition(store, queueItemId, QueueItemStatus.PLAYING, QueueItemStatus.SKIPPED)) {
            return promoteNextOrStartFallback(store, null);
        }
        if (transition(store, queueItemId, QueueItemStatus.WAITING, QueueItemStatus.SKIPPED)) {
            return currentNowPlaying(store);
        }
        throw new GokchuException(ErrorCode.QUEUE_ITEM_ALREADY_FINISHED);
    }

    /**
     * 관리자가 현재 재생중인 폴백 트랙을 수동으로 건너뛴다. 신청곡이 재생중일 때는 대상이 없으므로
     * (관리자는 그 경우 {@link #skip}을 쓴다) 예외를 던진다.
     */
    @Transactional
    public Optional<NowPlayingResponse> skipFallback(UUID tableToken) {
        TableToken table = findTable(tableToken);
        Store store = table.getStore();

        if (!store.hasFallbackPlaying()) {
            throw new GokchuException(ErrorCode.FALLBACK_NOT_PLAYING);
        }

        String justFinishedFallbackVideoId = store.getCurrentFallbackVideoId();
        store.clearFallback();
        return startFallback(store, justFinishedFallbackVideoId);
    }

    private Optional<NowPlayingResponse> currentNowPlaying(Store store) {
        Optional<QueueItem> playing = findPlayingQueueItem(store.getId());
        if (playing.isPresent()) {
            return Optional.of(NowPlayingResponse.ofQueueItem(playing.get()));
        }
        if (store.hasFallbackPlaying()) {
            return Optional.of(NowPlayingResponse.ofFallback(store));
        }
        return Optional.empty();
    }

    private Optional<NowPlayingResponse> completeQueueItem(Store store, Long finishedQueueItemId) {
        if (!transition(store, finishedQueueItemId, QueueItemStatus.PLAYING, QueueItemStatus.DONE)) {
            return currentNowPlaying(store);
        }
        return promoteNextOrStartFallback(store, null);
    }

    /**
     * 폴백 트랙 종료. 그 사이 신청곡이 재생을 시작했다면 TV가 낡은 상태로 보낸 요청이므로 아무것도 바꾸지
     * 않는다. 같은 폴백 종료가 동시에 두 번 오는 경쟁은 아직 막지 않는다(docs/concurrency-experiment.md 6장).
     */
    private Optional<NowPlayingResponse> completeFallback(Store store) {
        if (findPlayingQueueItem(store.getId()).isPresent()) {
            return currentNowPlaying(store);
        }
        String justFinishedFallbackVideoId = store.getCurrentFallbackVideoId();
        store.clearFallback();
        return promoteNextOrStartFallback(store, justFinishedFallbackVideoId);
    }

    private Optional<NowPlayingResponse> promoteNextOrStartFallback(Store store, String excludeFallbackVideoId) {
        Optional<NowPlayingResponse> promoted = promoteNextWaiting(store);
        if (promoted.isPresent()) {
            return promoted;
        }
        return startFallback(store, excludeFallbackVideoId);
    }

    /**
     * 가장 오래된 WAITING을 PLAYING으로 올린다. 조회와 승격 사이에 그 곡이 스킵되면 승격이 0행이 되므로
     * 다음 WAITING으로 다시 시도한다. 실패한 곡은 이미 WAITING이 아니므로 정상이라면 몇 번 안에 끝난다.
     */
    private Optional<NowPlayingResponse> promoteNextWaiting(Store store) {
        for (int attempt = 0; attempt < MAX_PROMOTE_ATTEMPTS; attempt++) {
            Optional<QueueItem> next = queueItemRepository
                    .findFirstByStoreIdAndStatusOrderByRequestedAtAsc(store.getId(), QueueItemStatus.WAITING);
            if (next.isEmpty()) {
                return Optional.empty();
            }
            if (transition(store, next.get().getId(), QueueItemStatus.WAITING, QueueItemStatus.PLAYING)) {
                return Optional.of(NowPlayingResponse.ofQueueItem(next.get()));
            }
        }
        throw new IllegalStateException(
                "다음 곡 승격이 " + MAX_PROMOTE_ATTEMPTS + "번 연속 실패했습니다. storeId=" + store.getId());
    }

    /** 조건부 UPDATE로 상태를 바꾼다. 실제로 1행을 바꿨을 때만 true. */
    private boolean transition(Store store, Long queueItemId, QueueItemStatus from, QueueItemStatus to) {
        return queueItemRepository.transitionStatus(queueItemId, store.getId(), from, to) == 1;
    }

    private Optional<NowPlayingResponse> startFallback(Store store, String excludeVideoId) {
        List<FallbackTrack> tracks = fallbackTrackRepository.findByStoreId(store.getId());
        if (tracks.isEmpty()) {
            store.clearFallback();
            return Optional.empty();
        }

        FallbackTrack chosen = pickRandomExcluding(tracks, excludeVideoId);
        store.startFallback(chosen);
        return Optional.of(NowPlayingResponse.ofFallback(store));
    }

    private FallbackTrack pickRandomExcluding(List<FallbackTrack> tracks, String excludeVideoId) {
        List<FallbackTrack> candidates = tracks.size() > 1
                ? tracks.stream().filter(track -> !track.getVideoId().equals(excludeVideoId)).toList()
                : tracks;
        if (candidates.isEmpty()) {
            candidates = tracks;
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private Optional<QueueItem> findPlayingQueueItem(Long storeId) {
        return queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(storeId, QueueItemStatus.PLAYING);
    }

    private QueueItem findQueueItem(Long storeId, Long queueItemId) {
        QueueItem item = queueItemRepository.findById(queueItemId)
                .orElseThrow(() -> new GokchuException(ErrorCode.QUEUE_ITEM_NOT_FOUND));

        if (!item.getStore().getId().equals(storeId)) {
            throw new GokchuException(ErrorCode.QUEUE_ITEM_NOT_FOUND);
        }
        return item;
    }

    private TableToken findTable(UUID tableToken) {
        return tableTokenRepository.findByToken(tableToken)
                .orElseThrow(() -> new GokchuException(ErrorCode.TABLE_TOKEN_NOT_FOUND));
    }
}
