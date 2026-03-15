package com.biorad.csrag.interfaces.rest.image;

import com.biorad.csrag.common.exception.NotFoundException;
import com.biorad.csrag.common.exception.ValidationException;
import com.biorad.csrag.infrastructure.persistence.image.ImageJpaEntity;
import com.biorad.csrag.infrastructure.persistence.image.ImageJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class ImageStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private final String storagePath;
    private final String baseUrl;
    private final ImageJpaRepository imageRepository;

    public ImageStorageService(
            @Value("${image.storage.path:./uploads/images}") String storagePath,
            @Value("${image.storage.base-url:http://localhost:8081}") String baseUrl,
            ImageJpaRepository imageRepository
    ) {
        this.storagePath = storagePath;
        this.baseUrl = baseUrl;
        this.imageRepository = imageRepository;
    }

    public ImageUploadResponse upload(MultipartFile file, UUID inquiryId) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("FILE_REQUIRED", "File is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException("FILE_TOO_LARGE", "File size exceeds 5MB limit");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("FILE_READ_ERROR", "Failed to read uploaded file");
        }

        String contentType = validateContentType(bytes);
        String extension = "image/png".equals(contentType) ? "png" : "jpg";

        UUID imageId = UUID.randomUUID();
        String fileName = imageId + "." + extension;

        Path dirPath = Paths.get(storagePath);
        Path filePath = dirPath.resolve(fileName);

        try {
            Files.createDirectories(dirPath);
            Files.write(filePath, bytes);
        } catch (IOException e) {
            throw new ValidationException("FILE_WRITE_ERROR", "Failed to save uploaded file");
        }

        int width = 0;
        int height = 0;
        try {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(bytes));
            if (bufferedImage != null) {
                width = bufferedImage.getWidth();
                height = bufferedImage.getHeight();
            }
        } catch (IOException e) {
            // Non-critical: dimensions will be 0
        }

        ImageJpaEntity entity = new ImageJpaEntity(
                imageId, inquiryId, fileName, contentType,
                bytes.length, width, height, filePath.toString()
        );
        imageRepository.save(entity);

        String url = baseUrl + "/api/v1/images/" + imageId + "." + extension;

        return new ImageUploadResponse(
                imageId.toString(), url, width, height, extension, bytes.length
        );
    }

    public Resource getImageResource(UUID imageId) {
        ImageJpaEntity entity = imageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("IMAGE_NOT_FOUND", "Image not found"));

        Path filePath = Paths.get(entity.getStoragePath());
        if (!Files.exists(filePath)) {
            throw new NotFoundException("IMAGE_FILE_NOT_FOUND", "Image file not found on disk");
        }

        return new FileSystemResource(filePath);
    }

    public ImageJpaEntity getImageEntity(UUID imageId) {
        return imageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("IMAGE_NOT_FOUND", "Image not found"));
    }

    /**
     * Validates the actual file content by checking magic bytes.
     * Returns the detected content type.
     */
    private String validateContentType(byte[] bytes) {
        if (bytes.length < 4) {
            throw new ValidationException("INVALID_FILE_TYPE", "File is too small to be a valid image");
        }

        // PNG: 89 50 4E 47
        if (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50
                && bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47) {
            return "image/png";
        }

        // JPEG: FF D8 FF
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return "image/jpeg";
        }

        throw new ValidationException("INVALID_FILE_TYPE", "Only PNG and JPEG images are supported");
    }
}
