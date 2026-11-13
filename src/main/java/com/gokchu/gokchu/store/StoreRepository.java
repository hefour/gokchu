package com.gokchu.gokchu.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {

    /**
     * 매장의 현재 폴백이 아직 {@code videoId}일 때만 비운다(compare-and-set). 같은 폴백 종료가 중복으로 오거나
     * 관리자 폴백 스킵과 겹쳐도 1행을 바꾼 트랜잭션 하나만 다음 곡으로 넘어간다.
     *
     * <p>clearAutomatically를 켜는 이유: 같은 트랜잭션에서 미리 읽어 둔 Store는 이 UPDATE 이전 상태라서,
     * 그대로 쓰면 이미 비운 폴백을 재생중으로 보거나 dirty checking이 옛 값을 다시 쓸 수 있다.
     * 호출한 쪽은 이 메서드 뒤에 Store를 다시 읽어서 써야 한다.
     *
     * @return 바뀐 행 수 (0 또는 1)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Store s
            set s.currentFallbackVideoId = null, s.currentFallbackTitle = null, s.currentFallbackThumbnailUrl = null
            where s.id = :storeId and s.currentFallbackVideoId = :videoId
            """)
    int clearFallbackIfCurrent(@Param("storeId") Long storeId, @Param("videoId") String videoId);
}
