package com.gokchu.gokchu.video;

import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

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

        YoutubeApiClient.VideoItem item = response.items().get(0);
        boolean embeddable = item.status() != null && Boolean.TRUE.equals(item.status().embeddable());

        CachedVideo cachedVideo = CachedVideo.builder()
                .videoId(videoId)
                .title(item.snippet().title())
                .thumbnailUrl(resolveThumbnail(item.snippet().thumbnails()))
                .channelTitle(item.snippet().channelTitle())
                .embeddable(embeddable)
                .allowedRegions(extractRegions(item, YoutubeApiClient.RegionRestriction::allowed))
                .blockedRegions(extractRegions(item, YoutubeApiClient.RegionRestriction::blocked))
                .build();

        return cachedVideoRepository.save(cachedVideo);
    }

    private Set<String> extractRegions(
            YoutubeApiClient.VideoItem item,
            java.util.function.Function<YoutubeApiClient.RegionRestriction, List<String>> extractor
    ) {
        if (item.contentDetails() == null || item.contentDetails().regionRestriction() == null) {
            return Set.of();
        }
        List<String> regions = extractor.apply(item.contentDetails().regionRestriction());
        return regions == null ? Set.of() : Set.copyOf(regions);
    }

    private String resolveThumbnail(YoutubeApiClient.Thumbnails thumbnails) {
        if (thumbnails == null) {
            return "";
        }
        if (thumbnails.high() != null) {
            return thumbnails.high().url();
        }
        if (thumbnails.medium() != null) {
            return thumbnails.medium().url();
        }
        if (thumbnails.defaultThumbnail() != null) {
            return thumbnails.defaultThumbnail().url();
        }
        return "";
    }
}
