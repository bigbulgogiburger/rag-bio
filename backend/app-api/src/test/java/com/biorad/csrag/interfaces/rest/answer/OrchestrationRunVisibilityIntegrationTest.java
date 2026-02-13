package com.biorad.csrag.interfaces.rest.answer;

import com.biorad.csrag.app.CsRagApplication;
import com.biorad.csrag.interfaces.rest.answer.orchestration.RetrieveStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CsRagApplication.class)
@AutoConfigureMockMvc
class OrchestrationRunVisibilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RetrieveStep retrieveStep;

    @Test
    void orchestrationRuns_endpointShowsFailedStep_whenRetrieveFails() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "orchestration-run visibility test",
                                  "customerChannel": "email"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String inquiryId = objectMapper.readTree(createResponse).path("inquiryId").asText();

        when(retrieveStep.execute(any(), any(), anyInt()))
                .thenThrow(new RuntimeException("forced-retrieve-failure"));

        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/draft", inquiryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "test fallback",
                                  "tone": "professional",
                                  "channel": "email"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskFlags[0]").value("ORCHESTRATION_FALLBACK"));

        mockMvc.perform(get("/api/v1/inquiries/{inquiryId}/orchestration-runs", inquiryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].step").value("RETRIEVE"))
                .andExpect(jsonPath("$[0].status").value("FAILED"));
    }
}
