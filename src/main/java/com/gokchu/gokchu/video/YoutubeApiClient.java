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
        try {
            VideosListResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/videos")
                            .queryParam("part", "snippet,status,contentDetails")
                            .queryParam("id", videoId)
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideosListResponse(List<VideoItem> items) {
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
