package com.gokchu.gokchu.store;

import com.gokchu.gokchu.fallback.FallbackTrack;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "region_code", nullable = false, length = 2)
    private String regionCode;

    @Column(name = "current_fallback_video_id", length = 20)
    private String currentFallbackVideoId;

    @Column(name = "current_fallback_title", length = 500)
    private String currentFallbackTitle;

    @Column(name = "current_fallback_thumbnail_url", length = 500)
    private String currentFallbackThumbnailUrl;

    @Builder
    public Store(String name, String regionCode) {
        this.name = name;
        this.regionCode = regionCode == null ? "KR" : regionCode;
    }

    public boolean hasFallbackPlaying() {
        return currentFallbackVideoId != null;
    }

    public void startFallback(FallbackTrack track) {
        this.currentFallbackVideoId = track.getVideoId();
        this.currentFallbackTitle = track.getTitle();
        this.currentFallbackThumbnailUrl = track.getThumbnailUrl();
    }

    public void clearFallback() {
        this.currentFallbackVideoId = null;
        this.currentFallbackTitle = null;
        this.currentFallbackThumbnailUrl = null;
    }
}
