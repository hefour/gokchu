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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchServiceTest {

    @Mock
    private TableTokenRepository tableTokenRepository;
    @Mock
    private SearchQueryCacheRepository searchQueryCacheRepository;
    @Mock
    private CachedVideoRepository cachedVideoRepository;
    @Mock
    private YoutubeApiClient youtubeApiClient;
    @Mock
    private SearchQuotaLimiter searchQuotaLimiter;

    @InjectMocks
    private SearchService searchService;

    private UUID tableToken;

    @BeforeEach
    void setUp() {
        Store store = Store.builder().name("데모 매장").regionCode("KR").build();
        ReflectionTestUtils.setField(store, "id", 1L);

        TableToken table = TableToken.builder().store(store).label("1번 테이블").build();
        ReflectionTestUtils.setField(table, "id", 10L);
        tableToken = table.getToken();

        when(tableTokenRepository.findByToken(tableToken)).thenReturn(Optional.of(table));
    }

    @Test
    void search_캐시_히트면_유튜브_검색_API를_호출하지_않는다() {
        SearchQueryCache cached = SearchQueryCache.builder()
                .normalizedQuery("rick astley")
                .videoIds(List.of("dQw4w9WgXcQ"))
                .build();
        when(searchQueryCacheRepository.findById("rick astley")).thenReturn(Optional.of(cached));
        when(cachedVideoRepository.findAllById(List.of("dQw4w9WgXcQ")))
                .thenReturn(List.of(playableVideo("dQw4w9WgXcQ", "Never Gonna Give You Up")));

        List<SearchResultResponse> results = searchService.search(tableToken, "  Rick Astley  ");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).videoId()).isEqualTo("dQw4w9WgXcQ");
        verify(youtubeApiClient, never()).search(anyString(), anyInt());
        verify(searchQuotaLimiter, never()).tryConsume();
    }

    @Test
    void search_캐시_미스면_검색_후_결과를_캐시에_저장한다() {
        when(searchQueryCacheRepository.findById("zoo")).thenReturn(Optional.empty());
        when(searchQuotaLimiter.tryConsume()).thenReturn(true);
        when(youtubeApiClient.search("zoo", 25)).thenReturn(
                new YoutubeApiClient.SearchListResponse(List.of(
                        new YoutubeApiClient.SearchResultItem(
                                new YoutubeApiClient.SearchResultId("jNQXAC9IVRw"),
                                new YoutubeApiClient.Snippet("Me at the zoo", "jawed", null))
                )));
        when(youtubeApiClient.fetchVideoDetails(List.of("jNQXAC9IVRw"))).thenReturn(
                new YoutubeApiClient.VideosListResponse(List.of(videoItem("jNQXAC9IVRw", "Me at the zoo"))));
        when(cachedVideoRepository.existsById("jNQXAC9IVRw")).thenReturn(false);
        when(cachedVideoRepository.findAllById(List.of("jNQXAC9IVRw")))
                .thenReturn(List.of(playableVideo("jNQXAC9IVRw", "Me at the zoo")));

        List<SearchResultResponse> results = searchService.search(tableToken, "zoo");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Me at the zoo");
        verify(searchQueryCacheRepository).save(any(SearchQueryCache.class));
        verify(cachedVideoRepository).saveAll(any());
    }

    @Test
    void search_쿼터를_다_썼으면_검색_API를_호출하지_않고_예외를_던진다() {
        when(searchQueryCacheRepository.findById("new song")).thenReturn(Optional.empty());
        when(searchQuotaLimiter.tryConsume()).thenReturn(false);

        assertThatThrownBy(() -> searchService.search(tableToken, "new song"))
                .isInstanceOf(GokchuException.class)
                .extracting(e -> ((GokchuException) e).getErrorCode())
                .isEqualTo(ErrorCode.SEARCH_QUOTA_EXCEEDED);

        verify(youtubeApiClient, never()).search(anyString(), anyInt());
    }

    @Test
    void search_존재하지_않는_테이블이면_예외를_던진다() {
        UUID unknownToken = UUID.randomUUID();
        when(tableTokenRepository.findByToken(unknownToken)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> searchService.search(unknownToken, "query"))
                .isInstanceOf(GokchuException.class)
                .extracting(e -> ((GokchuException) e).getErrorCode())
                .isEqualTo(ErrorCode.TABLE_TOKEN_NOT_FOUND);
    }

    @Test
    void search_검색어가_공백이면_빈_목록을_반환한다() {
        List<SearchResultResponse> results = searchService.search(tableToken, "   ");

        assertThat(results).isEmpty();
        verify(searchQueryCacheRepository, never()).findById(anyString());
    }

    private CachedVideo playableVideo(String videoId, String title) {
        return CachedVideo.builder()
                .videoId(videoId)
                .title(title)
                .thumbnailUrl("https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg")
                .channelTitle("channel")
                .embeddable(true)
                .allowedRegions(null)
                .blockedRegions(null)
                .build();
    }

    private YoutubeApiClient.VideoItem videoItem(String videoId, String title) {
        return new YoutubeApiClient.VideoItem(
                videoId,
                new YoutubeApiClient.Snippet(title, "channel", null),
                new YoutubeApiClient.Status(true),
                null);
    }
}
