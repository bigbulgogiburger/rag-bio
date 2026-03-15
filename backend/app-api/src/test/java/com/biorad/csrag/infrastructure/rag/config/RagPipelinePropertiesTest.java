package com.biorad.csrag.infrastructure.rag.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RagPipelineProperties} 기본값 및 getter/setter 검증 테스트.
 */
class RagPipelinePropertiesTest {

    private RagPipelineProperties props;

    @BeforeEach
    void setUp() {
        props = new RagPipelineProperties();
    }

    // --- Budget ---

    @Test
    void budget_defaultMaxTokensPerInquiry_is25000() {
        assertThat(props.getBudget().getMaxTokensPerInquiry()).isEqualTo(25000);
    }

    @Test
    void budget_setterOverridesDefault() {
        props.getBudget().setMaxTokensPerInquiry(50000);
        assertThat(props.getBudget().getMaxTokensPerInquiry()).isEqualTo(50000);
    }

    // --- Cost ---

    @Test
    void cost_defaults() {
        assertThat(props.getCost().getDailyBudgetUsd()).isEqualTo(50.0);
        assertThat(props.getCost().getAlertThresholdPercent()).isEqualTo(80);
        assertThat(props.getCost().getPerInquiryMaxUsd()).isEqualTo(0.10);
    }

    @Test
    void cost_settersOverrideDefaults() {
        props.getCost().setDailyBudgetUsd(100.0);
        props.getCost().setAlertThresholdPercent(90);
        props.getCost().setPerInquiryMaxUsd(0.25);

        assertThat(props.getCost().getDailyBudgetUsd()).isEqualTo(100.0);
        assertThat(props.getCost().getAlertThresholdPercent()).isEqualTo(90);
        assertThat(props.getCost().getPerInquiryMaxUsd()).isEqualTo(0.25);
    }

    // --- Evidence ---

    @Test
    void evidence_defaults() {
        assertThat(props.getEvidence().getMaxItems()).isEqualTo(8);
        assertThat(props.getEvidence().getMinScore()).isEqualTo(0.30);
        assertThat(props.getEvidence().getMaxPerDocument()).isEqualTo(3);
        assertThat(props.getEvidence().isEnsureSourceDiversity()).isTrue();
    }

    @Test
    void evidence_settersOverrideDefaults() {
        props.getEvidence().setMaxItems(12);
        props.getEvidence().setMinScore(0.50);
        props.getEvidence().setMaxPerDocument(5);
        props.getEvidence().setEnsureSourceDiversity(false);

        assertThat(props.getEvidence().getMaxItems()).isEqualTo(12);
        assertThat(props.getEvidence().getMinScore()).isEqualTo(0.50);
        assertThat(props.getEvidence().getMaxPerDocument()).isEqualTo(5);
        assertThat(props.getEvidence().isEnsureSourceDiversity()).isFalse();
    }

    // --- Search ---

    @Test
    void search_defaults() {
        assertThat(props.getSearch().getRrfK()).isEqualTo(60);
        assertThat(props.getSearch().getVectorWeight()).isEqualTo(1.0);
        assertThat(props.getSearch().getKeywordWeight()).isEqualTo(1.0);
        assertThat(props.getSearch().getMinVectorScore()).isEqualTo(0.25);
        assertThat(props.getSearch().getTopK()).isEqualTo(5);
    }

    @Test
    void search_settersOverrideDefaults() {
        props.getSearch().setRrfK(100);
        props.getSearch().setVectorWeight(0.8);
        props.getSearch().setKeywordWeight(0.6);
        props.getSearch().setMinVectorScore(0.35);
        props.getSearch().setTopK(10);

        assertThat(props.getSearch().getRrfK()).isEqualTo(100);
        assertThat(props.getSearch().getVectorWeight()).isEqualTo(0.8);
        assertThat(props.getSearch().getKeywordWeight()).isEqualTo(0.6);
        assertThat(props.getSearch().getMinVectorScore()).isEqualTo(0.35);
        assertThat(props.getSearch().getTopK()).isEqualTo(10);
    }

    // --- Confidence ---

    @Test
    void confidence_defaults() {
        assertThat(props.getConfidence().getHighConfidenceScore()).isEqualTo(0.80);
        assertThat(props.getConfidence().getHighConfidenceThreshold()).isEqualTo(0.75);
        assertThat(props.getConfidence().getSupportedThreshold()).isEqualTo(0.70);
        assertThat(props.getConfidence().getConditionalThreshold()).isEqualTo(0.45);
    }

    @Test
    void confidence_settersOverrideDefaults() {
        props.getConfidence().setHighConfidenceScore(0.90);
        props.getConfidence().setHighConfidenceThreshold(0.85);
        props.getConfidence().setSupportedThreshold(0.60);
        props.getConfidence().setConditionalThreshold(0.40);

        assertThat(props.getConfidence().getHighConfidenceScore()).isEqualTo(0.90);
        assertThat(props.getConfidence().getHighConfidenceThreshold()).isEqualTo(0.85);
        assertThat(props.getConfidence().getSupportedThreshold()).isEqualTo(0.60);
        assertThat(props.getConfidence().getConditionalThreshold()).isEqualTo(0.40);
    }

    // --- Adaptive ---

    @Test
    void adaptive_defaults() {
        assertThat(props.getAdaptive().getMinConfidence()).isEqualTo(0.50);
        assertThat(props.getAdaptive().getMaxRetries()).isEqualTo(3);
    }

    @Test
    void adaptive_settersOverrideDefaults() {
        props.getAdaptive().setMinConfidence(0.70);
        props.getAdaptive().setMaxRetries(5);

        assertThat(props.getAdaptive().getMinConfidence()).isEqualTo(0.70);
        assertThat(props.getAdaptive().getMaxRetries()).isEqualTo(5);
    }

    // --- Multihop ---

    @Test
    void multihop_defaults() {
        assertThat(props.getMultihop().getTriggerTopScore()).isEqualTo(0.70);
        assertThat(props.getMultihop().getTriggerMinEvidence()).isEqualTo(3);
        assertThat(props.getMultihop().getMaxHops()).isEqualTo(2);
    }

    @Test
    void multihop_settersOverrideDefaults() {
        props.getMultihop().setTriggerTopScore(0.80);
        props.getMultihop().setTriggerMinEvidence(5);
        props.getMultihop().setMaxHops(4);

        assertThat(props.getMultihop().getTriggerTopScore()).isEqualTo(0.80);
        assertThat(props.getMultihop().getTriggerMinEvidence()).isEqualTo(5);
        assertThat(props.getMultihop().getMaxHops()).isEqualTo(4);
    }

    // --- Chunking ---

    @Test
    void chunking_defaults() {
        assertThat(props.getChunking().getParentSize()).isEqualTo(1500);
        assertThat(props.getChunking().getChildSize()).isEqualTo(400);
        assertThat(props.getChunking().getOverlap()).isEqualTo(300);
    }

    @Test
    void chunking_settersOverrideDefaults() {
        props.getChunking().setParentSize(2000);
        props.getChunking().setChildSize(500);
        props.getChunking().setOverlap(200);

        assertThat(props.getChunking().getParentSize()).isEqualTo(2000);
        assertThat(props.getChunking().getChildSize()).isEqualTo(500);
        assertThat(props.getChunking().getOverlap()).isEqualTo(200);
    }

    // --- Compose ---

    @Test
    void compose_defaultEvidenceTokenBudget_is3000() {
        assertThat(props.getCompose().getEvidenceTokenBudget()).isEqualTo(3000);
    }

    @Test
    void compose_setterOverridesDefault() {
        props.getCompose().setEvidenceTokenBudget(8000);
        assertThat(props.getCompose().getEvidenceTokenBudget()).isEqualTo(8000);
    }

    // --- CircuitBreaker ---

    @Test
    void circuitBreaker_defaults() {
        assertThat(props.getCircuitBreaker().getFailureThreshold()).isEqualTo(3);
        assertThat(props.getCircuitBreaker().getResetTimeoutSeconds()).isEqualTo(30);
    }

    @Test
    void circuitBreaker_settersOverrideDefaults() {
        props.getCircuitBreaker().setFailureThreshold(5);
        props.getCircuitBreaker().setResetTimeoutSeconds(60);

        assertThat(props.getCircuitBreaker().getFailureThreshold()).isEqualTo(5);
        assertThat(props.getCircuitBreaker().getResetTimeoutSeconds()).isEqualTo(60);
    }

    // --- Indexing ---

    @Test
    void indexing_defaults() {
        assertThat(props.getIndexing().getEnrichmentMinParents()).isEqualTo(5);
        assertThat(props.getIndexing().getEnrichmentMaxParents()).isEqualTo(30);
        assertThat(props.getIndexing().getEnrichmentSampleInterval()).isEqualTo(3);
    }

    @Test
    void indexing_settersOverrideDefaults() {
        props.getIndexing().setEnrichmentMinParents(10);
        props.getIndexing().setEnrichmentMaxParents(50);
        props.getIndexing().setEnrichmentSampleInterval(5);

        assertThat(props.getIndexing().getEnrichmentMinParents()).isEqualTo(10);
        assertThat(props.getIndexing().getEnrichmentMaxParents()).isEqualTo(50);
        assertThat(props.getIndexing().getEnrichmentSampleInterval()).isEqualTo(5);
    }

    // --- Top-level setters ---

    @Test
    void topLevelSetters_replaceInnerObjects() {
        var newBudget = new RagPipelineProperties.Budget();
        newBudget.setMaxTokensPerInquiry(99999);
        props.setBudget(newBudget);
        assertThat(props.getBudget().getMaxTokensPerInquiry()).isEqualTo(99999);

        var newCost = new RagPipelineProperties.Cost();
        newCost.setDailyBudgetUsd(200.0);
        props.setCost(newCost);
        assertThat(props.getCost().getDailyBudgetUsd()).isEqualTo(200.0);

        var newEvidence = new RagPipelineProperties.Evidence();
        newEvidence.setMaxItems(20);
        props.setEvidence(newEvidence);
        assertThat(props.getEvidence().getMaxItems()).isEqualTo(20);

        var newSearch = new RagPipelineProperties.Search();
        newSearch.setTopK(15);
        props.setSearch(newSearch);
        assertThat(props.getSearch().getTopK()).isEqualTo(15);

        var newConfidence = new RagPipelineProperties.Confidence();
        newConfidence.setHighConfidenceScore(0.95);
        props.setConfidence(newConfidence);
        assertThat(props.getConfidence().getHighConfidenceScore()).isEqualTo(0.95);

        var newAdaptive = new RagPipelineProperties.Adaptive();
        newAdaptive.setMaxRetries(10);
        props.setAdaptive(newAdaptive);
        assertThat(props.getAdaptive().getMaxRetries()).isEqualTo(10);

        var newMultihop = new RagPipelineProperties.Multihop();
        newMultihop.setMaxHops(5);
        props.setMultihop(newMultihop);
        assertThat(props.getMultihop().getMaxHops()).isEqualTo(5);

        var newChunking = new RagPipelineProperties.Chunking();
        newChunking.setParentSize(3000);
        props.setChunking(newChunking);
        assertThat(props.getChunking().getParentSize()).isEqualTo(3000);

        var newCompose = new RagPipelineProperties.Compose();
        newCompose.setEvidenceTokenBudget(12000);
        props.setCompose(newCompose);
        assertThat(props.getCompose().getEvidenceTokenBudget()).isEqualTo(12000);

        var newCb = new RagPipelineProperties.CircuitBreaker();
        newCb.setFailureThreshold(10);
        props.setCircuitBreaker(newCb);
        assertThat(props.getCircuitBreaker().getFailureThreshold()).isEqualTo(10);

        var newIndexing = new RagPipelineProperties.Indexing();
        newIndexing.setEnrichmentMinParents(15);
        props.setIndexing(newIndexing);
        assertThat(props.getIndexing().getEnrichmentMinParents()).isEqualTo(15);
    }
}
