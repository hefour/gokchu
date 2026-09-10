package com.gokchu.gokchu.queue;

import com.gokchu.gokchu.queue.dto.AddQueueItemRequest;
import com.gokchu.gokchu.queue.dto.QueueItemResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/tables/{tableToken}/queue-items")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QueueItemResponse addToQueue(@PathVariable UUID tableToken, @Valid @RequestBody AddQueueItemRequest request) {
        return queueService.addToQueue(tableToken, request);
    }

    @GetMapping
    public List<QueueItemResponse> getQueue(@PathVariable UUID tableToken) {
        return queueService.getQueue(tableToken);
    }
}
