package com.gokchu.gokchu.queue.dto;

import jakarta.validation.constraints.AssertTrue;

/**
 * TV가 "어떤 곡이 끝났는지"를 알리는 요청. now-playing 응답을 보고 둘 중 하나만 채운다.
 *
 * @param queueItemId     끝난 게 신청곡이면(source=QUEUE) 그 queueItemId
 * @param fallbackVideoId 끝난 게 폴백 트랙이면(source=FALLBACK) 그 videoId
 */
public record CompleteNowPlayingRequest(Long queueItemId, String fallbackVideoId) {

    @AssertTrue(message = "queueItemId와 fallbackVideoId 중 하나만 보내 주세요.")
    public boolean isExactlyOneTarget() {
        boolean hasQueueItem = queueItemId != null;
        boolean hasFallback = fallbackVideoId != null && !fallbackVideoId.isBlank();
        return hasQueueItem != hasFallback;
    }
}
