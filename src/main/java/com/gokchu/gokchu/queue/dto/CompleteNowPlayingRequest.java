package com.gokchu.gokchu.queue.dto;

/**
 * TV가 "어떤 곡이 끝났는지"를 알리는 요청. now-playing 응답의 queueItemId를 그대로 보낸다.
 *
 * @param queueItemId 끝난 신청곡 id. 끝난 게 폴백 트랙이면 null(폴백은 queueItemId가 없다)
 */
public record CompleteNowPlayingRequest(Long queueItemId) {
}
