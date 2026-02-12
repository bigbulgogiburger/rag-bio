CREATE TABLE IF NOT EXISTS inquiries (
    id UUID PRIMARY KEY,
    question VARCHAR(4000) NOT NULL,
    customer_channel VARCHAR(100) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY,
    inquiry_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_documents_inquiry FOREIGN KEY (inquiry_id) REFERENCES inquiries(id)
);

CREATE INDEX IF NOT EXISTS idx_documents_inquiry_id ON documents(inquiry_id);
CREATE INDEX IF NOT EXISTS idx_inquiries_created_at ON inquiries(created_at);
