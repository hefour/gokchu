package com.gokchu.gokchu.queue.experiment;

import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
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
 * 해결책 ①: 조건부 UPDATE(compare-and-set). "요청한 곡이 아직 PLAYING일 때만" DONE/SKIPPED로 바꾸고,
 * 실제로 1행을 바꾼 트랜잭션(승자)만 다음 곡을 올린다. 0행이면 이미 누가 처리했으므로 아무것도 하지 않는다(멱등).
 */
@Component
@RequiredArgsConstructor
class ConditionalUpdateTransitions implements NowPlayingTransitions {

    private final TableTokenRepository tableTokenRepository;
    private final QueueItemRepository queueItemRepository;
    private final ExperimentQueueItemRepository experimentRepository;

    @Override
    public String name() {
        return "① 조건부 UPDATE";
    }

    @Override
    @Transactional
    public Optional<NowPlayingResponse> complete(UUID tableToken, Long finishedQueueItemId) {
        Long storeId = findStoreId(tableToken);
        int changed = experimentRepository.transition(finishedQueueItemId, QueueItemStatus.PLAYING, QueueItemStatus.DONE);
        if (changed == 0) {
            return currentQueueItem(storeId);
        }
        return promoteNextWaiting(storeId);
    }

    @Override
    @Transactional
    public Optional<NowPlayingResponse> skip(UUID tableToken, Long queueItemId) {
        Long storeId = findStoreId(tableToken);
        if (experimentRepository.transition(queueItemId, QueueItemStatus.PLAYING, QueueItemStatus.SKIPPED) == 1) {
            return promoteNextWaiting(storeId);
        }
        if (experimentRepository.transition(queueItemId, QueueItemStatus.WAITING, QueueItemStatus.SKIPPED) == 1) {
            return currentQueueItem(storeId);
        }
        throw new GokchuException(ErrorCode.QUEUE_ITEM_ALREADY_FINISHED);
    }

    private Optional<NowPlayingResponse> promoteNextWaiting(Long storeId) {
        return queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(storeId, QueueItemStatus.WAITING)
                .filter(next -> experimentRepository.transition(
                        next.getId(), QueueItemStatus.WAITING, QueueItemStatus.PLAYING) == 1)
                .flatMap(next -> queueItemRepository.findById(next.getId()))
                .map(NowPlayingResponse::ofQueueItem);
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
