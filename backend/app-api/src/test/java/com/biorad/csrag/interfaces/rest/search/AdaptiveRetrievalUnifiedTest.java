package com.biorad.csrag.interfaces.rest.search;

import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaEntity;
import com.biorad.csrag.interfaces.rest.chunk.ContextualChunkEnricher;
import com.biorad.csrag.interfaces.rest.vector.VectorizingService;
import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaRepository;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaRepository;
import com.biorad.csrag.interfaces.rest.vector.EmbeddingService;
import com.biorad.csrag.interfaces.rest.vector.VectorStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdaptiveRetrievalUnifiedTest {

    @Nested
    class UnifiedQueryGeneration {

        private HybridSearchService hybridSearchService;
        private RerankingService rerankingService;
        private RestClient restClient;
        private RestClient.ResponseSpec responseSpec;
        private OpenAiAdaptiveRetrievalAgent agent;

        @BeforeEach
        void setUp() {
            hybridSearchService = mock(HybridSearchService.class);
            rerankingService = mock(RerankingService.class);

            restClient = mock(RestClient.class);
            var requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
            var requestBodySpec = mock(RestClient.RequestBodySpec.class, RETURNS_SELF);
            responseSpec = mock(RestClient.ResponseSpec.class);

            when(restClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.retrieve()).thenReturn(responseSpec);

            agent = new OpenAiAdaptiveRetrievalAgent(
                    hybridSearchService, rerankingService, restClient, new ObjectMapper(), "gpt-4.1-mini", null);
        }

        @Test
        void unifiedPrompt_generates3Variants_fromSingleLlmCall() {
            String llmResponse = """
                    {"choices":[{"message":{"content":"[{\\"strategy\\":\\"expand\\",\\"query\\":\\"ddPCR QX200 droplet generation troubleshooting\\"},{\\"strategy\\":\\"broaden\\",\\"query\\":\\"digital PCR system issues\\"},{\\"strategy\\":\\"translate\\",\\"query\\":\\"QX200 드롭렛 생성 문제 해결\\"}]"}}]}
                    """;

            List<OpenAiAdaptiveRetrievalAgent.ReformulatedQuery> variants =
                    agent.parseVariants(llmResponse);

            assertThat(variants).hasSize(3);
            assertThat(variants.get(0).strategy()).isEqualTo("expand");
            assertThat(variants.get(0).query()).contains("ddPCR");
            assertThat(variants.get(1).strategy()).isEqualTo("broaden");
            assertThat(variants.get(2).strategy()).isEqualTo("translate");
        }

        @Test
        void parseVariants_invalidJson_returnsEmptyList() {
            String llmResponse = """
                    {"choices":[{"message":{"content":"this is not valid json"}}]}
                    """;

            List<OpenAiAdaptiveRetrievalAgent.ReformulatedQuery> variants =
                    agent.parseVariants(llmResponse);

            assertThat(variants).isEmpty();
        }

        @Test
        void parseVariants_emptyArray_returnsEmptyList() {
            String llmResponse = """
                    {"choices":[{"message":{"content":"[]"}}]}
                    """;

            List<OpenAiAdaptiveRetrievalAgent.ReformulatedQuery> variants =
                    agent.parseVariants(llmResponse);

            assertThat(variants).isEmpty();
        }

        @Test
        void parseVariants_blankQuery_skipped() {
            String llmResponse = """
                    {"choices":[{"message":{"content":"[{\\"strategy\\":\\"expand\\",\\"query\\":\\"valid query\\"},{\\"strategy\\":\\"broaden\\",\\"query\\":\\"\\"},{\\"strategy\\":\\"translate\\",\\"query\\":\\"another query\\"}]"}}]}
                    """;

            List<OpenAiAdaptiveRetrievalAgent.ReformulatedQuery> variants =
                    agent.parseVariants(llmResponse);

            assertThat(variants).hasSize(2);
            assertThat(variants.get(0).query()).isEqualTo("valid query");
            assertThat(variants.get(1).query()).isEqualTo("another query");
        }

        @Test
        void retrieve_unifiedCallSuccess_searchesAllVariants() {
            UUID inquiryId = UUID.randomUUID();
            UUID chunkId1 = UUID.randomUUID();
            UUID chunkId2 = UUID.randomUUID();
            UUID chunkId3 = UUID.randomUUID();

            // 1차 시도: 낮은 점수
            when(hybridSearchService.search(anyString(), anyInt(), any()))
                    .thenReturn(List.of(candidate(chunkId1, 0.3)))  // initial
                    .thenReturn(List.of(candidate(chunkId2, 0.7)))  // expand
                    .thenReturn(List.of(candidate(chunkId3, 0.6)))  // broaden
                    .thenReturn(List.of(candidate(chunkId1, 0.4))); // translate (dup chunkId1)
            when(rerankingService.rerank(anyString(), anyList(), anyInt()))
                    .thenReturn(List.of(rerankResult(chunkId1, 0.3)))
                    .thenReturn(List.of(rerankResult(chunkId2, 0.7)))
                    .thenReturn(List.of(rerankResult(chunkId3, 0.6)))
                    .thenReturn(List.of(rerankResult(chunkId1, 0.4)));

            // unified LLM 응답
            String unifiedResponse = """
                    {"choices":[{"message":{"content":"[{\\"strategy\\":\\"expand\\",\\"query\\":\\"expanded\\"},{\\"strategy\\":\\"broaden\\",\\"query\\":\\"broadened\\"},{\\"strategy\\":\\"translate\\",\\"query\\":\\"translated\\"}]"}}]}
                    """;
            when(responseSpec.body(String.class)).thenReturn(unifiedResponse);

            AdaptiveRetrievalAgent.AdaptiveResult result = agent.retrieve("test query", "", inquiryId);

            assertThat(result.status()).isEqualTo(AdaptiveRetrievalAgent.AdaptiveResult.ResultStatus.SUCCESS);
            // LLM은 1회만 호출 (unified call)
            verify(restClient, times(1)).post();
            // 검색은 4회 (원본 1 + 변형 3)
            verify(hybridSearchService, times(4)).search(anyString(), anyInt(), any());
        }

        @Test
        void retrieve_unifiedCallFails_fallsBackToSequential() {
            UUID inquiryId = UUID.randomUUID();

            // 1차 시도: 낮은 점수
            when(hybridSearchService.search(anyString(), anyInt(), any()))
                    .thenReturn(List.of(candidate(0.3)))
                    .thenReturn(List.of(candidate(0.8)));
            when(rerankingService.rerank(anyString(), anyList(), anyInt()))
                    .thenReturn(List.of(rerankResult(0.3)))
                    .thenReturn(List.of(rerankResult(0.8)));

            // unified call 실패 → fallback reformulation 응답
            when(responseSpec.body(String.class))
                    .thenThrow(new RuntimeException("Unified call failed"))
                    .thenReturn("""
                            {"choices":[{"message":{"content":"fallback query"}}]}
                            """);

            AdaptiveRetrievalAgent.AdaptiveResult result = agent.retrieve("test query", "", inquiryId);

            assertThat(result).isNotNull();
            assertThat(result.status()).isIn(
                    AdaptiveRetrievalAgent.AdaptiveResult.ResultStatus.SUCCESS,
                    AdaptiveRetrievalAgent.AdaptiveResult.ResultStatus.LOW_CONFIDENCE);
            // unified 1회 실패 + fallback에서 reformulation 호출
            verify(restClient, atLeast(2)).post();
        }

        private HybridSearchResult candidate(double score) {
            return candidate(UUID.randomUUID(), score);
        }

        private HybridSearchResult candidate(UUID chunkId, double score) {
            return new HybridSearchResult(chunkId, UUID.randomUUID(), "sample content",
                    score, 0.0, score, "INQUIRY", "VECTOR");
        }

        private RerankingService.RerankResult rerankResult(double score) {
            return rerankResult(UUID.randomUUID(), score);
        }

        private RerankingService.RerankResult rerankResult(UUID chunkId, double score) {
            return new RerankingService.RerankResult(chunkId, UUID.randomUUID(), "sample content",
                    score, score, "INQUIRY", "VECTOR");
        }
    }

    @Nested
    class DeduplicationByChunkId {

        private OpenAiAdaptiveRetrievalAgent agent;

        @BeforeEach
        void setUp() {
            agent = new OpenAiAdaptiveRetrievalAgent(
                    mock(HybridSearchService.class),
                    mock(RerankingService.class),
                    mock(RestClient.class),
                    new ObjectMapper(),
                    "gpt-4.1-mini",
                    null);
        }

        @Test
        void deduplicateByChunkId_keepsHighestScore() {
            UUID chunkId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();

            List<RerankingService.RerankResult> results = new ArrayList<>();
            results.add(new RerankingService.RerankResult(chunkId, docId, "content", 0.3, 0.3, "INQUIRY", "VECTOR"));
            results.add(new RerankingService.RerankResult(chunkId, docId, "content", 0.8, 0.8, "INQUIRY", "VECTOR"));
            results.add(new RerankingService.RerankResult(chunkId, docId, "content", 0.5, 0.5, "INQUIRY", "VECTOR"));

            List<RerankingService.RerankResult> deduped = agent.deduplicateByChunkId(results);

            assertThat(deduped).hasSize(1);
            assertThat(deduped.get(0).rerankScore()).isEqualTo(0.8);
        }

        @Test
        void deduplicateByChunkId_preservesDistinctChunks() {
            UUID chunkId1 = UUID.randomUUID();
            UUID chunkId2 = UUID.randomUUID();
            UUID docId = UUID.randomUUID();

            List<RerankingService.RerankResult> results = new ArrayList<>();
            results.add(new RerankingService.RerankResult(chunkId1, docId, "content1", 0.5, 0.5, "INQUIRY", "VECTOR"));
            results.add(new RerankingService.RerankResult(chunkId2, docId, "content2", 0.7, 0.7, "INQUIRY", "VECTOR"));

            List<RerankingService.RerankResult> deduped = agent.deduplicateByChunkId(results);

            assertThat(deduped).hasSize(2);
        }

        @Test
        void deduplicateByChunkId_emptyList_returnsEmpty() {
            List<RerankingService.RerankResult> deduped = agent.deduplicateByChunkId(new ArrayList<>());

            assertThat(deduped).isEmpty();
        }
    }

    @Nested
    class EnrichmentStrategySelection {

        private ContextualChunkEnricher mockEnricher;

        @BeforeEach
        void setUp() {
            mockEnricher = mock(ContextualChunkEnricher.class);
        }

        @Test
        void skipStrategy_whenParentCountBelowMin() {
            // 3 parents < 5 (min) => SKIP
            VectorizingService service = createService(mockEnricher);
            UUID docId = UUID.randomUUID();
            List<DocumentChunkJpaEntity> chunks = createParentChunks(docId, 3);

            service.applySelectiveEnrichment(docId, chunks, "doc text", "file.pdf");

            // enricher should NOT be called
            verify(mockEnricher, never()).enrichChunks(anyString(), anyList(), anyString());
            // all chunks should have enrichedContent = content
            for (DocumentChunkJpaEntity chunk : chunks) {
                assertThat(chunk.getEnrichedContent()).isEqualTo(chunk.getContent());
            }
        }

        @Test
        void fullStrategy_whenParentCountInRange() {
            // 10 parents: 5 <= 10 <= 30 => FULL
            VectorizingService service = createService(mockEnricher);
            UUID docId = UUID.randomUUID();
            List<DocumentChunkJpaEntity> chunks = createParentChunks(docId, 10);

            service.applySelectiveEnrichment(docId, chunks, "doc text", "file.pdf");

            // enricher SHOULD be called with all chunks
            verify(mockEnricher, times(1)).enrichChunks(eq("doc text"), eq(chunks), eq("file.pdf"));
        }

        @Test
        void sampleStrategy_whenParentCountAboveMax() {
            // 35 parents > 30 (max), interval=3 => sample every 3rd
            VectorizingService service = createService(mockEnricher);
            UUID docId = UUID.randomUUID();
            List<DocumentChunkJpaEntity> chunks = createParentChunks(docId, 35);

            service.applySelectiveEnrichment(docId, chunks, "doc text", "file.pdf");

            // enricher should be called with sampled subset
            verify(mockEnricher, times(1)).enrichChunks(eq("doc text"), anyList(), eq("file.pdf"));

            // verify sampled count: 35 parents / 3 interval = 12 sampled parents
            var captor = org.mockito.ArgumentCaptor.forClass(List.class);
            verify(mockEnricher).enrichChunks(eq("doc text"), captor.capture(), eq("file.pdf"));
            @SuppressWarnings("unchecked")
            List<DocumentChunkJpaEntity> enrichedChunks = captor.getValue();
            assertThat(enrichedChunks).hasSize(12); // indices 0,3,6,9,12,15,18,21,24,27,30,33

            // skipped chunks should have enrichedContent = content
            long skippedCount = chunks.stream()
                    .filter(c -> c.getEnrichedContent() != null && c.getEnrichedContent().equals(c.getContent()))
                    .count();
            assertThat(skippedCount).isEqualTo(23); // 35 - 12 sampled
        }

        @Test
        void skipStrategy_emptyChunks_noException() {
            VectorizingService service = createService(mockEnricher);
            UUID docId = UUID.randomUUID();

            service.applySelectiveEnrichment(docId, List.of(), "doc text", "file.pdf");
            service.applySelectiveEnrichment(docId, null, "doc text", "file.pdf");

            verify(mockEnricher, never()).enrichChunks(anyString(), anyList(), anyString());
        }

        @Test
        void boundaryValue_exactlyMinParents_usesFull() {
            // exactly 5 parents (= enrichmentMinParents) => FULL
            VectorizingService service = createService(mockEnricher);
            UUID docId = UUID.randomUUID();
            List<DocumentChunkJpaEntity> chunks = createParentChunks(docId, 5);

            service.applySelectiveEnrichment(docId, chunks, "doc text", "file.pdf");

            verify(mockEnricher, times(1)).enrichChunks(eq("doc text"), eq(chunks), eq("file.pdf"));
        }

        @Test
        void boundaryValue_exactlyMaxParents_usesFull() {
            // exactly 30 parents (= enrichmentMaxParents) => FULL
            VectorizingService service = createService(mockEnricher);
            UUID docId = UUID.randomUUID();
            List<DocumentChunkJpaEntity> chunks = createParentChunks(docId, 30);

            service.applySelectiveEnrichment(docId, chunks, "doc text", "file.pdf");

            verify(mockEnricher, times(1)).enrichChunks(eq("doc text"), eq(chunks), eq("file.pdf"));
        }

        @Test
        void sampleStrategy_parentWithChildren_onlySampledChildrenEnriched() {
            VectorizingService service = createService(mockEnricher);
            UUID docId = UUID.randomUUID();

            // 35 parents with 1 child each
            List<DocumentChunkJpaEntity> allChunks = new ArrayList<>();
            for (int i = 0; i < 35; i++) {
                DocumentChunkJpaEntity parent = createChunk(docId, i, "PARENT");
                allChunks.add(parent);

                DocumentChunkJpaEntity child = createChunk(docId, i * 100, "CHILD");
                child.setParentChunkId(parent.getId());
                allChunks.add(child);
            }

            service.applySelectiveEnrichment(docId, allChunks, "doc text", "file.pdf");

            // enricher called once with sampled parents + their children
            var captor = org.mockito.ArgumentCaptor.forClass(List.class);
            verify(mockEnricher).enrichChunks(eq("doc text"), captor.capture(), eq("file.pdf"));
            @SuppressWarnings("unchecked")
            List<DocumentChunkJpaEntity> enrichedChunks = captor.getValue();
            // 12 sampled parents + 12 associated children = 24
            assertThat(enrichedChunks).hasSize(24);

            // Skipped children should have enrichedContent = content
            long skippedChildren = allChunks.stream()
                    .filter(c -> "CHILD".equals(c.getChunkLevel()))
                    .filter(c -> c.getEnrichedContent() != null && c.getEnrichedContent().equals(c.getContent()))
                    .count();
            assertThat(skippedChildren).isEqualTo(23); // 35 - 12 = 23 skipped parents' children
        }

        private VectorizingService createService(ContextualChunkEnricher enricher) {
            return new VectorizingService(
                    mock(DocumentChunkJpaRepository.class),
                    mock(KnowledgeDocumentJpaRepository.class),
                    mock(EmbeddingService.class),
                    mock(VectorStore.class),
                    enricher,
                    null
            );
        }

        private List<DocumentChunkJpaEntity> createParentChunks(UUID docId, int count) {
            List<DocumentChunkJpaEntity> chunks = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                chunks.add(createChunk(docId, i, "PARENT"));
            }
            return chunks;
        }

        private DocumentChunkJpaEntity createChunk(UUID docId, int chunkIndex, String level) {
            DocumentChunkJpaEntity chunk = new DocumentChunkJpaEntity(
                    UUID.randomUUID(), docId, chunkIndex,
                    chunkIndex * 100, (chunkIndex + 1) * 100,
                    "chunk content " + chunkIndex,
                    "INQUIRY", docId, Instant.now()
            );
            chunk.setChunkLevel(level);
            return chunk;
        }
    }
}
