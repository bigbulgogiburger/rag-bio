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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
                .andExpect(jsonPath("$.sendSuccessRate").isNumber())
                .andExpect(jsonPath("$.duplicateBlockedCount").isNumber())
                .andExpect(jsonPath("$.duplicateBlockedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.duplicateBlockRate").isNumber());
    }

    @Test
    void timeline_default30d_returnsData() throws Exception {
        mockMvc.perform(get("/api/v1/ops/metrics/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("30d"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void timeline_7d() throws Exception {
        mockMvc.perform(get("/api/v1/ops/metrics/timeline").param("period", "7d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("7d"));
    }

    @Test
    void timeline_today() throws Exception {
        mockMvc.perform(get("/api/v1/ops/metrics/timeline").param("period", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("today"));
    }

    @Test
    void timeline_90d() throws Exception {
        mockMvc.perform(get("/api/v1/ops/metrics/timeline").param("period", "90d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("90d"));
    }

    @Test
    void timeline_customDateRange() throws Exception {
        mockMvc.perform(get("/api/v1/ops/metrics/timeline")
                        .param("from", "2025-01-01T00:00:00Z")
                        .param("to", "2025-12-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void timeline_invalidDateFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/ops/metrics/timeline")
                        .param("from", "not-a-date")
                        .param("to", "also-not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processingTime_default() throws Exception {
        mockMvc.perform(get("/api/v1/ops/metrics/processing-time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("30d"))
                .andExpect(jsonPath("$.avgProcessingTimeHours").isNumber())
                .andExpect(jsonPath("$.medianProcessingTimeHours").isNumber())
                .andExpect(jsonPath("$.totalCompleted").isNumber());
    }

    @Test
    void kbUsage_default() throws Exception {
        mockMvc.perform(get("/api/v1/ops/metrics/kb-usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("30d"))
                .andExpect(jsonPath("$.kbUsageRate").isNumber())
                .andExpect(jsonPath("$.topDocuments").isArray());
    }

    @Test
    void exportCsv_returnsCsvContent() throws Exception {
        mockMvc.perform(get("/api/v1/ops/metrics/export/csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().exists("Content-Disposition"));
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
