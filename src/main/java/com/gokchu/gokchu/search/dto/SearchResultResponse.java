package com.gokchu.gokchu.search.dto;

import com.gokchu.gokchu.video.CachedVideo;

public record SearchResultResponse(
        String videoId,
        String title,
        String thumbnailUrl,
        String channelTitle,
        boolean playable
) {
    public static SearchResultResponse of(CachedVideo video, String regionCode) {
        return new SearchResultResponse(
                video.getVideoId(),
                video.getTitle(),
                video.getThumbnailUrl(),
                video.getChannelTitle(),
                video.isPlayableIn(regionCode)
        );
    }
}
