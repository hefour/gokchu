package com.gokchu.gokchu.fallback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FallbackTrackRepository extends JpaRepository<FallbackTrack, Long> {

    List<FallbackTrack> findByStoreId(Long storeId);
}
