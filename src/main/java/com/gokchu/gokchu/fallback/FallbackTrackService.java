package com.gokchu.gokchu.fallback;

import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import com.gokchu.gokchu.fallback.dto.AddFallbackTrackRequest;
import com.gokchu.gokchu.fallback.dto.FallbackTrackResponse;
import com.gokchu.gokchu.tabletoken.TableToken;
import com.gokchu.gokchu.tabletoken.TableTokenRepository;
import com.gokchu.gokchu.video.CachedVideo;
import com.gokchu.gokchu.video.VideoValidationService;
import com.gokchu.gokchu.video.YoutubeLinkParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FallbackTrackService {

    private final TableTokenRepository tableTokenRepository;
    private final FallbackTrackRepository fallbackTrackRepository;
    private final VideoValidationService videoValidationService;

    @Transactional
    public FallbackTrackResponse addTrack(UUID tableToken, AddFallbackTrackRequest request) {
        TableToken table = findTable(tableToken);

        String videoId = YoutubeLinkParser.extractVideoId(request.link());
        CachedVideo video = videoValidationService.validateForPlayback(videoId, table.getStore().getRegionCode());

        FallbackTrack track = FallbackTrack.builder()
                .store(table.getStore())
                .videoId(video.getVideoId())
                .title(video.getTitle())
                .thumbnailUrl(video.getThumbnailUrl())
                .build();

        return FallbackTrackResponse.from(fallbackTrackRepository.save(track));
    }

    @Transactional(readOnly = true)
    public List<FallbackTrackResponse> getTracks(UUID tableToken) {
        TableToken table = findTable(tableToken);

        return fallbackTrackRepository.findByStoreId(table.getStore().getId())
                .stream()
                .map(FallbackTrackResponse::from)
                .toList();
    }

    private TableToken findTable(UUID tableToken) {
        return tableTokenRepository.findByToken(tableToken)
                .orElseThrow(() -> new GokchuException(ErrorCode.TABLE_TOKEN_NOT_FOUND));
    }
}
