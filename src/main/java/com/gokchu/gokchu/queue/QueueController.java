package com.gokchu.gokchu.queue;

import com.gokchu.gokchu.queue.dto.AddQueueItemRequest;
import com.gokchu.gokchu.queue.dto.CompleteNowPlayingRequest;
import com.gokchu.gokchu.queue.dto.NowPlayingResponse;
import com.gokchu.gokchu.queue.dto.QueueItemResponse;
import com.gokchu.gokchu.queue.dto.SkipFallbackRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/tables/{tableToken}")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping("/queue-items")
    @ResponseStatus(HttpStatus.CREATED)
    public QueueItemResponse addToQueue(@PathVariable UUID tableToken, @Valid @RequestBody AddQueueItemRequest request) {
        return queueService.addToQueue(tableToken, request);
    }

    @GetMapping("/queue-items")
    public List<QueueItemResponse> getQueue(@PathVariable UUID tableToken) {
        return queueService.getQueue(tableToken);
    }

    @GetMapping("/now-playing")
    public ResponseEntity<NowPlayingResponse> getNowPlaying(@PathVariable UUID tableToken) {
        return queueService.getNowPlaying(tableToken)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/now-playing/complete")
    public ResponseEntity<NowPlayingResponse> completeNowPlaying(
            @PathVariable UUID tableToken, @Valid @RequestBody CompleteNowPlayingRequest request) {
        Optional<NowPlayingResponse> next = request.queueItemId() != null
                ? queueService.completeQueueItem(tableToken, request.queueItemId())
                : queueService.completeFallback(tableToken, request.fallbackVideoId());
        return next
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/queue-items/{queueItemId}/skip")
    public ResponseEntity<NowPlayingResponse> skip(
            @PathVariable UUID tableToken, @PathVariable Long queueItemId) {
        return queueService.skip(tableToken, queueItemId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * 관리자가 관리자 페이지에서 현재 재생중인 폴백 트랙(배경음악)을 수동으로 건너뛸 때 쓰는
     * 엔드포인트. 폴백은 매장에 최대 1개만 재생되지만, 관리자가 누르는 사이 곡이 바뀔 수 있으므로
     * 화면에 보이던 videoId로 대상을 지정한다.
     */
    @PostMapping("/now-playing/skip-fallback")
    public ResponseEntity<NowPlayingResponse> skipFallback(
            @PathVariable UUID tableToken, @Valid @RequestBody SkipFallbackRequest request) {
        return queueService.skipFallback(tableToken, request.videoId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * TV 플레이어가 재생 불가 영상을 만났을 때 자동으로 호출하는 공개 엔드포인트.
     * 관리자가 수동으로 누르는 {@link #skip}과 로직은 같지만 인증을 요구하지 않는다 —
     * TV는 자격 증명이 없는 공용 기기라서 관리자 전용 엔드포인트를 쓸 수 없다.
     */
    @PostMapping("/queue-items/{queueItemId}/report-unplayable")
    public ResponseEntity<NowPlayingResponse> reportUnplayable(
            @PathVariable UUID tableToken, @PathVariable Long queueItemId) {
        return queueService.skip(tableToken, queueItemId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
