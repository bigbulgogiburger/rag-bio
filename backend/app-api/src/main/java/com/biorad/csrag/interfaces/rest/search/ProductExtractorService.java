package com.biorad.csrag.interfaces.rest.search;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProductExtractorService {

    public record ExtractedProduct(String productName, String productFamily, double confidence) {}

    private record ProductPattern(Pattern pattern, String productFamily, boolean exact) {}

    private static final List<ProductPattern> PRODUCT_PATTERNS = List.of(
            // More specific patterns first within each family
            new ProductPattern(
                    Pattern.compile("naica\\s*(?:10x)?\\s*multiplex", Pattern.CASE_INSENSITIVE),
                    "naica", true),
            new ProductPattern(
                    Pattern.compile("naica", Pattern.CASE_INSENSITIVE),
                    "naica", false),
            new ProductPattern(
                    Pattern.compile("vericheck\\s*(?:ddPCR)?", Pattern.CASE_INSENSITIVE),
                    "vericheck", true),
            new ProductPattern(
                    Pattern.compile("QX\\s*\\d{3,4}", Pattern.CASE_INSENSITIVE),
                    "QX", true),
            new ProductPattern(
                    Pattern.compile("QX\\s*ONE", Pattern.CASE_INSENSITIVE),
                    "QX", true),
            new ProductPattern(
                    Pattern.compile("CFX\\s*(?:Opus|Connect|\\d{2,3})", Pattern.CASE_INSENSITIVE),
                    "CFX", true),
            new ProductPattern(
                    Pattern.compile("ddPCR\\s*(?:Supermix|Multiplex|Library)", Pattern.CASE_INSENSITIVE),
                    "ddPCR", true),
            new ProductPattern(
                    Pattern.compile("Bio-?\\s*Plex", Pattern.CASE_INSENSITIVE),
                    "BioPlex", true),
            new ProductPattern(
                    Pattern.compile("ChemiDoc", Pattern.CASE_INSENSITIVE),
                    "ChemiDoc", false),
            new ProductPattern(
                    Pattern.compile("NGC\\s*\\d*", Pattern.CASE_INSENSITIVE),
                    "NGC", false),
            new ProductPattern(
                    Pattern.compile("Mini-PROTEAN", Pattern.CASE_INSENSITIVE),
                    "MiniPROTEAN", false),
            new ProductPattern(
                    Pattern.compile("Trans-Blot", Pattern.CASE_INSENSITIVE),
                    "TransBlot", false)
    );

    private static final int MAX_EXTRACT_COUNT = 3;

    /**
     * 질문 텍스트에서 Bio-Rad 제품명을 추출한다.
     * @return 추출된 제품 정보, 없으면 null
     */
    public ExtractedProduct extract(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }

        String bestMatch = null;
        String bestFamily = null;
        boolean bestExact = false;

        for (ProductPattern pp : PRODUCT_PATTERNS) {
            Matcher matcher = pp.pattern().matcher(question);
            if (matcher.find()) {
                String matched = matcher.group();
                if (bestMatch == null || matched.length() > bestMatch.length()) {
                    bestMatch = matched;
                    bestFamily = pp.productFamily();
                    bestExact = pp.exact();
                }
            }
        }

        if (bestMatch == null) {
            return null;
        }

        double confidence = bestExact ? 0.9 : 0.6;
        return new ExtractedProduct(bestMatch.trim(), bestFamily, confidence);
    }

    /**
     * 질문 텍스트에서 모든 Bio-Rad 제품명을 추출한다 (최대 3개).
     * family 중복 시 confidence가 높은 것만 유지한다.
     * @return 신뢰도 내림차순 정렬된 추출 결과 (없으면 빈 리스트)
     */
    public List<ExtractedProduct> extractAll(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }

        // family별 최고 confidence 제품만 유지
        Map<String, ExtractedProduct> bestByFamily = new LinkedHashMap<>();

        for (ProductPattern pp : PRODUCT_PATTERNS) {
            Matcher matcher = pp.pattern().matcher(question);
            while (matcher.find()) {
                String matched = matcher.group().trim();
                double confidence = pp.exact() ? 0.9 : 0.6;
                ExtractedProduct candidate = new ExtractedProduct(matched, pp.productFamily(), confidence);

                bestByFamily.merge(pp.productFamily(), candidate,
                        (existing, newer) -> newer.confidence() > existing.confidence() ? newer : existing);
            }
        }

        if (bestByFamily.isEmpty()) {
            return List.of();
        }

        return bestByFamily.values().stream()
                .sorted(Comparator.comparingDouble(ExtractedProduct::confidence).reversed())
                .limit(MAX_EXTRACT_COUNT)
                .toList();
    }
}
