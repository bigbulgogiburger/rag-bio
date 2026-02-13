package com.biorad.csrag.interfaces.rest.answer;

import com.biorad.csrag.app.CsRagApplication;
import com.biorad.csrag.interfaces.rest.answer.orchestration.AnswerOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CsRagApplication.class)
@AutoConfigureMockMvc
class AnswerFallbackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AnswerOrchestrationService orchestrationService;

    @Test
    void draftAnswer_returnsFallbackResponse_whenOrchestrationFails() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Fallback integration test question",
                                  "customerChannel": "email"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String inquiryId = objectMapper.readTree(createResponse).path("inquiryId").asText();

        when(orchestrationService.run(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("forced-orchestration-failure"));

        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/draft", inquiryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Could you validate this protocol?",
                                  "tone": "professional",
                                  "channel": "email"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("CONDITIONAL"))
                .andExpect(jsonPath("$.riskFlags[0]").value("ORCHESTRATION_FALLBACK"))
                .andExpect(jsonPath("$.formatWarnings[0]").value("FALLBACK_DRAFT_USED"))
                .andExpect(jsonPath("$.citations").isArray())
                .andExpect(jsonPath("$.citations").isEmpty());
    }
}
