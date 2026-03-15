package com.biorad.csrag.interfaces.rest.image;

public record ImageUploadResponse(
        String imageId,
        String url,
        int width,
        int height,
        String format,
        long sizeBytes
) {}
