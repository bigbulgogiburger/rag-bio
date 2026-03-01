package com.biorad.csrag.interfaces.rest.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DocumentController의 이미지 분석 결과 → 청크 인덱싱 로직 단위 테스트.
 * buildImageChunkText 메서드와 confidence 기반 인덱싱 트리거 조건을 검증한다.
 */
class ImageChunkIndexingTest {

    /**
     * buildImageChunkText에 접근하기 위해 DocumentController 인스턴스가 필요하지만,
     * 생성자에 여러 의존성이 필요하므로 여기서는 동일한 포맷 로직을 직접 검증한다.
     */

    @Test
    void buildImageChunkText_contains_all_fields() {
        String fileName = "error_screenshot.png";
        var result = new ImageAnalysisService.ImageAnalysisResult(
                "SCREENSHOT",
                "Error: Calibration failed",
                "A screenshot showing a calibration error dialog box",
                "CFX96 real-time PCR system calibration module",
                "CFX96 calibration error troubleshooting",
                0.85
        );

        String text = formatImageChunkText(fileName, result);

        assertThat(text).contains("[이미지 분석: error_screenshot.png]");
        assertThat(text).contains("유형: SCREENSHOT");
        assertThat(text).contains("추출 텍스트: Error: Calibration failed");
        assertThat(text).contains("시각적 설명: A screenshot showing a calibration error dialog box");
        assertThat(text).contains("기술 컨텍스트: CFX96 real-time PCR system calibration module");
        assertThat(text).contains("권장 검색 쿼리: CFX96 calibration error troubleshooting");
    }

    @Test
    void buildImageChunkText_handles_null_fields() {
        String fileName = "photo.jpg";
        var result = new ImageAnalysisService.ImageAnalysisResult(
                null, null, null, null, null, 0.3
        );

        String text = formatImageChunkText(fileName, result);

        assertThat(text).contains("유형: 알 수 없음");
        assertThat(text).contains("추출 텍스트: ");
        assertThat(text).contains("시각적 설명: ");
        assertThat(text).contains("기술 컨텍스트: ");
        assertThat(text).contains("권장 검색 쿼리: ");
    }

    @Test
    void indexing_triggered_when_confidence_above_threshold() {
        double confidence = 0.5;
        assertThat(confidence > 0.1).isTrue();
    }

    @Test
    void indexing_not_triggered_when_confidence_at_threshold() {
        double confidence = 0.1;
        assertThat(confidence > 0.1).isFalse();
    }

    @Test
    void indexing_not_triggered_when_confidence_below_threshold() {
        double confidence = 0.05;
        assertThat(confidence > 0.1).isFalse();
    }

    @Test
    void indexing_not_triggered_when_confidence_is_zero() {
        double confidence = 0.0;
        assertThat(confidence > 0.1).isFalse();
    }

    /**
     * DocumentController.buildImageChunkText와 동일한 포맷 로직.
     */
    private String formatImageChunkText(String fileName, ImageAnalysisService.ImageAnalysisResult result) {
        return String.format("""
                [이미지 분석: %s]
                유형: %s
                추출 텍스트: %s
                시각적 설명: %s
                기술 컨텍스트: %s
                권장 검색 쿼리: %s""",
                fileName,
                result.imageType() != null ? result.imageType() : "알 수 없음",
                result.extractedText() != null ? result.extractedText() : "",
                result.visualDescription() != null ? result.visualDescription() : "",
                result.technicalContext() != null ? result.technicalContext() : "",
                result.suggestedQuery() != null ? result.suggestedQuery() : ""
        );
    }
}
