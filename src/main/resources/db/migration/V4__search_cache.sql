CREATE TABLE search_query_cache (
    normalized_query VARCHAR(300) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE search_query_cache_video_ids (
    normalized_query VARCHAR(300) NOT NULL REFERENCES search_query_cache (normalized_query) ON DELETE CASCADE,
    video_ids_order INTEGER NOT NULL,
    video_id VARCHAR(20) NOT NULL,
    PRIMARY KEY (normalized_query, video_ids_order)
);
