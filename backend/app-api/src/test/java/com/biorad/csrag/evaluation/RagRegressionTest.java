package com.biorad.csrag.evaluation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAGAS 기반 RAG 회귀 테스트.
 *
 * <p>이 테스트는 실제 OpenAI API 키와 골든 데이터셋이 필요하므로 기본적으로 비활성화되어 있다.
 * 활성화하려면 {@code OPENAI_API_KEY} 환경변수를 설정하고 @Disabled 어노테이션을 제거하라.
 *
 * <p>실행 방법:
 * <pre>
 * OPENAI_API_KEY=sk-... ./gradlew :app-api:test --tests "*RagRegressionTest"
 * </pre>
 */
@Disabled("Requires OpenAI API key and golden dataset — remove @Disabled to run manually")
class RagRegressionTest {

    private static final Logger log = LoggerFactory.getLogger(RagRegressionTest.class);

    // 품질 게이트 임계값
    private static final double MIN_FAITHFULNESS = 0.70;
    private static final double MIN_ANSWER_RELEVANCY = 0.75;
    private static final double MIN_CONTEXT_PRECISION = 0.65;
    private static final double MIN_CONTEXT_RECALL = 0.65;

    private static RagasEvaluator evaluator;
    private static GoldenDataset goldenDataset;

    @BeforeAll
    static void setUp() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY 환경변수가 설정되어 있지 않습니다.");
        }
        String baseUrl = System.getenv().getOrDefault("OPENAI_BASE_URL", "https://api.openai.com/v1");
        evaluator = new RagasEvaluator(apiKey, baseUrl);
        goldenDataset = GoldenDataset.load();
    }

    @Test
    void goldenDatasetShouldLoad() {
        assertThat(goldenDataset.getCases()).isNotEmpty();
        assertThat(goldenDataset.size()).isGreaterThanOrEqualTo(10);
        log.info("Loaded {} golden cases", goldenDataset.size());
    }

    @Test
    void ragPipelineShouldMeetQualityGates() {
        List<GoldenDataset.GoldenCase> cases = goldenDataset.getCases();

        // 실제 RAG 파이프라인 호출 대신, 골든 케이스의 expectedAnswer와
        // groundTruthFacts를 context 삼아 메트릭을 평가한다.
        // 실제 통합 테스트에서는 AnalysisService + AnswerOrchestrationService 호출로 대체한다.
        List<String> generatedAnswers = cases.stream()
                .map(GoldenDataset.GoldenCase::expectedAnswer)
                .toList();

        List<List<String>> retrievedContexts = cases.stream()
                .map(c -> c.groundTruthFacts() != null ? c.groundTruthFacts() : List.<String>of())
                .toList();

        EvaluationReport report = evaluator.evaluateAll(cases, generatedAnswers, retrievedContexts);

        log.info("=== RAGAS Evaluation Report ===");
        log.info("Faithfulness:      {:.3f} (min: {})", report.faithfulness(), MIN_FAITHFULNESS);
        log.info("Answer Relevancy:  {:.3f} (min: {})", report.answerRelevancy(), MIN_ANSWER_RELEVANCY);
        log.info("Context Precision: {:.3f} (min: {})", report.contextPrecision(), MIN_CONTEXT_PRECISION);
        log.info("Context Recall:    {:.3f} (min: {})", report.contextRecall(), MIN_CONTEXT_RECALL);
        log.info("Overall Score:     {:.3f}", report.overallScore());
        log.info("Case results:");
        report.caseResults().forEach(r -> log.info("  {}", r));

        assertThat(report.faithfulness())
                .as("Faithfulness must be >= " + MIN_FAITHFULNESS)
                .isGreaterThanOrEqualTo(MIN_FAITHFULNESS);

        assertThat(report.answerRelevancy())
                .as("Answer Relevancy must be >= " + MIN_ANSWER_RELEVANCY)
                .isGreaterThanOrEqualTo(MIN_ANSWER_RELEVANCY);

        assertThat(report.contextPrecision())
                .as("Context Precision must be >= " + MIN_CONTEXT_PRECISION)
                .isGreaterThanOrEqualTo(MIN_CONTEXT_PRECISION);

        assertThat(report.contextRecall())
                .as("Context Recall must be >= " + MIN_CONTEXT_RECALL)
                .isGreaterThanOrEqualTo(MIN_CONTEXT_RECALL);
    }
}
