package com.gokchu.gokchu.queue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QueueItemRepository extends JpaRepository<QueueItem, Long> {

    List<QueueItem> findByStoreIdAndStatusInOrderByRequestedAtAsc(Long storeId, List<QueueItemStatus> statuses);
}
