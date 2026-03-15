package com.biorad.csrag.infrastructure.persistence.image;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "images")
public class ImageJpaEntity {

    @Id
    private UUID id;

    @Column(name = "inquiry_id")
    private UUID inquiryId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "created_at")
    private Instant createdAt;

    protected ImageJpaEntity() {}

    public ImageJpaEntity(UUID id, UUID inquiryId, String fileName, String contentType,
                          long sizeBytes, Integer width, Integer height, String storagePath) {
        this.id = id;
        this.inquiryId = inquiryId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
        this.storagePath = storagePath;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getInquiryId() { return inquiryId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public String getStoragePath() { return storagePath; }
    public Instant getCreatedAt() { return createdAt; }
}
