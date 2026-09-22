package com.gokchu.gokchu.search;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchQueryCacheRepository extends JpaRepository<SearchQueryCache, String> {
}
