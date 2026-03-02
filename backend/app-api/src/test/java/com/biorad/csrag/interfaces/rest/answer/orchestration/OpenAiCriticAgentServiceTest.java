package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OpenAiCriticAgentServiceTest {

    private RestClient restClient;
    private RestClient.ResponseSpec responseSpec;
    private OpenAiCriticAgentService service;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        var requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        var requestBodySpec = mock(RestClient.RequestBodySpec.class, RETURNS_SELF);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);

        service = new OpenAiCriticAgentService(restClient, "gpt-5.2", new ObjectMapper(), null /* ragMetricsService */);
    }

    @Test
    void critique_highFaithfulness_returnsNonRevision() {
        String apiResponse = """
                {"choices":[{"message":{"content":"{\\"faithfulness_score\\": 0.95, \\"claims\\": [], \\"corrections\\": [], \\"needs_revision\\": false}"}}]}
                """;
        when(responseSpec.body(String.class)).thenReturn(apiResponse);

        EvidenceItem evidence = new EvidenceItem("c1", "d1", 0.9, "AAV2 is compatible",
                "INQUIRY", "manual.pdf", 5, 5);

        CriticAgentService.CriticResult result = service.critique(
                "AAV2 is compatible with ddPCR.", "AAV2 compatibility", List.of(evidence));

        assertThat(result.faithfulnessScore()).isEqualTo(0.95);
        assertThat(result.needsRevision()).isFalse();
    }

    @Test
    void critique_lowFaithfulness_returnsRevisionRequired() {
        String apiResponse = """
                {"choices":[{"message":{"content":"{\\"faithfulness_score\\": 0.45, \\"claims\\": [], \\"corrections\\": [\\"p.5 참조 필요\\"], \\"needs_revision\\": false}"}}]}
                """;
        when(responseSpec.body(String.class)).thenReturn(apiResponse);

        CriticAgentService.CriticResult result = service.critique(
                "잘못된 답변", "질문", List.of());

        // faithfulness < 0.70 이면 needsRevision=true 강제
        assertThat(result.faithfulnessScore()).isEqualTo(0.45);
        assertThat(result.needsRevision()).isTrue();
        assertThat(result.corrections()).contains("p.5 참조 필요");
    }

    @Test
    void critique_withClaimsArray_parsesCorrectly() {
        String apiResponse = """
                {"choices":[{"message":{"content":"{\\"faithfulness_score\\": 0.8, \\"claims\\": [{\\"claim\\": \\"AAV2 compatible\\", \\"faithfulness\\": \\"TRUE\\", \\"citation_accuracy\\": \\"CORRECT\\", \\"factual_match\\": \\"MATCH\\"}], \\"corrections\\": [], \\"needs_revision\\": false}"}}]}
                """;
        when(responseSpec.body(String.class)).thenReturn(apiResponse);

        CriticAgentService.CriticResult result = service.critique("draft", "question", List.of());

        assertThat(result.claims()).hasSize(1);
        assertThat(result.claims().get(0).faithfulness()).isEqualTo("TRUE");
        assertThat(result.claims().get(0).citationAccuracy()).isEqualTo("CORRECT");
    }

    @Test
    void critique_codeFencedResponse_parsesCorrectly() {
        String apiResponse = """
                {"choices":[{"message":{"content":"```json\\n{\\"faithfulness_score\\": 0.9, \\"claims\\": [], \\"corrections\\": [], \\"needs_revision\\": false}\\n```"}}]}
                """;
        when(responseSpec.body(String.class)).thenReturn(apiResponse);

        CriticAgentService.CriticResult result = service.critique("draft", "question", List.of());

        assertThat(result.faithfulnessScore()).isEqualTo(0.9);
        assertThat(result.needsRevision()).isFalse();
    }

    @Test
    void critique_apiError_returnsDefaultPassing() {
        when(responseSpec.body(String.class)).thenThrow(new RuntimeException("API unavailable"));

        CriticAgentService.CriticResult result = service.critique("draft", "question", List.of());

        assertThat(result.needsRevision()).isFalse();
        assertThat(result.faithfulnessScore()).isEqualTo(1.0);
    }

    @Test
    void critique_malformedJson_returnsDefaultPassing() {
        String apiResponse = """
                {"choices":[{"message":{"content":"not valid json at all"}}]}
                """;
        when(responseSpec.body(String.class)).thenReturn(apiResponse);

        CriticAgentService.CriticResult result = service.critique("draft", "question", List.of());

        assertThat(result.needsRevision()).isFalse();
        assertThat(result.faithfulnessScore()).isEqualTo(1.0);
    }

    @Test
    void critique_withFileEvidences_formatsCorrectly() {
        String apiResponse = """
                {"choices":[{"message":{"content":"{\\"faithfulness_score\\": 0.85, \\"claims\\": [], \\"corrections\\": [], \\"needs_revision\\": false}"}}]}
                """;
        when(responseSpec.body(String.class)).thenReturn(apiResponse);

        List<EvidenceItem> evidences = List.of(
                new EvidenceItem("c1", "d1", 0.9, "evidence text", "INQUIRY", "protocol.pdf", 3, 4),
                new EvidenceItem("c2", "d2", 0.8, "more evidence", "KNOWLEDGE_BASE", null, null, null)
        );

        CriticAgentService.CriticResult result = service.critique("draft", "question", evidences);

        assertThat(result.faithfulnessScore()).isEqualTo(0.85);
        verify(restClient).post(); // LLM 호출 발생
    }
}
