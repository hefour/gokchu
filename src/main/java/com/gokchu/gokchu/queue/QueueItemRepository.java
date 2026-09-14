package com.gokchu.gokchu.queue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QueueItemRepository extends JpaRepository<QueueItem, Long> {

    List<QueueItem> findByStoreIdAndStatusInOrderByRequestedAtAsc(Long storeId, List<QueueItemStatus> statuses);

    Optional<QueueItem> findFirstByStoreIdAndStatusOrderByRequestedAtAsc(Long storeId, QueueItemStatus status);
}
