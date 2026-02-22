package com.biorad.csrag.interfaces.rest.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgresKeywordSearchService.normalizeKorean() 단위 테스트.
 * Java와 PL/pgSQL(V27 migration) 정규화 로직의 계약을 문서화한다.
 */
class NormalizeKoreanTest {

    @Test
    void null_and_blank_passthrough() {
        assertThat(PostgresKeywordSearchService.normalizeKorean(null)).isNull();
        assertThat(PostgresKeywordSearchService.normalizeKorean("")).isEqualTo("");
        // blank 입력은 isBlank() 체크로 원본 반환
        assertThat(PostgresKeywordSearchService.normalizeKorean("   ")).isEqualTo("   ");
    }

    @Test
    void strips_eomi_endings() {
        assertThat(PostgresKeywordSearchService.normalizeKorean("검사합니다")).isEqualTo("검사");
        assertThat(PostgresKeywordSearchService.normalizeKorean("확인됩니다")).isEqualTo("확인");
        assertThat(PostgresKeywordSearchService.normalizeKorean("발생했습니다")).isEqualTo("발생");
        assertThat(PostgresKeywordSearchService.normalizeKorean("처리되었습니다")).isEqualTo("처리");
        assertThat(PostgresKeywordSearchService.normalizeKorean("입력하세요")).isEqualTo("입력");
    }

    @Test
    void strips_josa_particles() {
        assertThat(PostgresKeywordSearchService.normalizeKorean("장비에서")).isEqualTo("장비");
        assertThat(PostgresKeywordSearchService.normalizeKorean("오류가")).isEqualTo("오류");
        assertThat(PostgresKeywordSearchService.normalizeKorean("결과를")).isEqualTo("결과");
        assertThat(PostgresKeywordSearchService.normalizeKorean("시스템은")).isEqualTo("시스템");
        assertThat(PostgresKeywordSearchService.normalizeKorean("데이터의")).isEqualTo("데이터");
    }

    @Test
    void preserves_english_and_technical_terms() {
        assertThat(PostgresKeywordSearchService.normalizeKorean("ddPCR")).isEqualTo("ddPCR");
        assertThat(PostgresKeywordSearchService.normalizeKorean("CFX96")).isEqualTo("CFX96");
        assertThat(PostgresKeywordSearchService.normalizeKorean("QX200")).isEqualTo("QX200");
    }

    @Test
    void mixed_korean_and_english() {
        assertThat(PostgresKeywordSearchService.normalizeKorean("ddPCR 장비에서 오류가 발생했습니다"))
                .isEqualTo("ddPCR 장비 오류 발생");
    }

    @Test
    void josa_not_stripped_when_followed_by_korean() {
        // "이전" — "이" 뒤에 한글 "전"이 오므로 조사 제거 안 함
        assertThat(PostgresKeywordSearchService.normalizeKorean("이전")).isEqualTo("이전");
        // "가격" — "가" 뒤에 한글 "격"이 오므로 조사 제거 안 함
        assertThat(PostgresKeywordSearchService.normalizeKorean("가격")).isEqualTo("가격");
    }

    @Test
    void collapses_multiple_spaces() {
        assertThat(PostgresKeywordSearchService.normalizeKorean("장비에서   오류가   발생했습니다"))
                .isEqualTo("장비 오류 발생");
    }

    @Test
    void complex_sentence() {
        // "의" after "droplet" is NOT stripped (lookbehind requires preceding Korean char)
        String input = "naica 시스템에서 droplet의 형광 측정 결과를 확인하고 분석합니다";
        String expected = "naica 시스템 droplet의 형광 측정 결과 확인 분석";
        assertThat(PostgresKeywordSearchService.normalizeKorean(input)).isEqualTo(expected);
    }
}
