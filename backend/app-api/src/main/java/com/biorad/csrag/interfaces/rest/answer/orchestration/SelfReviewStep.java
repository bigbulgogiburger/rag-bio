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
        for (int idx = 0; idx < normalized.size(); idx++) {
            String sentence = normalized.get(idx);
            String key = sentence.toLowerCase();
            if (!seen.add(key)) {
                issues.add(new QualityIssue(
                        "DUPLICATION", "WARNING",
                        "동일 문장이 2회 이상 등장합니다: '" + truncate(sentence, 50) + "'",
                        "문장 '" + truncate(sentence, 50) + "'이(가) 반복됩니다. 중복을 제거하거나 통합하세요."
                ));
            }
        }

        // Check for near-duplicate paragraphs (same core content, different wording)
        for (int i = 0; i < normalized.size(); i++) {
            for (int j = i + 1; j < normalized.size(); j++) {
                double similarity = computeSimilarity(normalized.get(i), normalized.get(j));
                if (similarity > 0.7) {
                    int pctSimilarity = (int) Math.round(similarity * 100);
                    issues.add(new QualityIssue(
                            "DUPLICATION", "WARNING",
                            "단락 " + (i + 1) + " '" + truncate(normalized.get(i), 50) +
                                    "'와 단락 " + (j + 1) + " '" + truncate(normalized.get(j), 50) +
                                    "'이 유사(" + pctSimilarity + "%)",
                            "단락 " + (j + 1) + "을 제거하거나 단락 " + (i + 1) + "과 통합하세요."
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
            String phantomList = phantomProducts.stream()
                    .map(p -> "'" + p + "'")
                    .collect(Collectors.joining(", "));
            String evidenceList = evidenceProducts.stream()
                    .map(p -> "'" + p + "'")
                    .collect(Collectors.joining(", "));
            issues.add(new QualityIssue(
                    "INCONSISTENCY", "CRITICAL",
                    "답변에 " + phantomList + "가 언급되었으나 근거에는 " + evidenceList + "만 존재합니다.",
                    "근거에 없는 " + phantomList + "을(를) 제거하고, 근거 자료에 명시된 제품명(" + evidenceList + ")만 사용하세요."
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
                    // Find the closest numeric values from evidence for suggestion
                    String closestValues = allEvidenceNumerics.stream()
                            .limit(5)
                            .map(v -> "'" + v + "'")
                            .collect(Collectors.joining(", "));
                    issues.add(new QualityIssue(
                            "INCONSISTENCY", "WARNING",
                            "답변의 수치 '" + draftValue + "'가 근거 자료에서 확인되지 않습니다. 근거에 있는 수치: " + closestValues,
                            "'" + draftValue + "'을(를) 근거 자료에 명시된 정확한 수치로 수정하세요."
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
        Pattern procedureQuestionPattern = Pattern.compile("(?i)(방법|어떻게|절차|순서|프로토콜|protocol|how)");
        boolean questionAsksProcedure = question != null && procedureQuestionPattern.matcher(question).find();

        if (questionAsksProcedure && hasConditions && !hasProcedures) {
            // Extract the specific sub-question or topic that asks for procedure
            String procedureTopic = extractProcedureTopic(question);
            String topicDetail = procedureTopic != null
                    ? "하위질문 '" + truncate(procedureTopic, 50) + "'에 대한 구체적 절차(단계별 방법)가 누락되었습니다."
                    : "질문에서 요청한 절차(HOW)에 대한 구체적 단계별 방법이 누락되었습니다.";
            issues.add(new QualityIssue(
                    "INCOMPLETE_PROCEDURE", "CRITICAL",
                    topicDetail,
                    "조건(WHAT)뿐 아니라 '" + truncate(procedureTopic != null ? procedureTopic : "해당 절차", 50) + "'에 대한 구체적 수행 단계를 포함하세요."
            ));
        }
    }

    /**
     * Extracts the sub-question or clause that asks for a procedure.
     * Looks for sentences/clauses containing procedure keywords.
     */
    private String extractProcedureTopic(String question) {
        if (question == null) return null;

        // Try to find a numbered sub-question that contains procedure keywords
        Pattern numberedSubQ = Pattern.compile("(?:질문\\s*)?(?:#)?(\\d+)[).:]\\s*([^\n]+)");
        Matcher subQMatcher = numberedSubQ.matcher(question);
        Pattern procKeywords = Pattern.compile("(?i)(방법|어떻게|절차|순서|프로토콜|protocol|how|캘리브레이션|설정|세팅|수행)");

        while (subQMatcher.find()) {
            String subQText = subQMatcher.group(2).trim();
            if (procKeywords.matcher(subQText).find()) {
                return subQText;
            }
        }

        // Fallback: find the clause containing a procedure keyword
        String[] clauses = question.split("[,??\n]+");
        for (String clause : clauses) {
            String trimmed = clause.trim();
            if (trimmed.length() > 5 && procKeywords.matcher(trimmed).find()) {
                return trimmed;
            }
        }

        return null;
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
                Set<String> availableFiles = evidences.stream()
                        .map(EvidenceItem::fileName)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                String availableList = availableFiles.isEmpty()
                        ? "(파일명 정보 없음)"
                        : availableFiles.stream().map(f -> "'" + f + "'").collect(Collectors.joining(", "));
                issues.add(new QualityIssue(
                        "CITATION_MISMATCH", "WARNING",
                        "인용 '(" + citedFile + ", p." + citedPageStart + ")'에서 해당 파일명이 근거 목록에 없습니다. 가능한 파일: " + availableList,
                        "인용 '(" + citedFile + ", p." + citedPageStart + ")'을(를) 근거에 있는 파일(" + availableList + ")로 수정하세요."
                ));
            }
        }
    }

    private void checkSubQuestionCompleteness(String draft, String question, List<QualityIssue> issues) {
        if (draft == null || question == null) return;

        // 질문에서 하위 질문 수 및 텍스트 감지
        // 패턴: "질문 ?N)", "N)", "N.", "#N)" 등
        Pattern subQPattern = Pattern.compile("(?:질문\\s*)?(?:#)?(\\d+)[).:]\\s*([^\n]*)");
        Matcher qMatcher = subQPattern.matcher(question);
        Map<Integer, String> questionMap = new TreeMap<>();
        while (qMatcher.find()) {
            int num = Integer.parseInt(qMatcher.group(1));
            String text = qMatcher.group(2).trim();
            questionMap.put(num, text);
        }

        if (questionMap.size() <= 1) return; // 단일 질문이면 검증 불필요

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
        int questionCount = questionMap.size();

        if (totalAnswered < questionCount) {
            // Identify which specific sub-questions are missing
            Set<Integer> missingNumbers = new TreeSet<>(questionMap.keySet());
            missingNumbers.removeAll(answerNumbers);

            List<String> missingDetails = new ArrayList<>();
            for (int num : missingNumbers) {
                String qText = questionMap.get(num);
                if (qText != null && !qText.isEmpty()) {
                    missingDetails.add("하위질문 " + num + " '" + truncate(qText, 50) + "'");
                } else {
                    missingDetails.add("하위질문 " + num);
                }
            }

            String missingList = missingDetails.isEmpty()
                    ? (questionCount - totalAnswered) + "개의 하위질문"
                    : String.join(", ", missingDetails);

            issues.add(new QualityIssue(
                    "SUB_QUESTION_INCOMPLETE", "CRITICAL",
                    "질문에 " + questionCount + "개의 하위 질문이 있지만 답변에는 " + totalAnswered + "개만 답변되었습니다. 누락: " + missingList,
                    missingList + "에 대한 답변을 추가하거나, 답변할 수 없는 경우 '확인 후 답변드리겠습니다'를 포함하세요."
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
                .map(i -> "[" + i.category() + "] " + i.description() + " → " + i.suggestion())
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
