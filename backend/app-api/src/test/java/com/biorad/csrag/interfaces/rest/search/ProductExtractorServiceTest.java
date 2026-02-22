package com.biorad.csrag.interfaces.rest.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ProductExtractorServiceTest {

    private ProductExtractorService service;

    @BeforeEach
    void setUp() {
        service = new ProductExtractorService();
    }

    @Test
    void extractAll_multipleProducts_returnsUpTo3() {
        List<ProductExtractorService.ExtractedProduct> results =
                service.extractAll("naica와 CFX96 비교해주세요");

        assertThat(results).hasSize(2);
        Set<String> families = results.stream()
                .map(ProductExtractorService.ExtractedProduct::productFamily)
                .collect(Collectors.toSet());
        assertThat(families).containsExactlyInAnyOrder("naica", "CFX");
    }

    @Test
    void extractAll_sameFamily_deduplicated() {
        List<ProductExtractorService.ExtractedProduct> results =
                service.extractAll("QX200과 QX600 비교");

        // QX200 and QX600 both match "QX" family pattern
        Set<String> families = results.stream()
                .map(ProductExtractorService.ExtractedProduct::productFamily)
                .collect(Collectors.toSet());
        assertThat(families).containsExactly("QX");
    }

    @Test
    void extractAll_noProduct_empty() {
        List<ProductExtractorService.ExtractedProduct> results =
                service.extractAll("일반적인 질문입니다");

        assertThat(results).isEmpty();
    }

    @Test
    void extractAll_nullInput_empty() {
        assertThat(service.extractAll(null)).isEmpty();
    }

    @Test
    void extractAll_blankInput_empty() {
        assertThat(service.extractAll("   ")).isEmpty();
    }

    @Test
    void extract_backwardCompatible() {
        ProductExtractorService.ExtractedProduct result = service.extract("naica 시스템 문의");

        assertThat(result).isNotNull();
        assertThat(result.productFamily()).isEqualTo("naica");
        assertThat(result.confidence()).isGreaterThan(0);
    }

    @Test
    void extract_noMatch_returnsNull() {
        assertThat(service.extract("일반 질문")).isNull();
    }

    @Test
    void extract_nullInput_returnsNull() {
        assertThat(service.extract(null)).isNull();
    }

    @Test
    void extractAll_exactMatch_higherConfidence() {
        List<ProductExtractorService.ExtractedProduct> results =
                service.extractAll("naica 10x multiplex system");

        assertThat(results).isNotEmpty();
        // Exact match should have higher confidence (0.9)
        assertThat(results.get(0).confidence()).isEqualTo(0.9);
    }

    @Test
    void extractAll_bioPlex_detected() {
        List<ProductExtractorService.ExtractedProduct> results =
                service.extractAll("Bio-Plex 시스템 문의");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).productFamily()).isEqualTo("BioPlex");
    }

    @Test
    void extractAll_chemiDoc_detected() {
        List<ProductExtractorService.ExtractedProduct> results =
                service.extractAll("ChemiDoc 이미징 문의");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).productFamily()).isEqualTo("ChemiDoc");
    }
}
