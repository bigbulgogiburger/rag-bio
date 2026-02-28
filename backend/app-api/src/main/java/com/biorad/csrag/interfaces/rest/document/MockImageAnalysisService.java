package com.biorad.csrag.interfaces.rest.document;

import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class MockImageAnalysisService implements ImageAnalysisService {

    @Override
    public ImageAnalysisResult analyze(Path imagePath) {
        return new ImageAnalysisResult(
                "SCREENSHOT",
                "Mock: 이미지 분석을 사용하려면 OPENAI_ENABLED=true로 설정하세요",
                "Mock 이미지 분석 결과",
                "",
                "",
                0.0
        );
    }
}
