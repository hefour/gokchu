package com.gokchu.gokchu.queue;

import com.gokchu.gokchu.store.Store;
import com.gokchu.gokchu.tabletoken.TableToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "queue_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QueueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_token_id", nullable = false)
    private TableToken tableToken;

    @Column(name = "video_id", nullable = false, length = 20)
    private String videoId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "thumbnail_url", nullable = false, length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QueueItemStatus status;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Builder
    public QueueItem(Store store, TableToken tableToken, String videoId, String title, String thumbnailUrl) {
        this.store = store;
        this.tableToken = tableToken;
        this.videoId = videoId;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
        this.status = QueueItemStatus.WAITING;
    }

    public void play() {
        this.status = QueueItemStatus.PLAYING;
    }

    public void finish() {
        this.status = QueueItemStatus.DONE;
    }

    public void skip() {
        this.status = QueueItemStatus.SKIPPED;
    }
}
