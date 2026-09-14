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
     * 현재 재생중이던 것(신청곡 또는 폴백)이 끝났음을 통보받아 다음으로 넘어간다.
     * 대기중인 신청곡이 있으면 그걸 재생하고, 없으면 폴백으로(또는 폴백 안에서 다음 곡으로) 넘어간다.
     */
    @Transactional
    public Optional<NowPlayingResponse> completeNowPlaying(UUID tableToken) {
        TableToken table = findTable(tableToken);
        Store store = table.getStore();
        Long storeId = store.getId();

        String justFinishedFallbackVideoId = null;
        Optional<QueueItem> playing = findPlayingQueueItem(storeId);
        if (playing.isPresent()) {
            playing.get().finish();
        } else if (store.hasFallbackPlaying()) {
            justFinishedFallbackVideoId = store.getCurrentFallbackVideoId();
            store.clearFallback();
        }

        Optional<NowPlayingResponse> promoted = promoteNextWaiting(storeId);
        if (promoted.isPresent()) {
            return promoted;
        }
        return startFallback(store, justFinishedFallbackVideoId);
    }

    @Transactional
    public Optional<NowPlayingResponse> skip(UUID tableToken, Long queueItemId) {
        TableToken table = findTable(tableToken);
        Store store = table.getStore();
        Long storeId = store.getId();
        QueueItem item = findQueueItem(storeId, queueItemId);

        if (item.getStatus() == QueueItemStatus.DONE || item.getStatus() == QueueItemStatus.SKIPPED) {
            throw new GokchuException(ErrorCode.QUEUE_ITEM_ALREADY_FINISHED);
        }

        boolean wasPlaying = item.getStatus() == QueueItemStatus.PLAYING;
        item.skip();

        if (!wasPlaying) {
            return currentNowPlaying(store);
        }

        Optional<NowPlayingResponse> promoted = promoteNextWaiting(storeId);
        if (promoted.isPresent()) {
            return promoted;
        }
        return startFallback(store, null);
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

    private Optional<NowPlayingResponse> promoteNextWaiting(Long storeId) {
        return queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(storeId, QueueItemStatus.WAITING)
                .map(next -> {
                    next.play();
                    return NowPlayingResponse.ofQueueItem(next);
                });
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
