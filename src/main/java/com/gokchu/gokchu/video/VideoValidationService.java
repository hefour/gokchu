package com.gokchu.gokchu.video;

import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VideoValidationService {

    private final CachedVideoRepository cachedVideoRepository;
    private final YoutubeApiClient youtubeApiClient;

    @Transactional
    public CachedVideo validateForPlayback(String videoId, String regionCode) {
        CachedVideo cachedVideo = cachedVideoRepository.findById(videoId)
                .orElseGet(() -> fetchAndCache(videoId));

        if (!cachedVideo.isPlayableIn(regionCode)) {
            throw new GokchuException(ErrorCode.VIDEO_NOT_PLAYABLE);
        }
        return cachedVideo;
    }

    private CachedVideo fetchAndCache(String videoId) {
        YoutubeApiClient.VideosListResponse response = youtubeApiClient.fetchVideoDetails(videoId);

        if (response.items() == null || response.items().isEmpty()) {
            throw new GokchuException(ErrorCode.VIDEO_NOT_FOUND);
        }

        return cachedVideoRepository.save(CachedVideo.fromApiItem(response.items().get(0)));
    }
}
