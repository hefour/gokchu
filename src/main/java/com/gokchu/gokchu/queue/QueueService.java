package com.gokchu.gokchu.queue;

import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import com.gokchu.gokchu.queue.dto.AddQueueItemRequest;
import com.gokchu.gokchu.queue.dto.QueueItemResponse;
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
public class QueueService {

    private final TableTokenRepository tableTokenRepository;
    private final QueueItemRepository queueItemRepository;
    private final VideoValidationService videoValidationService;

    @Transactional
    public QueueItemResponse addToQueue(UUID tableToken, AddQueueItemRequest request) {
        TableToken table = findTable(tableToken);

        String videoId = YoutubeLinkParser.extractVideoId(request.link());
        CachedVideo video = videoValidationService.validateForPlayback(videoId, table.getStore().getRegionCode());

        QueueItem queueItem = QueueItem.builder()
                .store(table.getStore())
                .tableToken(table)
                .videoId(video.getVideoId())
                .title(video.getTitle())
                .thumbnailUrl(video.getThumbnailUrl())
                .build();

        return QueueItemResponse.from(queueItemRepository.save(queueItem));
    }

    @Transactional(readOnly = true)
    public List<QueueItemResponse> getQueue(UUID tableToken) {
        TableToken table = findTable(tableToken);

        return queueItemRepository
                .findByStoreIdAndStatusInOrderByRequestedAtAsc(
                        table.getStore().getId(),
                        List.of(QueueItemStatus.WAITING, QueueItemStatus.PLAYING)
                )
                .stream()
                .map(QueueItemResponse::from)
                .toList();
    }

    private TableToken findTable(UUID tableToken) {
        return tableTokenRepository.findByToken(tableToken)
                .orElseThrow(() -> new GokchuException(ErrorCode.TABLE_TOKEN_NOT_FOUND));
    }
}
