package com.gokchu.gokchu.queue.experiment;

import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import com.gokchu.gokchu.queue.QueueItem;
import com.gokchu.gokchu.queue.QueueItemRepository;
import com.gokchu.gokchu.queue.QueueItemStatus;
import com.gokchu.gokchu.queue.dto.NowPlayingResponse;
import com.gokchu.gokchu.tabletoken.TableTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 해결책 ②: 비관적 락. 대상 곡 행을 SELECT ... FOR UPDATE로 먼저 잠그고, 락을 잡은 뒤에 상태를 확인한다.
 * 같은 곡에 대한 요청은 이 행 락에서 줄을 서므로, 뒤에 온 요청은 앞 요청이 커밋한 최신 상태(DONE/SKIPPED)를 보게 된다.
 * 나머지 흐름(dirty checking으로 상태 변경)은 운영 코드와 같다.
 */
@Component
@RequiredArgsConstructor
class PessimisticLockTransitions implements NowPlayingTransitions {

    private final TableTokenRepository tableTokenRepository;
    private final QueueItemRepository queueItemRepository;
    private final ExperimentQueueItemRepository experimentRepository;

    @Override
    public String name() {
        return "② 비관적 락 (FOR UPDATE)";
    }

    @Override
    @Transactional
    public Optional<NowPlayingResponse> complete(UUID tableToken, Long finishedQueueItemId) {
        Long storeId = findStoreId(tableToken);
        QueueItem item = lockQueueItem(finishedQueueItemId);
        if (item.getStatus() != QueueItemStatus.PLAYING) {
            return currentQueueItem(storeId);
        }
        item.finish();
        return promoteNextWaiting(storeId);
    }

    @Override
    @Transactional
    public Optional<NowPlayingResponse> skip(UUID tableToken, Long queueItemId) {
        Long storeId = findStoreId(tableToken);
        QueueItem item = lockQueueItem(queueItemId);
        if (item.getStatus() == QueueItemStatus.DONE || item.getStatus() == QueueItemStatus.SKIPPED) {
            throw new GokchuException(ErrorCode.QUEUE_ITEM_ALREADY_FINISHED);
        }
        boolean wasPlaying = item.getStatus() == QueueItemStatus.PLAYING;
        item.skip();
        return wasPlaying ? promoteNextWaiting(storeId) : currentQueueItem(storeId);
    }

    private QueueItem lockQueueItem(Long queueItemId) {
        return experimentRepository.findByIdForUpdate(queueItemId)
                .orElseThrow(() -> new GokchuException(ErrorCode.QUEUE_ITEM_NOT_FOUND));
    }

    private Optional<NowPlayingResponse> promoteNextWaiting(Long storeId) {
        return queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(storeId, QueueItemStatus.WAITING)
                .map(next -> {
                    next.play();
                    return NowPlayingResponse.ofQueueItem(next);
                });
    }

    private Optional<NowPlayingResponse> currentQueueItem(Long storeId) {
        return queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(storeId, QueueItemStatus.PLAYING)
                .map(NowPlayingResponse::ofQueueItem);
    }

    private Long findStoreId(UUID tableToken) {
        return tableTokenRepository.findByToken(tableToken)
                .orElseThrow(() -> new GokchuException(ErrorCode.TABLE_TOKEN_NOT_FOUND))
                .getStore().getId();
    }
}
