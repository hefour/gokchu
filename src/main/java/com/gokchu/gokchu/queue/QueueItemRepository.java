package com.gokchu.gokchu.queue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QueueItemRepository extends JpaRepository<QueueItem, Long> {

    List<QueueItem> findByStoreIdAndStatusInOrderByRequestedAtAsc(Long storeId, List<QueueItemStatus> statuses);

    Optional<QueueItem> findFirstByStoreIdAndStatusOrderByRequestedAtAsc(Long storeId, QueueItemStatus status);

    /**
     * 큐 항목이 아직 {@code from} 상태일 때만 {@code to}로 바꾼다(compare-and-set).
     * 확인(WHERE)과 변경(SET)이 한 문장이라, 같은 곡을 두고 complete와 skip이 동시에 와도
     * 1행을 바꾼 트랜잭션 하나만 다음 곡을 올린다. 근거는 docs/concurrency-experiment.md.
     *
     * <p>clearAutomatically를 켜지 않는 이유: 같은 트랜잭션에서 읽어 둔 Store가 detach되면
     * 이후 폴백 전환(store.startFallback)이 dirty checking으로 저장되지 않는다.
     * 대신 이 메서드로 바꾼 QueueItem은 영속성 컨텍스트에서 status가 낡을 수 있으므로,
     * 호출한 쪽은 그 엔티티의 status로 판단하지 말고 반환값(바뀐 행 수)으로 분기해야 한다.
     *
     * @return 바뀐 행 수 (0 또는 1)
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update QueueItem q set q.status = :to
            where q.id = :id and q.store.id = :storeId and q.status = :from
            """)
    int transitionStatus(@Param("id") Long id,
                         @Param("storeId") Long storeId,
                         @Param("from") QueueItemStatus from,
                         @Param("to") QueueItemStatus to);
}
