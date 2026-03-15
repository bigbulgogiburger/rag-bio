package com.biorad.csrag.infrastructure.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 파이프라인 하이퍼파라미터를 외부화한 설정 클래스.
 *
 * <p>{@code application.yml}의 {@code rag.*} 프로퍼티와 바인딩되며,
 * 각 파이프라인 단계(검색, 검증, 작성, 인덱싱 등)에서 {@code @Value} 대신
 * 이 빈을 주입받아 사용한다.
 */
@Component
@ConfigurationProperties(prefix = "rag")
public class RagPipelineProperties {

    private Budget budget = new Budget();
    private Cost cost = new Cost();
    private Evidence evidence = new Evidence();
    private Search search = new Search();
    private Confidence confidence = new Confidence();
    private Adaptive adaptive = new Adaptive();
    private Multihop multihop = new Multihop();
    private Chunking chunking = new Chunking();
    private Compose compose = new Compose();
    private CircuitBreaker circuitBreaker = new CircuitBreaker();
    private Indexing indexing = new Indexing();

    // --- top-level getters / setters ---

    public Budget getBudget() { return budget; }
    public void setBudget(Budget budget) { this.budget = budget; }

    public Cost getCost() { return cost; }
    public void setCost(Cost cost) { this.cost = cost; }

    public Evidence getEvidence() { return evidence; }
    public void setEvidence(Evidence evidence) { this.evidence = evidence; }

    public Search getSearch() { return search; }
    public void setSearch(Search search) { this.search = search; }

    public Confidence getConfidence() { return confidence; }
    public void setConfidence(Confidence confidence) { this.confidence = confidence; }

    public Adaptive getAdaptive() { return adaptive; }
    public void setAdaptive(Adaptive adaptive) { this.adaptive = adaptive; }

    public Multihop getMultihop() { return multihop; }
    public void setMultihop(Multihop multihop) { this.multihop = multihop; }

    public Chunking getChunking() { return chunking; }
    public void setChunking(Chunking chunking) { this.chunking = chunking; }

    public Compose getCompose() { return compose; }
    public void setCompose(Compose compose) { this.compose = compose; }

    public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }
    public void setCircuitBreaker(CircuitBreaker circuitBreaker) { this.circuitBreaker = circuitBreaker; }

    public Indexing getIndexing() { return indexing; }
    public void setIndexing(Indexing indexing) { this.indexing = indexing; }

    // === Inner configuration classes ===

    /** 파이프라인 요청당 토큰 예산 설정. */
    public static class Budget {
        private int maxTokensPerInquiry = 25000;

        public int getMaxTokensPerInquiry() { return maxTokensPerInquiry; }
        public void setMaxTokensPerInquiry(int maxTokensPerInquiry) { this.maxTokensPerInquiry = maxTokensPerInquiry; }
    }

    /** 일일 비용 한도 및 알림 임계값 설정. */
    public static class Cost {
        private double dailyBudgetUsd = 50.0;
        private int alertThresholdPercent = 80;
        private double perInquiryMaxUsd = 0.10;

        public double getDailyBudgetUsd() { return dailyBudgetUsd; }
        public void setDailyBudgetUsd(double dailyBudgetUsd) { this.dailyBudgetUsd = dailyBudgetUsd; }

        public int getAlertThresholdPercent() { return alertThresholdPercent; }
        public void setAlertThresholdPercent(int alertThresholdPercent) { this.alertThresholdPercent = alertThresholdPercent; }

        public double getPerInquiryMaxUsd() { return perInquiryMaxUsd; }
        public void setPerInquiryMaxUsd(double perInquiryMaxUsd) { this.perInquiryMaxUsd = perInquiryMaxUsd; }
    }

    /** 증거 수집 제한 설정. */
    public static class Evidence {
        private int maxItems = 8;
        private double minScore = 0.30;
        private int maxPerDocument = 3;
        private boolean ensureSourceDiversity = true;

        public int getMaxItems() { return maxItems; }
        public void setMaxItems(int maxItems) { this.maxItems = maxItems; }

        public double getMinScore() { return minScore; }
        public void setMinScore(double minScore) { this.minScore = minScore; }

        public int getMaxPerDocument() { return maxPerDocument; }
        public void setMaxPerDocument(int maxPerDocument) { this.maxPerDocument = maxPerDocument; }

        public boolean isEnsureSourceDiversity() { return ensureSourceDiversity; }
        public void setEnsureSourceDiversity(boolean ensureSourceDiversity) { this.ensureSourceDiversity = ensureSourceDiversity; }
    }

    /** 하이브리드 검색(벡터 + 키워드) 파라미터. */
    public static class Search {
        private int rrfK = 60;
        private double vectorWeight = 1.0;
        private double keywordWeight = 1.0;
        private double minVectorScore = 0.25;
        private int topK = 5;

        public int getRrfK() { return rrfK; }
        public void setRrfK(int rrfK) { this.rrfK = rrfK; }

        public double getVectorWeight() { return vectorWeight; }
        public void setVectorWeight(double vectorWeight) { this.vectorWeight = vectorWeight; }

        public double getKeywordWeight() { return keywordWeight; }
        public void setKeywordWeight(double keywordWeight) { this.keywordWeight = keywordWeight; }

        public double getMinVectorScore() { return minVectorScore; }
        public void setMinVectorScore(double minVectorScore) { this.minVectorScore = minVectorScore; }

        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
    }

    /** 검증 단계 신뢰도 임계값. */
    public static class Confidence {
        private double highConfidenceScore = 0.80;
        private double highConfidenceThreshold = 0.75;
        private double supportedThreshold = 0.70;
        private double conditionalThreshold = 0.45;

        public double getHighConfidenceScore() { return highConfidenceScore; }
        public void setHighConfidenceScore(double highConfidenceScore) { this.highConfidenceScore = highConfidenceScore; }

        public double getHighConfidenceThreshold() { return highConfidenceThreshold; }
        public void setHighConfidenceThreshold(double highConfidenceThreshold) { this.highConfidenceThreshold = highConfidenceThreshold; }

        public double getSupportedThreshold() { return supportedThreshold; }
        public void setSupportedThreshold(double supportedThreshold) { this.supportedThreshold = supportedThreshold; }

        public double getConditionalThreshold() { return conditionalThreshold; }
        public void setConditionalThreshold(double conditionalThreshold) { this.conditionalThreshold = conditionalThreshold; }
    }

    /** Adaptive retrieval (재검색) 설정. */
    public static class Adaptive {
        private double minConfidence = 0.50;
        private int maxRetries = 3;

        public double getMinConfidence() { return minConfidence; }
        public void setMinConfidence(double minConfidence) { this.minConfidence = minConfidence; }

        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }

    /** Multi-hop 검색 트리거 조건. */
    public static class Multihop {
        private double triggerTopScore = 0.70;
        private int triggerMinEvidence = 3;
        private int maxHops = 2;

        public double getTriggerTopScore() { return triggerTopScore; }
        public void setTriggerTopScore(double triggerTopScore) { this.triggerTopScore = triggerTopScore; }

        public int getTriggerMinEvidence() { return triggerMinEvidence; }
        public void setTriggerMinEvidence(int triggerMinEvidence) { this.triggerMinEvidence = triggerMinEvidence; }

        public int getMaxHops() { return maxHops; }
        public void setMaxHops(int maxHops) { this.maxHops = maxHops; }
    }

    /** Parent-Child 이중 청킹 크기 설정. */
    public static class Chunking {
        private int parentSize = 1500;
        private int childSize = 400;
        private int overlap = 300;

        public int getParentSize() { return parentSize; }
        public void setParentSize(int parentSize) { this.parentSize = parentSize; }

        public int getChildSize() { return childSize; }
        public void setChildSize(int childSize) { this.childSize = childSize; }

        public int getOverlap() { return overlap; }
        public void setOverlap(int overlap) { this.overlap = overlap; }
    }

    /** 답변 작성 단계의 증거 토큰 예산. */
    public static class Compose {
        private int evidenceTokenBudget = 3000;

        public int getEvidenceTokenBudget() { return evidenceTokenBudget; }
        public void setEvidenceTokenBudget(int evidenceTokenBudget) { this.evidenceTokenBudget = evidenceTokenBudget; }
    }

    /** OpenAI 호출 서킷브레이커 설정. */
    public static class CircuitBreaker {
        private int failureThreshold = 3;
        private int resetTimeoutSeconds = 30;

        public int getFailureThreshold() { return failureThreshold; }
        public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }

        public int getResetTimeoutSeconds() { return resetTimeoutSeconds; }
        public void setResetTimeoutSeconds(int resetTimeoutSeconds) { this.resetTimeoutSeconds = resetTimeoutSeconds; }
    }

    /** Contextual enrichment 인덱싱 설정. */
    public static class Indexing {
        private int enrichmentMinParents = 5;
        private int enrichmentMaxParents = 30;
        private int enrichmentSampleInterval = 3;

        public int getEnrichmentMinParents() { return enrichmentMinParents; }
        public void setEnrichmentMinParents(int enrichmentMinParents) { this.enrichmentMinParents = enrichmentMinParents; }

        public int getEnrichmentMaxParents() { return enrichmentMaxParents; }
        public void setEnrichmentMaxParents(int enrichmentMaxParents) { this.enrichmentMaxParents = enrichmentMaxParents; }

        public int getEnrichmentSampleInterval() { return enrichmentSampleInterval; }
        public void setEnrichmentSampleInterval(int enrichmentSampleInterval) { this.enrichmentSampleInterval = enrichmentSampleInterval; }
    }
}
