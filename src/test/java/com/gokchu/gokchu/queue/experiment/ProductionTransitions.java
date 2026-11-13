package com.gokchu.gokchu.queue.experiment;

import com.gokchu.gokchu.queue.QueueService;
import com.gokchu.gokchu.queue.dto.NowPlayingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 해결책 ①을 실제 운영 코드(QueueService)에 적용한 결과. 실험용 ① 구현과 같은 결과·성능이 나오는지
 * 확인하려고 같은 하네스에 끼운다. 운영 코드는 매장 소유 검증(skip의 findById, UPDATE의 store_id 조건)과
 * 승격 재시도가 더 붙어 있다.
 */
@Component
@RequiredArgsConstructor
class ProductionTransitions implements NowPlayingTransitions {

    private final QueueService queueService;

    @Override
    public String name() {
        return "운영 코드 (QueueService, ① 적용)";
    }

    @Override
    public Optional<NowPlayingResponse> complete(UUID tableToken, Long finishedQueueItemId) {
        return queueService.completeQueueItem(tableToken, finishedQueueItemId);
    }

    @Override
    public Optional<NowPlayingResponse> skip(UUID tableToken, Long queueItemId) {
        return queueService.skip(tableToken, queueItemId);
    }
}
