package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rule-based self-review step that checks draft quality without LLM calls.
 * Checks: duplication, inconsistency, procedure completeness, citation accuracy.
 */
@Component
public class SelfReviewStep {

    private static final Pattern PRODUCT_NAME_PATTERN = Pattern.compile(
            "(?i)(CFX\\s*\\d+|QX\\s*\\d+|ddPCR|iQ\\s*\\d*|SsoAdvanced|iTaq|" +
            "Clarity\\s*Western|Mini-PROTEAN|Trans-Blot|ChemiDoc|Gel\\s*Doc|" +
            "NGC\\s*\\d*|Bio-Plex|Aurum|Chelex|InstaGene|Precision\\s*Melt|" +
            "SYBR\\s*Green|EvaGreen|naica|vericheck|Vericheck)"
    );

    private static final Pattern NUMERIC_VALUE_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?\\s*(?:°C|℃|%|µL|µl|mL|ml|ng|µg|mg|rpm|min|sec|s|hr|h|bp|kb))"
    );

    private static final Pattern CONDITION_KEYWORDS = Pattern.compile(
            "(?i)(경우|조건|때|필요|설정|온도|농도|시간|횟수|사이클)"
    );

    private static final Pattern PROCEDURE_KEYWORDS = Pattern.compile(
            "(?i)(순서|단계|절차|방법|먼저|다음|이후|진행|수행|실행|준비|혼합|첨가|추가|넣|올려|세팅)"
    );

    public SelfReviewResult review(String draft, List<EvidenceItem> evidences, String question) {
        List<QualityIssue> issues = new ArrayList<>();

        checkDuplication(draft, issues);
        checkConsistency(draft, evidences, issues);
        checkProcedureCompleteness(draft, question, issues);
        checkCitationAccuracy(draft, evidences, issues);
        checkSubQuestionCompleteness(draft, question, issues);

        boolean passed = issues.stream().noneMatch(i -> "CRITICAL".equals(i.severity()));
        String feedback = buildFeedback(issues);

        return new SelfReviewResult(passed, issues, feedback);
    }

    private void checkDuplication(String draft, List<QualityIssue> issues) {
        if (draft == null || draft.isBlank()) return;

        String[] sentences = draft.split("[.。\n]+");
        List<String> normalized = Arrays.stream(sentences)
                .map(s -> s.replaceAll("\\s+", " ").trim())
                .filter(s -> s.length() > 15)
                .toList();

        Set<String> seen = new HashSet<>();
        for (String sentence : normalized) {
            String key = sentence.toLowerCase();
            if (!seen.add(key)) {
                issues.add(new QualityIssue(
                        "DUPLICATION", "WARNING",
                        "동일 문장이 2회 이상 등장합니다: " + truncate(sentence, 50),
                        "중복 문장을 제거하거나 통합하세요"
                ));
            }
        }

        // Check for near-duplicate paragraphs (same core content, different wording)
        for (int i = 0; i < normalized.size(); i++) {
            for (int j = i + 1; j < normalized.size(); j++) {
                if (computeSimilarity(normalized.get(i), normalized.get(j)) > 0.7) {
                    issues.add(new QualityIssue(
                            "DUPLICATION", "WARNING",
                            "유사한 내용이 반복됩니다: " + truncate(normalized.get(i), 40),
                            "유사 문장을 하나로 통합하세요"
                    ));
                    break;
                }
            }
        }
    }

    private void checkConsistency(String draft, List<EvidenceItem> evidences, List<QualityIssue> issues) {
        if (draft == null || evidences == null || evidences.isEmpty()) return;

        // Collect product names from evidence excerpts
        Set<String> evidenceProducts = new HashSet<>();
        for (EvidenceItem ev : evidences) {
            if (ev.excerpt() != null) {
                Matcher m = PRODUCT_NAME_PATTERN.matcher(ev.excerpt());
                while (m.find()) {
                    evidenceProducts.add(m.group(1).toLowerCase().replaceAll("\\s+", ""));
                }
            }
        }

        // Collect product names from draft
        Set<String> draftProducts = new HashSet<>();
        Matcher m = PRODUCT_NAME_PATTERN.matcher(draft);
        while (m.find()) {
            draftProducts.add(m.group(1).toLowerCase().replaceAll("\\s+", ""));
        }

        // Check for products mentioned in draft but not in evidence
        Set<String> phantomProducts = new HashSet<>(draftProducts);
        phantomProducts.removeAll(evidenceProducts);
        if (!phantomProducts.isEmpty() && !evidenceProducts.isEmpty()) {
            issues.add(new QualityIssue(
                    "INCONSISTENCY", "CRITICAL",
                    "답변에 근거에 없는 제품명이 등장합니다: " + String.join(", ", phantomProducts),
                    "근거 자료에 명시된 제품명만 사용하세요"
            ));
        }

        // Check numeric value consistency between evidence and draft
        Map<String, Set<String>> evidenceValues = extractNumericValues(evidences);
        checkNumericConsistency(draft, evidenceValues, issues);
    }

    private Map<String, Set<String>> extractNumericValues(List<EvidenceItem> evidences) {
        Map<String, Set<String>> values = new HashMap<>();
        for (EvidenceItem ev : evidences) {
            if (ev.excerpt() != null) {
                Matcher m = NUMERIC_VALUE_PATTERN.matcher(ev.excerpt());
                while (m.find()) {
                    values.computeIfAbsent(ev.documentId(), k -> new HashSet<>())
                            .add(m.group(1).replaceAll("\\s+", ""));
                }
            }
        }
        return values;
    }

    private void checkNumericConsistency(String draft, Map<String, Set<String>> evidenceValues, List<QualityIssue> issues) {
        if (evidenceValues.isEmpty()) return;

        Set<String> allEvidenceNumerics = evidenceValues.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        Matcher m = NUMERIC_VALUE_PATTERN.matcher(draft);
        while (m.find()) {
            String draftValue = m.group(1).replaceAll("\\s+", "");
            // Only flag if evidence has numeric values but this specific one is absent
            if (!allEvidenceNumerics.isEmpty() && !allEvidenceNumerics.contains(draftValue)) {
                // Check if the base number exists with a different unit (likely a template artifact)
                String numOnly = draftValue.replaceAll("[^0-9.]", "");
                boolean numberExistsElsewhere = allEvidenceNumerics.stream()
                        .anyMatch(v -> v.startsWith(numOnly));
                if (!numberExistsElsewhere) {
                    issues.add(new QualityIssue(
                            "INCONSISTENCY", "WARNING",
                            "답변의 수치(" + draftValue + ")가 근거 자료에서 확인되지 않습니다",
                            "근거 자료의 정확한 수치를 인용하세요"
                    ));
                }
            }
        }
    }

    private void checkProcedureCompleteness(String draft, String question, List<QualityIssue> issues) {
        if (draft == null || draft.isBlank()) return;

        boolean hasConditions = CONDITION_KEYWORDS.matcher(draft).find();
        boolean hasProcedures = PROCEDURE_KEYWORDS.matcher(draft).find();

        // If question asks about how-to / procedure
        boolean questionAsksProcedure = question != null &&
                Pattern.compile("(?i)(방법|어떻게|절차|순서|프로토콜|protocol|how)").matcher(question).find();

        if (questionAsksProcedure && hasConditions && !hasProcedures) {
            issues.add(new QualityIssue(
                    "INCOMPLETE_PROCEDURE", "CRITICAL",
                    "조건(WHAT)은 언급되었으나 절차(HOW)가 누락되었습니다",
                    "조건뿐 아니라 구체적인 수행 절차도 포함하세요"
            ));
        }
    }

    private void checkCitationAccuracy(String draft, List<EvidenceItem> evidences, List<QualityIssue> issues) {
        if (draft == null || evidences == null || evidences.isEmpty()) return;

        // Check that cited file names in the draft actually exist in evidence
        Pattern citationInDraft = Pattern.compile("\\(([^,)]+\\.pdf),\\s*p\\.(\\d+)(?:-(\\d+))?\\)", Pattern.CASE_INSENSITIVE);
        Matcher m = citationInDraft.matcher(draft);

        while (m.find()) {
            String citedFile = m.group(1).trim();
            int citedPageStart = Integer.parseInt(m.group(2));

            boolean found = evidences.stream().anyMatch(ev ->
                    ev.fileName() != null &&
                    ev.fileName().equalsIgnoreCase(citedFile) &&
                    (ev.pageStart() == null || ev.pageStart() <= citedPageStart)
            );

            if (!found) {
                issues.add(new QualityIssue(
                        "CITATION_MISMATCH", "WARNING",
                        "인용된 문서(" + citedFile + " p." + citedPageStart + ")가 근거 목록에 없습니다",
                        "실제 검색된 근거의 파일명과 페이지를 사용하세요"
                ));
            }
        }
    }

    private void checkSubQuestionCompleteness(String draft, String question, List<QualityIssue> issues) {
        if (draft == null || question == null) return;

        // 질문에서 하위 질문 수 감지
        // 패턴: "질문 ?N)", "N)", "N.", "#N)" 등
        Pattern subQPattern = Pattern.compile("(?:질문\\s*)?(?:#)?(\\d+)[).:]");
        Matcher qMatcher = subQPattern.matcher(question);
        Set<Integer> questionNumbers = new TreeSet<>();
        while (qMatcher.find()) {
            questionNumbers.add(Integer.parseInt(qMatcher.group(1)));
        }

        if (questionNumbers.size() <= 1) return; // 단일 질문이면 검증 불필요

        // 답변에서 하위 답변 수 감지
        Pattern subAPattern = Pattern.compile("(?:#)?(\\d+)[).]");
        Matcher aMatcher = subAPattern.matcher(draft);
        Set<Integer> answerNumbers = new TreeSet<>();
        while (aMatcher.find()) {
            answerNumbers.add(Integer.parseInt(aMatcher.group(1)));
        }

        // "확인 후 답변" 패턴도 답변으로 카운트
        int dontKnowCount = countOccurrences(draft, "확인 후");

        int totalAnswered = answerNumbers.size() + dontKnowCount;
        int questionCount = questionNumbers.size();

        if (totalAnswered < questionCount) {
            issues.add(new QualityIssue(
                    "SUB_QUESTION_INCOMPLETE", "CRITICAL",
                    "질문에 " + questionCount + "개의 하위 질문이 있지만 답변에는 " + totalAnswered + "개만 답변되었습니다",
                    "모든 하위 질문에 대해 답변하거나, 답변할 수 없는 경우 '확인 후 답변드리겠습니다'를 포함하세요"
            ));
        }
    }

    private int countOccurrences(String text, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }

    private double computeSimilarity(String a, String b) {
        Set<String> wordsA = tokenize(a);
        Set<String> wordsB = tokenize(b);
        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(wordsA);
        intersection.retainAll(wordsB);

        Set<String> union = new HashSet<>(wordsA);
        union.addAll(wordsB);

        return (double) intersection.size() / union.size();
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("\\s+"))
                .filter(w -> w.length() > 1)
                .collect(Collectors.toSet());
    }

    private String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private String buildFeedback(List<QualityIssue> issues) {
        if (issues.isEmpty()) return "";
        return issues.stream()
                .filter(i -> "CRITICAL".equals(i.severity()) || "WARNING".equals(i.severity()))
                .map(i -> "[" + i.category() + "] " + i.suggestion())
                .collect(Collectors.joining("; "));
    }

    public record SelfReviewResult(
            boolean passed,
            List<QualityIssue> issues,
            String feedback
    ) {}

    public record QualityIssue(
            String category,
            String severity,
            String description,
            String suggestion
    ) {}
}
