package com.biorad.csrag.interfaces.rest.document;

import java.nio.file.Path;

public interface ImageAnalysisService {

    ImageAnalysisResult analyze(Path imagePath);

    record ImageAnalysisResult(
            String imageType,           // SCREENSHOT, GRAPH, PHOTO, DIAGRAM
            String extractedText,       // OCR extracted text
            String visualDescription,   // Visual element description
            String technicalContext,    // Bio-Rad product/software context
            String suggestedQuery,      // Recommended search query
            double confidence           // Analysis confidence 0.0-1.0
    ) {}
}
