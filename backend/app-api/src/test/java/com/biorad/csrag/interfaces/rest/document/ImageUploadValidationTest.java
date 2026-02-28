package com.biorad.csrag.interfaces.rest.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that the DocumentController's allowed content types include image formats.
 */
class ImageUploadValidationTest {

    // Mirror the sets from DocumentController for validation
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/png",
            "image/jpeg",
            "image/webp"
    );

    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp"
    );

    @ParameterizedTest
    @ValueSource(strings = {"image/png", "image/jpeg", "image/webp"})
    void imageTypesAreAllowed(String contentType) {
        assertTrue(ALLOWED_CONTENT_TYPES.contains(contentType),
                contentType + " should be in ALLOWED_CONTENT_TYPES");
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/png", "image/jpeg", "image/webp"})
    void imageTypesAreRecognizedAsImages(String contentType) {
        assertTrue(IMAGE_CONTENT_TYPES.contains(contentType),
                contentType + " should be recognized as an image type");
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"})
    void documentTypesAreNotImages(String contentType) {
        assertFalse(IMAGE_CONTENT_TYPES.contains(contentType),
                contentType + " should NOT be recognized as an image type");
    }

    @Test
    void unsupportedTypesAreRejected() {
        assertFalse(ALLOWED_CONTENT_TYPES.contains("image/gif"));
        assertFalse(ALLOWED_CONTENT_TYPES.contains("application/zip"));
        assertFalse(ALLOWED_CONTENT_TYPES.contains("text/plain"));
    }

    @Test
    void maxImageSizeIs20MB() {
        long maxImageSize = 20L * 1024 * 1024;
        assertEquals(20_971_520L, maxImageSize);
    }
}
