CREATE TABLE images (
    id          UUID PRIMARY KEY,
    inquiry_id  UUID REFERENCES inquiries(id),
    file_name   VARCHAR(255) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    size_bytes  BIGINT NOT NULL,
    width       INT,
    height      INT,
    storage_path VARCHAR(500) NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_images_inquiry ON images(inquiry_id);
