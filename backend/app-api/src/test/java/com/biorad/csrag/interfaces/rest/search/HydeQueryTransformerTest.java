package com.biorad.csrag.interfaces.rest.search;

import com.biorad.csrag.interfaces.rest.vector.EmbeddingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class HydeQueryTransformerTest {

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = mock(EmbeddingService.class);
        when(embeddingService.embedQuery(anyString())).thenReturn(List.of(0.1, 0.2, 0.3));
        when(embeddingService.embedDocument(anyString())).thenReturn(List.of(0.4, 0.5, 0.6));
    }

    @Nested
    class MockHydeTest {

        private MockHydeQueryTransformer mockTransformer;

        @BeforeEach
        void setUp() {
            mockTransformer = new MockHydeQueryTransformer(embeddingService);
        }

        @Test
        void transformAndEmbed_usesEmbedQuery() {
            List<Double> result = mockTransformer.transformAndEmbed("CFX96 캘리브레이션 방법", "");

            assertThat(result).containsExactly(0.1, 0.2, 0.3);
            verify(embeddingService).embedQuery("CFX96 캘리브레이션 방법");
            verify(embeddingService, never()).embedDocument(anyString());
        }

        @Test
        void isEnabled_returnsFalse() {
            assertThat(mockTransformer.isEnabled()).isFalse();
        }
    }

    @Nested
    class OpenAiHydeTest {

        private OpenAiHydeQueryTransformer openAiTransformer;
        private RestClient restClient;
        private RestClient.RequestBodyUriSpec requestBodyUriSpec;
        private RestClient.RequestBodySpec requestBodySpec;
        private RestClient.ResponseSpec responseSpec;

        @BeforeEach
        @SuppressWarnings("unchecked")
        void setUp() {
            restClient = mock(RestClient.class);
            requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
            requestBodySpec = mock(RestClient.RequestBodySpec.class, withSettings().defaultAnswer(RETURNS_SELF));
            responseSpec = mock(RestClient.ResponseSpec.class);

            when(restClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.retrieve()).thenReturn(responseSpec);

            openAiTransformer = new OpenAiHydeQueryTransformer(
                    restClient, new ObjectMapper(), embeddingService, "gpt-4o-mini");
        }

        @Test
        void transformAndEmbed_usesEmbedDocument() {
            String llmResponse = """
                    {"choices":[{"message":{"content":"CFX96 캘리브레이션은 SYBR Green 필터를 사용하여 수행합니다."}}]}""";
            when(responseSpec.body(String.class)).thenReturn(llmResponse);

            List<Double> result = openAiTransformer.transformAndEmbed("CFX96 캘리브레이션 방법", "");

            assertThat(result).containsExactly(0.4, 0.5, 0.6);
            verify(embeddingService).embedDocument(anyString());
            verify(embeddingService, never()).embedQuery(anyString());
        }

        @Test
        void transformAndEmbed_withProductContext_callsEmbedDocument() {
            String llmResponse = """
                    {"choices":[{"message":{"content":"naica 시스템의 calibration 절차는 다음과 같습니다."}}]}""";
            when(responseSpec.body(String.class)).thenReturn(llmResponse);

            List<Double> result = openAiTransformer.transformAndEmbed("캘리브레이션 방법", "naica");

            assertThat(result).containsExactly(0.4, 0.5, 0.6);
            verify(embeddingService).embedDocument(anyString());
        }

        @Test
        void transformAndEmbed_llmFailure_fallsBackToQueryEmbedding() {
            when(responseSpec.body(String.class)).thenThrow(new RuntimeException("LLM timeout"));

            List<Double> result = openAiTransformer.transformAndEmbed("테스트 질문", "");

            assertThat(result).containsExactly(0.1, 0.2, 0.3);
            verify(embeddingService).embedQuery("테스트 질문");
            verify(embeddingService, never()).embedDocument(anyString());
        }

        @Test
        void transformAndEmbed_emptyLlmResponse_fallsBackToQueryEmbedding() {
            String llmResponse = """
                    {"choices":[{"message":{"content":""}}]}""";
            when(responseSpec.body(String.class)).thenReturn(llmResponse);

            List<Double> result = openAiTransformer.transformAndEmbed("테스트 질문", "");

            assertThat(result).containsExactly(0.1, 0.2, 0.3);
            verify(embeddingService).embedQuery("테스트 질문");
        }

        @Test
        void transformAndEmbed_nullLlmResponse_fallsBackToQueryEmbedding() {
            when(responseSpec.body(String.class)).thenReturn(null);

            List<Double> result = openAiTransformer.transformAndEmbed("테스트 질문", "");

            assertThat(result).containsExactly(0.1, 0.2, 0.3);
            verify(embeddingService).embedQuery("테스트 질문");
        }

        @Test
        void isEnabled_returnsTrue() {
            assertThat(openAiTransformer.isEnabled()).isTrue();
        }
    }
}
