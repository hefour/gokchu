package com.gokchu.gokchu.video;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Entity
@Table(name = "cached_videos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CachedVideo {

    @Id
    @Column(name = "video_id", length = 20)
    private String videoId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "thumbnail_url", nullable = false, length = 500)
    private String thumbnailUrl;

    @Column(name = "channel_title")
    private String channelTitle;

    @Column(nullable = false)
    private boolean embeddable;

    @ElementCollection
    @CollectionTable(name = "cached_video_allowed_regions", joinColumns = @JoinColumn(name = "video_id"))
    @Column(name = "region_code", length = 2)
    private Set<String> allowedRegions;

    @ElementCollection
    @CollectionTable(name = "cached_video_blocked_regions", joinColumns = @JoinColumn(name = "video_id"))
    @Column(name = "region_code", length = 2)
    private Set<String> blockedRegions;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public CachedVideo(String videoId, String title, String thumbnailUrl, String channelTitle,
                        boolean embeddable, Set<String> allowedRegions, Set<String> blockedRegions) {
        this.videoId = videoId;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
        this.channelTitle = channelTitle;
        this.embeddable = embeddable;
        this.allowedRegions = allowedRegions;
        this.blockedRegions = blockedRegions;
    }

    public boolean isPlayableIn(String regionCode) {
        if (!embeddable) {
            return false;
        }
        if (allowedRegions != null && !allowedRegions.isEmpty()) {
            return allowedRegions.contains(regionCode);
        }
        if (blockedRegions != null && !blockedRegions.isEmpty()) {
            return !blockedRegions.contains(regionCode);
        }
        return true;
    }

    /** YouTube {@code videos.list} 응답 한 건을 캐시 엔티티로 매핑한다 — 링크 등록/검색 양쪽에서 공유. */
    public static CachedVideo fromApiItem(YoutubeApiClient.VideoItem item) {
        boolean embeddable = item.status() != null && Boolean.TRUE.equals(item.status().embeddable());

        return CachedVideo.builder()
                .videoId(item.id())
                .title(item.snippet().title())
                .thumbnailUrl(resolveThumbnail(item.snippet().thumbnails()))
                .channelTitle(item.snippet().channelTitle())
                .embeddable(embeddable)
                .allowedRegions(extractRegions(item, YoutubeApiClient.RegionRestriction::allowed))
                .blockedRegions(extractRegions(item, YoutubeApiClient.RegionRestriction::blocked))
                .build();
    }

    private static Set<String> extractRegions(
            YoutubeApiClient.VideoItem item,
            Function<YoutubeApiClient.RegionRestriction, List<String>> extractor
    ) {
        if (item.contentDetails() == null || item.contentDetails().regionRestriction() == null) {
            return Set.of();
        }
        List<String> regions = extractor.apply(item.contentDetails().regionRestriction());
        return regions == null ? Set.of() : Set.copyOf(regions);
    }

    private static String resolveThumbnail(YoutubeApiClient.Thumbnails thumbnails) {
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
