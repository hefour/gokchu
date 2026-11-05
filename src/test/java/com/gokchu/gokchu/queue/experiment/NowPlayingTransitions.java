package com.gokchu.gokchu.queue.experiment;

import com.gokchu.gokchu.queue.dto.NowPlayingResponse;

import java.util.Optional;
import java.util.UUID;

/**
 * 실험 대상: "재생 완료 통보"와 "스킵"이라는 두 가지 now-playing 전환.
 * 같은 동시성 테스트에 구현만 바꿔 끼우기 위한 공통 인터페이스.
 *
 * <p>complete에도 queueItemId를 받는다. 현재 운영 코드(baseline)는 이 값을 무시하고
 * "지금 재생중인 곡"을 끝내지만, 해결책들은 "요청한 곡이 아직 재생중일 때만" 넘긴다.
 */
public interface NowPlayingTransitions {

    String name();

    Optional<NowPlayingResponse> complete(UUID tableToken, Long finishedQueueItemId);

    Optional<NowPlayingResponse> skip(UUID tableToken, Long queueItemId);
}
