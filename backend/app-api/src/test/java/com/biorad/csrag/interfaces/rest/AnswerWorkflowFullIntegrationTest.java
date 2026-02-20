package com.biorad.csrag.interfaces.rest;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CsRagApplication.class)
@AutoConfigureMockMvc
class AnswerWorkflowFullIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void analysis_invalidInquiryId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/inquiries/not-uuid/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"test","topK":5}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analysis_inquiryNotFound_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/inquiries/00000000-0000-0000-0000-000000000099/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"test","topK":5}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void analysis_success() throws Exception {
        String inquiryId = createInquiry();

        mockMvc.perform(post("/api/v1/inquiries/{id}/analysis", inquiryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"What is qPCR?","topK":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").isString())
                .andExpect(jsonPath("$.confidence").isNumber());
    }

    @Test
    void draft_latest_history_workflow() throws Exception {
        String inquiryId = createInquiry();

        // Generate draft
        String draftResponse = mockMvc.perform(post("/api/v1/inquiries/{id}/answers/draft", inquiryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"What is qPCR?","tone":"professional","channel":"email"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();

        String answerId = objectMapper.readTree(draftResponse).path("answerId").asText();

        // Get latest
        mockMvc.perform(get("/api/v1/inquiries/{id}/answers/latest", inquiryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerId").value(answerId));

        // Get history
        mockMvc.perform(get("/api/v1/inquiries/{id}/answers/history", inquiryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].answerId").value(answerId));
    }

    @Test
    void editDraft_success() throws Exception {
        String inquiryId = createInquiry();
        String answerId = createDraft(inquiryId);

        mockMvc.perform(patch("/api/v1/inquiries/{inquiryId}/answers/{answerId}/edit-draft", inquiryId, answerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"draft":"Updated draft text here"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft").value("Updated draft text here"));
    }

    @Test
    void editDraft_emptyDraft_returns400() throws Exception {
        String inquiryId = createInquiry();
        String answerId = createDraft(inquiryId);

        mockMvc.perform(patch("/api/v1/inquiries/{inquiryId}/answers/{answerId}/edit-draft", inquiryId, answerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"draft":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fullWorkflow_draft_review_approve_send() throws Exception {
        String inquiryId = createInquiry();
        String answerId = createDraft(inquiryId);

        // Review
        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/review", inquiryId, answerId)
                        .header("X-User-Id", "reviewer-1")
                        .header("X-User-Roles", "REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"looks good"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWED"));

        // Approve
        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/approve", inquiryId, answerId)
                        .header("X-User-Id", "approver-1")
                        .header("X-User-Roles", "APPROVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"approved"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Send
        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/send", inquiryId, answerId)
                        .header("X-User-Id", "sender-1")
                        .header("X-User-Roles", "SENDER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"email","sendRequestId":"full-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    void aiReview_success() throws Exception {
        String inquiryId = createInquiry();
        String answerId = createDraft(inquiryId);

        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/ai-review", inquiryId, answerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").isString())
                .andExpect(jsonPath("$.score").isNumber());
    }

    @Test
    void autoWorkflow_success() throws Exception {
        String inquiryId = createInquiry();
        String answerId = createDraft(inquiryId);

        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/auto-workflow", inquiryId, answerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review").isNotEmpty())
                .andExpect(jsonPath("$.approval").isNotEmpty())
                .andExpect(jsonPath("$.summary").isString());
    }

    @Test
    void auditLogs_default() throws Exception {
        String inquiryId = createInquiry();
        createDraft(inquiryId);

        mockMvc.perform(get("/api/v1/inquiries/{inquiryId}/answers/audit-logs", inquiryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").isNumber());
    }

    @Test
    void auditLogs_withFilters() throws Exception {
        String inquiryId = createInquiry();
        createDraft(inquiryId);

        mockMvc.perform(get("/api/v1/inquiries/{inquiryId}/answers/audit-logs", inquiryId)
                        .param("status", "DRAFT")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sort", "version,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void auditLogs_invalidDateRange_returns400() throws Exception {
        String inquiryId = createInquiry();

        mockMvc.perform(get("/api/v1/inquiries/{inquiryId}/answers/audit-logs", inquiryId)
                        .param("from", "2026-01-02T00:00:00Z")
                        .param("to", "2025-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    private String createInquiry() throws Exception {
        String body = mockMvc.perform(post("/api/v1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"answer workflow test","customerChannel":"email"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("inquiryId").asText();
    }

    private String createDraft(String inquiryId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/inquiries/{id}/answers/draft", inquiryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"test question","tone":"professional","channel":"email"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("answerId").asText();
    }
}
