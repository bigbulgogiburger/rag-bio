package com.biorad.csrag.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAGAS 기반 RAG 평가 엔진.
 * OpenAI GPT-4.1-mini를 LLM-as-Judge로 사용하여 4개 메트릭을 계산한다.
 *
 * <ul>
 *   <li>Faithfulness: 답변의 각 claim이 context에 근거하는지</li>
 *   <li>Answer Relevancy: 답변이 질문에 얼마나 적절한지</li>
 *   <li>Context Precision: 상위 K개 검색 결과의 관련성</li>
 *   <li>Context Recall: ground truth facts가 context에 포함되는지</li>
 * </ul>
 */
public class RagasEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RagasEvaluator.class);
    private static final String MODEL = "gpt-4.1-mini";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RagasEvaluator(String apiKey, String baseUrl) {
        var requestFactory = new org.springframework.http.client.JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 골든 케이스에 대해 생성된 답변과 검색된 컨텍스트를 평가한다.
     *
     * @param goldenCase       골든 케이스 (질문 + ground truth)
     * @param generatedAnswer  RAG 시스템이 생성한 답변
     * @param retrievedContext 검색된 컨텍스트 청크 목록
     * @return 케이스별 평가 결과
     */
    public EvaluationReport.CaseResult evaluate(
            GoldenDataset.GoldenCase goldenCase,
            String generatedAnswer,
            List<String> retrievedContext
    ) {
        double faithfulness = evaluateFaithfulness(generatedAnswer, retrievedContext);
        double answerRelevancy = evaluateAnswerRelevancy(goldenCase.question(), generatedAnswer);
        double contextPrecision = evaluateContextPrecision(goldenCase.question(), retrievedContext);
        double contextRecall = evaluateContextRecall(goldenCase.groundTruthFacts(), retrievedContext);

        return new EvaluationReport.CaseResult(
                goldenCase.id(),
                faithfulness,
                answerRelevancy,
                contextPrecision,
                contextRecall
        );
    }

    /**
     * 여러 케이스를 평가하여 EvaluationReport를 반환한다.
     *
     * @param goldenCases       골든 케이스 목록
     * @param generatedAnswers  케이스 인덱스에 대응하는 생성된 답변 목록
     * @param retrievedContexts 케이스 인덱스에 대응하는 검색된 컨텍스트 목록
     */
    public EvaluationReport evaluateAll(
            List<GoldenDataset.GoldenCase> goldenCases,
            List<String> generatedAnswers,
            List<List<String>> retrievedContexts
    ) {
        if (goldenCases.size() != generatedAnswers.size() || goldenCases.size() != retrievedContexts.size()) {
            throw new IllegalArgumentException("goldenCases, generatedAnswers, retrievedContexts 크기가 일치해야 합니다.");
        }

        List<EvaluationReport.CaseResult> results = new ArrayList<>();
        for (int i = 0; i < goldenCases.size(); i++) {
            log.info("Evaluating case {}/{}: {}", i + 1, goldenCases.size(), goldenCases.get(i).id());
            EvaluationReport.CaseResult result = evaluate(goldenCases.get(i), generatedAnswers.get(i), retrievedContexts.get(i));
            results.add(result);
            log.info("Case result: {}", result);
        }

        double avgFaithfulness = results.stream().mapToDouble(EvaluationReport.CaseResult::faithfulness).average().orElse(0.0);
        double avgAnswerRelevancy = results.stream().mapToDouble(EvaluationReport.CaseResult::answerRelevancy).average().orElse(0.0);
        double avgContextPrecision = results.stream().mapToDouble(EvaluationReport.CaseResult::contextPrecision).average().orElse(0.0);
        double avgContextRecall = results.stream().mapToDouble(EvaluationReport.CaseResult::contextRecall).average().orElse(0.0);

        return new EvaluationReport(avgFaithfulness, avgAnswerRelevancy, avgContextPrecision, avgContextRecall, results);
    }

    // ─── Metric implementations ──────────────────────────────────────────────

    /**
     * Faithfulness: 답변의 각 claim이 context에 근거하는지 평가한다.
     * 1) LLM에 답변에서 claim 목록 추출 요청
     * 2) 각 claim이 context에 근거하는지 이진 판단
     * 점수 = 지지되는 claim 수 / 전체 claim 수
     */
    double evaluateFaithfulness(String generatedAnswer, List<String> retrievedContext) {
        try {
            String contextText = String.join("\n\n---\n\n", retrievedContext);

            // Step 1: claim 추출
            String claimPrompt = String.format(
                    "다음 답변에서 개별 사실적 주장(claim)을 줄바꿈으로 구분하여 추출하세요. " +
                    "각 줄에 하나의 claim만 작성하세요. claim 이외의 다른 텍스트는 절대 포함하지 마세요.\n\n" +
                    "답변:\n%s",
                    generatedAnswer
            );
            String claimsText = callLlm("You are a fact extraction assistant.", claimPrompt);
            List<String> claims = List.of(claimsText.split("\n"))
                    .stream()
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();

            if (claims.isEmpty()) {
                return 1.0;
            }

            // Step 2: 각 claim의 근거 여부 판단
            long supported = 0;
            for (String claim : claims) {
                String verifyPrompt = String.format(
                        "다음 컨텍스트를 참고하여 주어진 주장이 컨텍스트에 근거하는지 판단하세요.\n" +
                        "근거가 있으면 'YES', 없으면 'NO'만 응답하세요.\n\n" +
                        "컨텍스트:\n%s\n\n" +
                        "주장: %s",
                        contextText, claim
                );
                String answer = callLlm("You are a factual verification assistant.", verifyPrompt).trim().toUpperCase();
                if (answer.startsWith("YES")) {
                    supported++;
                }
            }

            return (double) supported / claims.size();
        } catch (Exception e) {
            log.warn("faithfulness evaluation failed: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Answer Relevancy: 답변이 질문에 얼마나 적절한지 0~1 점수로 평가한다.
     */
    double evaluateAnswerRelevancy(String question, String generatedAnswer) {
        try {
            String prompt = String.format(
                    "다음 질문에 대해 제시된 답변이 얼마나 적절하고 관련성이 있는지 0.0에서 1.0 사이의 숫자로만 평가하세요.\n" +
                    "0.0 = 전혀 관련 없음, 1.0 = 완전히 관련 있고 직접적인 답변\n" +
                    "숫자만 응답하세요. 다른 텍스트는 포함하지 마세요.\n\n" +
                    "질문: %s\n\n" +
                    "답변: %s",
                    question, generatedAnswer
            );
            String response = callLlm("You are an answer relevancy evaluator.", prompt).trim();
            return parseScore(response);
        } catch (Exception e) {
            log.warn("answerRelevancy evaluation failed: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Context Precision: 상위 K개 검색 결과 중 관련성 있는 결과의 비율을 평가한다.
     */
    double evaluateContextPrecision(String question, List<String> retrievedContext) {
        if (retrievedContext.isEmpty()) {
            return 0.0;
        }
        try {
            long relevant = 0;
            for (String chunk : retrievedContext) {
                String prompt = String.format(
                        "다음 질문에 대해 제시된 컨텍스트가 관련성이 있는지 판단하세요.\n" +
                        "관련성이 있으면 'YES', 없으면 'NO'만 응답하세요.\n\n" +
                        "질문: %s\n\n" +
                        "컨텍스트: %s",
                        question, chunk
                );
                String answer = callLlm("You are a context relevancy evaluator.", prompt).trim().toUpperCase();
                if (answer.startsWith("YES")) {
                    relevant++;
                }
            }
            return (double) relevant / retrievedContext.size();
        } catch (Exception e) {
            log.warn("contextPrecision evaluation failed: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Context Recall: ground truth facts가 검색된 context에 포함되는지 평가한다.
     * 점수 = context에서 발견된 fact 수 / 전체 fact 수
     */
    double evaluateContextRecall(List<String> groundTruthFacts, List<String> retrievedContext) {
        if (groundTruthFacts == null || groundTruthFacts.isEmpty()) {
            return 1.0;
        }
        if (retrievedContext.isEmpty()) {
            return 0.0;
        }
        try {
            String contextText = String.join("\n\n---\n\n", retrievedContext);
            long found = 0;
            for (String fact : groundTruthFacts) {
                String prompt = String.format(
                        "다음 컨텍스트에 주어진 사실이 포함되어 있는지 판단하세요.\n" +
                        "포함되어 있으면 'YES', 없으면 'NO'만 응답하세요.\n\n" +
                        "컨텍스트:\n%s\n\n" +
                        "사실: %s",
                        contextText, fact
                );
                String answer = callLlm("You are a fact coverage evaluator.", prompt).trim().toUpperCase();
                if (answer.startsWith("YES")) {
                    found++;
                }
            }
            return (double) found / groundTruthFacts.size();
        } catch (Exception e) {
            log.warn("contextRecall evaluation failed: {}", e.getMessage());
            return 0.0;
        }
    }

    // ─── OpenAI API call helper ──────────────────────────────────────────────

    private String callLlm(String systemPrompt, String userPrompt) {
        String response = restClient.post()
                .uri("/chat/completions")
                .body(Map.of(
                        "model", MODEL,
                        "messages", new Object[]{
                                Map.of("role", "system", "content", systemPrompt),
                                Map.of("role", "user", "content", userPrompt)
                        },
                        "temperature", 0.0,
                        "max_tokens", 512
                ))
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.isBlank()) {
                throw new IllegalStateException("OpenAI returned empty content");
            }
            return content;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI response parse failed", e);
        }
    }

    private double parseScore(String text) {
        try {
            // 숫자만 추출
            String cleaned = text.replaceAll("[^0-9.]", "").trim();
            double value = Double.parseDouble(cleaned);
            return Math.max(0.0, Math.min(1.0, value));
        } catch (NumberFormatException e) {
            log.warn("Failed to parse score from '{}', defaulting to 0.0", text);
            return 0.0;
        }
    }
}
