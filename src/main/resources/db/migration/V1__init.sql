CREATE TABLE stores (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    region_code VARCHAR(2) NOT NULL
);

CREATE TABLE table_tokens (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL REFERENCES stores (id),
    token UUID NOT NULL,
    label VARCHAR(255) NOT NULL,
    CONSTRAINT uk_table_tokens_token UNIQUE (token)
);

CREATE TABLE cached_videos (
    video_id VARCHAR(20) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500) NOT NULL,
    channel_title VARCHAR(255),
    embeddable BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE cached_video_allowed_regions (
    video_id VARCHAR(20) NOT NULL REFERENCES cached_videos (video_id),
    region_code VARCHAR(2) NOT NULL
);

CREATE TABLE cached_video_blocked_regions (
    video_id VARCHAR(20) NOT NULL REFERENCES cached_videos (video_id),
    region_code VARCHAR(2) NOT NULL
);

CREATE TABLE queue_items (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL REFERENCES stores (id),
    table_token_id BIGINT NOT NULL REFERENCES table_tokens (id),
    video_id VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_queue_items_store_status ON queue_items (store_id, status, requested_at);
CREATE INDEX idx_table_tokens_store ON table_tokens (store_id);
