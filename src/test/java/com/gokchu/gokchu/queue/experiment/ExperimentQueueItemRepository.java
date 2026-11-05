package com.gokchu.gokchu.queue.experiment;

import com.gokchu.gokchu.queue.QueueItem;
import com.gokchu.gokchu.queue.QueueItemStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** 해결책 두 가지에 필요한 쿼리. 스키마 변경 없이 기존 컬럼(status)만 사용한다. */
interface ExperimentQueueItemRepository extends Repository<QueueItem, Long> {

    /**
     * 해결책 ①: UPDATE queue_items SET status=:to WHERE id=:id AND status=:from
     * "확인(WHERE)과 변경(SET)"이 한 문장이라 원자적이다. 반환값 = 바뀐 행 수(0 또는 1).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update QueueItem q set q.status = :to where q.id = :id and q.status = :from")
    int transition(@Param("id") Long id, @Param("from") QueueItemStatus from, @Param("to") QueueItemStatus to);

    /** 해결책 ②: SELECT ... FROM queue_items WHERE id=:id FOR UPDATE */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QueueItem q where q.id = :id")
    Optional<QueueItem> findByIdForUpdate(@Param("id") Long id);
}
