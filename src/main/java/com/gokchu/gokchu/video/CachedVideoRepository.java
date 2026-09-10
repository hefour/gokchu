package com.gokchu.gokchu.video;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CachedVideoRepository extends JpaRepository<CachedVideo, String> {
}
