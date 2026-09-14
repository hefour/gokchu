package com.gokchu.gokchu.fallback;

import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import com.gokchu.gokchu.fallback.dto.AddFallbackTrackRequest;
import com.gokchu.gokchu.fallback.dto.FallbackTrackResponse;
import com.gokchu.gokchu.store.Store;
import com.gokchu.gokchu.tabletoken.TableToken;
import com.gokchu.gokchu.tabletoken.TableTokenRepository;
import com.gokchu.gokchu.video.CachedVideo;
import com.gokchu.gokchu.video.VideoValidationService;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FallbackTrackServiceTest {

    @Mock
    private TableTokenRepository tableTokenRepository;

    @Mock
    private FallbackTrackRepository fallbackTrackRepository;

    @Mock
    private VideoValidationService videoValidationService;

    @InjectMocks
    private FallbackTrackService fallbackTrackService;

    private Store store;
    private TableToken table;
    private UUID tableToken;

    @BeforeEach
    void setUp() {
        store = Store.builder().name("데모 매장").regionCode("KR").build();
        ReflectionTestUtils.setField(store, "id", 1L);

        table = TableToken.builder().store(store).label("1번 테이블").build();
        ReflectionTestUtils.setField(table, "id", 10L);
        tableToken = table.getToken();

        when(tableTokenRepository.findByToken(tableToken)).thenReturn(Optional.of(table));
    }

    @Test
    void addTrack_유효한_링크면_폴백트랙으로_등록된다() {
        CachedVideo video = CachedVideo.builder()
                .videoId("dQw4w9WgXcQ").title("t").thumbnailUrl("u")
                .embeddable(true).allowedRegions(Set.of()).blockedRegions(Set.of())
                .build();
        when(videoValidationService.validateForPlayback(anyString(), eq("KR"))).thenReturn(video);
        when(fallbackTrackRepository.save(any(FallbackTrack.class))).thenAnswer(invocation -> {
            FallbackTrack track = invocation.getArgument(0);
            ReflectionTestUtils.setField(track, "id", 100L);
            return track;
        });

        FallbackTrackResponse response = fallbackTrackService.addTrack(tableToken, new AddFallbackTrackRequest("dQw4w9WgXcQ"));

        assertThat(response.videoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(response.id()).isEqualTo(100L);
    }

    @Test
    void addTrack_존재하지_않는_테이블이면_예외가_발생한다() {
        UUID unknownToken = UUID.randomUUID();
        when(tableTokenRepository.findByToken(unknownToken)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fallbackTrackService.addTrack(unknownToken, new AddFallbackTrackRequest("dQw4w9WgXcQ")))
                .isInstanceOf(GokchuException.class)
                .extracting(e -> ((GokchuException) e).getErrorCode())
                .isEqualTo(ErrorCode.TABLE_TOKEN_NOT_FOUND);
    }

    @Test
    void getTracks_등록된_트랙_목록을_반환한다() {
        FallbackTrack track = FallbackTrack.builder()
                .store(store).videoId("v1").title("t1").thumbnailUrl("u1").build();
        ReflectionTestUtils.setField(track, "id", 1L);
        when(fallbackTrackRepository.findByStoreId(1L)).thenReturn(List.of(track));

        List<FallbackTrackResponse> responses = fallbackTrackService.getTracks(tableToken);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).videoId()).isEqualTo("v1");
    }
}
