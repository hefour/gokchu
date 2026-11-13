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
 * 수정 전 운영 코드(QueueService, 9ddaa81 시점)의 complete/skip을 그대로 옮겨 둔 기준선.
 * complete는 곡 ID 없이 "지금 재생중인 것"을 끝내고, 판단(SELECT)과 쓰기(dirty checking)가 분리돼 있다.
 * 운영 코드는 조건부 UPDATE로 바뀌었지만, 실험을 다시 돌려도 같은 비교가 나오도록 여기 남겨 둔다.
 * 실험 데이터에 폴백 트랙이 없으므로 폴백 분기는 뺐다(원래 코드도 이 경우 빈값을 반환했다).
 */
@Component
@RequiredArgsConstructor
class BaselineTransitions implements NowPlayingTransitions {

    private final TableTokenRepository tableTokenRepository;
    private final QueueItemRepository queueItemRepository;

    @Override
    public String name() {
        return "baseline (수정 전 코드)";
    }

    @Override
    @Transactional
    public Optional<NowPlayingResponse> complete(UUID tableToken, Long finishedQueueItemId) {
        Long storeId = findStoreId(tableToken);
        queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(storeId, QueueItemStatus.PLAYING)
                .ifPresent(QueueItem::finish);
        return promoteNextWaiting(storeId);
    }

    @Override
    @Transactional
    public Optional<NowPlayingResponse> skip(UUID tableToken, Long queueItemId) {
        Long storeId = findStoreId(tableToken);
        QueueItem item = queueItemRepository.findById(queueItemId)
                .orElseThrow(() -> new GokchuException(ErrorCode.QUEUE_ITEM_NOT_FOUND));
        if (item.getStatus() == QueueItemStatus.DONE || item.getStatus() == QueueItemStatus.SKIPPED) {
            throw new GokchuException(ErrorCode.QUEUE_ITEM_ALREADY_FINISHED);
        }
        boolean wasPlaying = item.getStatus() == QueueItemStatus.PLAYING;
        item.skip();
        if (!wasPlaying) {
            return queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(storeId, QueueItemStatus.PLAYING)
                    .map(NowPlayingResponse::ofQueueItem);
        }
        return promoteNextWaiting(storeId);
    }

    private Optional<NowPlayingResponse> promoteNextWaiting(Long storeId) {
        return queueItemRepository.findFirstByStoreIdAndStatusOrderByRequestedAtAsc(storeId, QueueItemStatus.WAITING)
                .map(next -> {
                    next.play();
                    return NowPlayingResponse.ofQueueItem(next);
                });
    }

    private Long findStoreId(UUID tableToken) {
        return tableTokenRepository.findByToken(tableToken)
                .orElseThrow(() -> new GokchuException(ErrorCode.TABLE_TOKEN_NOT_FOUND))
                .getStore().getId();
    }
}
