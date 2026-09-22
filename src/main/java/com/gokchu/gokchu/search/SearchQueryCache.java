package com.gokchu.gokchu.search;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 정규화된 검색어 → 검색 결과 videoId 목록(순위 순) 캐시. {@code search.list}는 1회 100유닛으로
 * 비싸서, 같은 검색어가 다시 들어오면 이 캐시로 응답하고 실제 API는 다시 호출하지 않는다.
 */
@Entity
@Table(name = "search_query_cache")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchQueryCache {

    @Id
    @Column(name = "normalized_query", length = 300)
    private String normalizedQuery;

    @ElementCollection
    @CollectionTable(name = "search_query_cache_video_ids", joinColumns = @JoinColumn(name = "normalized_query"))
    @OrderColumn(name = "video_ids_order")
    @Column(name = "video_id", length = 20)
    private List<String> videoIds;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public SearchQueryCache(String normalizedQuery, List<String> videoIds) {
        this.normalizedQuery = normalizedQuery;
        this.videoIds = videoIds;
    }
}
