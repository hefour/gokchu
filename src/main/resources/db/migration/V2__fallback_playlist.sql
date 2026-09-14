CREATE TABLE fallback_tracks (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL REFERENCES stores (id),
    video_id VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500) NOT NULL
);

CREATE INDEX idx_fallback_tracks_store ON fallback_tracks (store_id);

ALTER TABLE stores
    ADD COLUMN current_fallback_video_id VARCHAR(20),
    ADD COLUMN current_fallback_title VARCHAR(500),
    ADD COLUMN current_fallback_thumbnail_url VARCHAR(500);
