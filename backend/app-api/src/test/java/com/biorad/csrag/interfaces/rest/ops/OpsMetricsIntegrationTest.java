package com.biorad.csrag.interfaces.rest.ops;

import com.biorad.csrag.app.CsRagApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CsRagApplication.class)
@AutoConfigureMockMvc
class OpsMetricsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void opsMetrics_returns_send_and_fallback_metrics() throws Exception {
        String inquiryId = createInquiry();
        String answerId = createDraft(inquiryId);

        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/review", inquiryId, answerId)
                        .header("X-User-Id", "reviewer-1")
                        .header("X-User-Roles", "REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"reviewed"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/approve", inquiryId, answerId)
                        .header("X-User-Id", "approver-1")
                        .header("X-User-Roles", "APPROVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"approved"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/send", inquiryId, answerId)
                        .header("X-User-Id", "sender-1")
                        .header("X-User-Roles", "SENDER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"email","sendRequestId":"ops-1"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/ops/metrics").param("topFailures", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentCount").isNumber())
                .andExpect(jsonPath("$.sentCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.approvedOrSentCount").isNumber())
                .andExpect(jsonPath("$.approvedOrSentCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.sendSuccessRate").isNumber());
    }

    private String createInquiry() throws Exception {
        String body = mockMvc.perform(post("/api/v1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "ops metric test",
                                  "customerChannel": "email"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(body);
        return node.path("inquiryId").asText();
    }

    private String createDraft(String inquiryId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/draft", inquiryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "ops metric draft",
                                  "tone": "professional",
                                  "channel": "email"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).path("answerId").asText();
    }
}
