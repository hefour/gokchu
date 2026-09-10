package com.gokchu.gokchu.queue.dto;

import com.gokchu.gokchu.queue.QueueItem;
import com.gokchu.gokchu.queue.QueueItemStatus;

import java.time.LocalDateTime;

public record QueueItemResponse(
        Long id,
        String videoId,
        String title,
        String thumbnailUrl,
        QueueItemStatus status,
        LocalDateTime requestedAt
) {

    public static QueueItemResponse from(QueueItem queueItem) {
        return new QueueItemResponse(
                queueItem.getId(),
                queueItem.getVideoId(),
                queueItem.getTitle(),
                queueItem.getThumbnailUrl(),
                queueItem.getStatus(),
                queueItem.getRequestedAt()
        );
    }
}
