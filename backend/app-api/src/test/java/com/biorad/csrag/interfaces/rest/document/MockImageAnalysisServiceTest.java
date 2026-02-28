package com.biorad.csrag.interfaces.rest.document;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MockImageAnalysisServiceTest {

    private final MockImageAnalysisService service = new MockImageAnalysisService();

    @Test
    void analyze_returnsFixedMockResult() {
        ImageAnalysisService.ImageAnalysisResult result = service.analyze(Path.of("test.png"));

        assertEquals("SCREENSHOT", result.imageType());
        assertTrue(result.extractedText().contains("OPENAI_ENABLED=true"));
        assertEquals(0.0, result.confidence());
    }

    @Test
    void analyze_worksWithAnyPath() {
        ImageAnalysisService.ImageAnalysisResult result1 = service.analyze(Path.of("photo.jpg"));
        ImageAnalysisService.ImageAnalysisResult result2 = service.analyze(Path.of("/some/deep/path/diagram.webp"));

        assertEquals(result1.imageType(), result2.imageType());
        assertEquals(result1.confidence(), result2.confidence());
    }
}
