package com.gokchu.gokchu.queue.dto;

import com.gokchu.gokchu.queue.QueueItem;
import com.gokchu.gokchu.store.Store;

public record NowPlayingResponse(
        NowPlayingSource source,
        Long queueItemId,
        String videoId,
        String title,
        String thumbnailUrl
) {

    public static NowPlayingResponse ofQueueItem(QueueItem item) {
        return new NowPlayingResponse(
                NowPlayingSource.QUEUE, item.getId(), item.getVideoId(), item.getTitle(), item.getThumbnailUrl());
    }

    public static NowPlayingResponse ofFallback(Store store) {
        return new NowPlayingResponse(
                NowPlayingSource.FALLBACK,
                null,
                store.getCurrentFallbackVideoId(),
                store.getCurrentFallbackTitle(),
                store.getCurrentFallbackThumbnailUrl()
        );
    }
}
