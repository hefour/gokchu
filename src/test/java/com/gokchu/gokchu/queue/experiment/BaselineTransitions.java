package com.gokchu.gokchu.queue.experiment;

import com.gokchu.gokchu.queue.QueueService;
import com.gokchu.gokchu.queue.dto.NowPlayingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** 현재 운영 코드 그대로. complete는 곡 ID 없이 "지금 재생중인 것"을 끝낸다. */
@Component
@RequiredArgsConstructor
class BaselineTransitions implements NowPlayingTransitions {

    private final QueueService queueService;

    @Override
    public String name() {
        return "baseline (현재 코드)";
    }

    @Override
    public Optional<NowPlayingResponse> complete(UUID tableToken, Long finishedQueueItemId) {
        return queueService.completeNowPlaying(tableToken);
    }

    @Override
    public Optional<NowPlayingResponse> skip(UUID tableToken, Long queueItemId) {
        return queueService.skip(tableToken, queueItemId);
    }
}
