package com.biorad.csrag.interfaces.rest.document;

import com.biorad.csrag.infrastructure.prompt.PromptRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiImageAnalysisServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MockImageAnalysisService fallback = new MockImageAnalysisService();

    @TempDir
    Path tempDir;

    private OpenAiImageAnalysisService buildService() {
        PromptRegistry promptRegistry = mock(PromptRegistry.class);
        when(promptRegistry.get(anyString())).thenReturn("Analyze this image.");
        return new OpenAiImageAnalysisService(
                "invalid-key",
                "https://api.openai.com/v1",
                "gpt-4o",
                objectMapper,
                fallback,
                promptRegistry
        );
    }

    @Test
    void analyze_fallsBackToMockOnInvalidApiKey() throws Exception {
        // Create a minimal test image file
        Path testImage = tempDir.resolve("test.png");
        Files.write(testImage, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}); // PNG magic bytes

        // Using an invalid API key should cause the service to fall back to mock
        ImageAnalysisService.ImageAnalysisResult result = buildService().analyze(testImage);

        // Should fall back to mock result
        assertEquals("SCREENSHOT", result.imageType());
        assertEquals(0.0, result.confidence());
    }

    @Test
    void analyze_fallsBackToMockOnNonExistentFile() {
        ImageAnalysisService.ImageAnalysisResult result =
                buildService().analyze(Path.of("/nonexistent/image.png"));

        // Should fall back to mock result
        assertEquals("SCREENSHOT", result.imageType());
        assertEquals(0.0, result.confidence());
    }
}
