package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.search.ProductExtractorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class QuestionDecomposerService {

    private static final Logger log = LoggerFactory.getLogger(QuestionDecomposerService.class);

    private final ProductExtractorService productExtractorService;

    public QuestionDecomposerService(ProductExtractorService productExtractorService) {
        this.productExtractorService = productExtractorService;
    }

    // Greeting/signature patterns to strip
    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "(?i)(안녕하세요[.,]?\\s*|문의\\s*드립니다[.,]?\\s*|감사합니다[.,]?\\s*|수고하세요[.,]?\\s*|" +
            "답변\\s*부탁\\s*드립니다[.,]?\\s*|확인\\s*부탁\\s*드립니다[.,]?\\s*)");

    // Numbered question patterns (priority order)
    private static final Pattern QUESTION_PREFIX_PATTERN = Pattern.compile(
            "질문\\s*\\d+[).:\\s]|\\d+[).]\\s*|#\\d+[)]\\s*");

    // Bio-Rad product name patterns (case-insensitive)
    private static final Pattern PRODUCT_PATTERN = Pattern.compile(
            "(?i)(naica(?:\\s+\\w+)*(?:\\s+(?:system|mix|platform))?|" +
            "vericheck|" +
            "QX\\d+[\\w]*|" +
            "CFX\\d+[\\w]*(?:\\s+\\w+)?|" +
            "ddPCR(?:\\s+\\w+)*(?:\\s+(?:system|mix|supermix))?|" +
            "Bio-Plex(?:\\s+\\w+)*|" +
            "naica\\s+(?:\\d+x\\s+)?multiplex\\s+ddpcr\\s+mix)");

    public DecomposedQuestion decompose(String question) {
        if (question == null || question.isBlank()) {
            log.debug("Empty question received, returning single empty sub-question");
            return new DecomposedQuestion(question, List.of(new SubQuestion(1, "", null)), null);
        }

        String original = question;
        String productContext = extractProductContext(question);
        String cleaned = removeGreetings(question).strip();

        List<SubQuestion> subQuestions = splitByNumberPattern(cleaned, productContext);

        if (subQuestions.isEmpty()) {
            subQuestions = List.of(new SubQuestion(1, cleaned, productContext));
        }

        // 하위 질문별 제품 패밀리 추출
        subQuestions = enrichWithProductFamilies(subQuestions);

        log.debug("Decomposed question into {} sub-questions, productContext={}",
                subQuestions.size(), productContext);

        return new DecomposedQuestion(original, subQuestions, productContext);
    }

    /**
     * 각 하위 질문에서 제품명을 추출하여 productFamilies를 설정한다.
     */
    private List<SubQuestion> enrichWithProductFamilies(List<SubQuestion> subQuestions) {
        List<SubQuestion> enriched = new ArrayList<>(subQuestions.size());
        for (SubQuestion sq : subQuestions) {
            List<ProductExtractorService.ExtractedProduct> products =
                    productExtractorService.extractAll(sq.question());
            if (products.isEmpty()) {
                enriched.add(sq);
            } else {
                Set<String> families = products.stream()
                        .map(ProductExtractorService.ExtractedProduct::productFamily)
                        .collect(Collectors.toSet());
                enriched.add(new SubQuestion(sq.index(), sq.question(), sq.context(), families));
            }
        }
        return enriched;
    }

    private String extractProductContext(String text) {
        Matcher matcher = PRODUCT_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group().strip();
        }
        return null;
    }

    private String removeGreetings(String text) {
        return GREETING_PATTERN.matcher(text).replaceAll("").strip();
    }

    private List<SubQuestion> splitByNumberPattern(String text, String productContext) {
        // Find all positions where a numbered question pattern appears
        Matcher matcher = QUESTION_PREFIX_PATTERN.matcher(text);

        List<int[]> matchPositions = new ArrayList<>();
        while (matcher.find()) {
            matchPositions.add(new int[]{matcher.start(), matcher.end()});
        }

        if (matchPositions.isEmpty()) {
            return List.of();
        }

        // If only one match found and it's at or near the beginning, treat as single question
        if (matchPositions.size() == 1) {
            int[] pos = matchPositions.get(0);
            String questionText = text.substring(pos[1]).strip();
            if (!questionText.isBlank()) {
                return List.of(new SubQuestion(1, questionText, productContext));
            }
            return List.of();
        }

        List<SubQuestion> result = new ArrayList<>();
        int index = 1;

        for (int i = 0; i < matchPositions.size(); i++) {
            int contentStart = matchPositions.get(i)[1];
            int contentEnd = (i + 1 < matchPositions.size())
                    ? matchPositions.get(i + 1)[0]
                    : text.length();

            String questionText = text.substring(contentStart, contentEnd).strip();

            if (!questionText.isBlank()) {
                result.add(new SubQuestion(index, questionText, productContext));
                index++;
            }
        }

        return result;
    }
}
