package com.gokchu.gokchu.video;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class YoutubeApiClient {

    private final RestClient restClient;
    private final YoutubeProperties properties;

    public YoutubeApiClient(YoutubeProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3_000);
        requestFactory.setReadTimeout(5_000);
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public VideosListResponse fetchVideoDetails(String videoId) {
        return fetchVideoDetails(List.of(videoId));
    }

    /**
     * videos.list는 콤마로 구분한 videoId를 한 번에 최대 50개까지 받아도 여전히 1유닛만 소모한다 —
     * 검색 결과 전체를 한 번의 호출로 검증/캐싱하기 위해 배치로 조회.
     */
    public VideosListResponse fetchVideoDetails(List<String> videoIds) {
        try {
            VideosListResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/videos")
                            .queryParam("part", "snippet,status,contentDetails")
                            .queryParam("id", String.join(",", videoIds))
                            .queryParam("key", properties.apiKey())
                            .build())
                    .retrieve()
                    .body(VideosListResponse.class);

            if (response == null) {
                throw new GokchuException(ErrorCode.YOUTUBE_API_ERROR);
            }
            return response;
        } catch (RestClientException e) {
            throw new GokchuException(ErrorCode.YOUTUBE_API_ERROR);
        }
    }

    public SearchListResponse search(String query, int maxResults) {
        try {
            SearchListResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("part", "snippet")
                            .queryParam("type", "video")
                            .queryParam("q", query)
                            .queryParam("maxResults", maxResults)
                            .queryParam("key", properties.apiKey())
                            .build())
                    .retrieve()
                    .body(SearchListResponse.class);

            if (response == null) {
                throw new GokchuException(ErrorCode.YOUTUBE_API_ERROR);
            }
            return response;
        } catch (RestClientException e) {
            throw new GokchuException(ErrorCode.YOUTUBE_API_ERROR);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideosListResponse(List<VideoItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchListResponse(List<SearchResultItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchResultItem(SearchResultId id, Snippet snippet) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchResultId(String videoId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideoItem(String id, Snippet snippet, Status status, ContentDetails contentDetails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snippet(String title, String channelTitle, Thumbnails thumbnails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Thumbnails(
            @JsonProperty("default") Thumbnail defaultThumbnail,
            Thumbnail medium,
            Thumbnail high
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Thumbnail(String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(Boolean embeddable) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentDetails(RegionRestriction regionRestriction) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RegionRestriction(List<String> allowed, List<String> blocked) {
    }
}
