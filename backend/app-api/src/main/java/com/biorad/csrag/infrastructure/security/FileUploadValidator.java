package com.biorad.csrag.infrastructure.security;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Component
public class FileUploadValidator {

    private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
            "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46},  // %PDF
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            new byte[]{0x50, 0x4B, 0x03, 0x04},  // PK (ZIP-based DOCX)
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[]{0x50, 0x4B, 0x03, 0x04},  // PK (ZIP-based XLSX)
            "application/zip", new byte[]{0x50, 0x4B, 0x03, 0x04}
    );

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB

    public ValidationResult validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ValidationResult.fail("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ValidationResult.fail("File exceeds maximum size of 20MB");
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            return ValidationResult.fail("Content type is missing");
        }

        byte[] expectedMagic = MAGIC_BYTES.get(contentType);
        if (expectedMagic != null) {
            try (InputStream is = file.getInputStream()) {
                byte[] header = new byte[expectedMagic.length];
                int read = is.read(header);
                if (read < expectedMagic.length) {
                    return ValidationResult.fail("File is too small to verify content type");
                }
                for (int i = 0; i < expectedMagic.length; i++) {
                    if (header[i] != expectedMagic[i]) {
                        return ValidationResult.fail(
                                "File content does not match declared content type: " + contentType);
                    }
                }
            } catch (IOException e) {
                return ValidationResult.fail("Failed to read file for validation");
            }
        }

        return ValidationResult.ok();
    }

    public record ValidationResult(boolean valid, String message) {
        static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        static ValidationResult fail(String message) {
            return new ValidationResult(false, message);
        }
    }
}
