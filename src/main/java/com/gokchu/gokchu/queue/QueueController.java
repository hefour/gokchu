package com.gokchu.gokchu.queue;

import com.gokchu.gokchu.queue.dto.AddQueueItemRequest;
import com.gokchu.gokchu.queue.dto.NowPlayingResponse;
import com.gokchu.gokchu.queue.dto.QueueItemResponse;
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
    public ResponseEntity<NowPlayingResponse> completeNowPlaying(@PathVariable UUID tableToken) {
        return queueService.completeNowPlaying(tableToken)
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
