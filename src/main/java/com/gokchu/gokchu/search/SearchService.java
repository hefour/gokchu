package com.gokchu.gokchu.search;

import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import com.gokchu.gokchu.search.dto.SearchResultResponse;
import com.gokchu.gokchu.store.Store;
import com.gokchu.gokchu.tabletoken.TableToken;
import com.gokchu.gokchu.tabletoken.TableTokenRepository;
import com.gokchu.gokchu.video.CachedVideo;
import com.gokchu.gokchu.video.CachedVideoRepository;
import com.gokchu.gokchu.video.YoutubeApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int MAX_RESULTS = 25;

    private final TableTokenRepository tableTokenRepository;
    private final SearchQueryCacheRepository searchQueryCacheRepository;
    private final CachedVideoRepository cachedVideoRepository;
    private final YoutubeApiClient youtubeApiClient;
    private final SearchQuotaLimiter searchQuotaLimiter;

    @Transactional
    public List<SearchResultResponse> search(UUID tableToken, String query) {
        Store store = findTable(tableToken).getStore();
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        List<String> videoIds = searchQueryCacheRepository.findById(normalizedQuery)
                .map(SearchQueryCache::getVideoIds)
                .orElseGet(() -> fetchAndCache(normalizedQuery));

        Map<String, CachedVideo> videosById = cachedVideoRepository.findAllById(videoIds).stream()
                .collect(Collectors.toMap(CachedVideo::getVideoId, Function.identity()));

        return videoIds.stream()
                .map(videosById::get)
                .filter(Objects::nonNull)
                .map(video -> SearchResultResponse.of(video, store.getRegionCode()))
                .toList();
    }

    private List<String> fetchAndCache(String normalizedQuery) {
        if (!searchQuotaLimiter.tryConsume()) {
            throw new GokchuException(ErrorCode.SEARCH_QUOTA_EXCEEDED);
        }

        YoutubeApiClient.SearchListResponse searchResponse = youtubeApiClient.search(normalizedQuery, MAX_RESULTS);
        List<String> videoIds = searchResponse.items() == null
                ? List.of()
                : searchResponse.items().stream()
                        .map(item -> item.id() == null ? null : item.id().videoId())
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        if (!videoIds.isEmpty()) {
            cacheVideoDetails(videoIds);
        }

        searchQueryCacheRepository.save(
                SearchQueryCache.builder().normalizedQuery(normalizedQuery).videoIds(videoIds).build());

        return videoIds;
    }

    /** search.list 결과의 embeddable/지역제한 정보를 videos.list 배치 호출(1유닛)로 채워 캐시에 저장한다. */
    private void cacheVideoDetails(List<String> videoIds) {
        YoutubeApiClient.VideosListResponse detailsResponse = youtubeApiClient.fetchVideoDetails(videoIds);
        if (detailsResponse.items() == null) {
            return;
        }

        List<CachedVideo> toSave = detailsResponse.items().stream()
                .filter(item -> !cachedVideoRepository.existsById(item.id()))
                .map(CachedVideo::fromApiItem)
                .toList();
        cachedVideoRepository.saveAll(toSave);
    }

    private String normalize(String query) {
        if (query == null) {
            return "";
        }
        return query.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private TableToken findTable(UUID tableToken) {
        return tableTokenRepository.findByToken(tableToken)
                .orElseThrow(() -> new GokchuException(ErrorCode.TABLE_TOKEN_NOT_FOUND));
    }
}
