package com.biorad.csrag.interfaces.rest.document.ocr;

import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class MockOcrService implements OcrService {

    @Override
    public OcrResult extract(Path filePath) {
        String simulated = "[MOCK_OCR] extracted text from image-based document: " + filePath.getFileName();
        return new OcrResult(simulated, 0.65d);
    }
}
