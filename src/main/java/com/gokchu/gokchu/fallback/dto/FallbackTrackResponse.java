package com.gokchu.gokchu.fallback.dto;

import com.gokchu.gokchu.fallback.FallbackTrack;

public record FallbackTrackResponse(Long id, String videoId, String title, String thumbnailUrl) {

    public static FallbackTrackResponse from(FallbackTrack track) {
        return new FallbackTrackResponse(track.getId(), track.getVideoId(), track.getTitle(), track.getThumbnailUrl());
    }
}
