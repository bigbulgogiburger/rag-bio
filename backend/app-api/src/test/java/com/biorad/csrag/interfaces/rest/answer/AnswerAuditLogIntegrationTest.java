package com.biorad.csrag.interfaces.rest.answer;

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
class AnswerAuditLogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void auditLogs_supports_status_actor_paging_filters() throws Exception {
        String inquiryId = createInquiry();

        String draftBody = mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/draft", inquiryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "audit log filter test",
                                  "tone": "professional",
                                  "channel": "email"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String answerId = objectMapper.readTree(draftBody).path("answerId").asText();

        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/review", inquiryId, answerId)
                        .header("X-User-Id", "reviewer-1")
                        .header("X-User-Roles", "REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"reviewer-1","comment":"reviewed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWED"));

        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/approve", inquiryId, answerId)
                        .header("X-User-Id", "approver-1")
                        .header("X-User-Roles", "APPROVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"approver-1","comment":"approved"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/v1/inquiries/{inquiryId}/answers/audit-logs", inquiryId)
                        .param("status", "APPROVED")
                        .param("actor", "approver-1")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.items[0].approvedBy").value("approver-1"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void auditLogs_rejects_invalid_sort_and_invalid_date_range() throws Exception {
        String inquiryId = createInquiry();

        mockMvc.perform(get("/api/v1/inquiries/{inquiryId}/answers/audit-logs", inquiryId)
                        .param("sort", "unknown,desc"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/inquiries/{inquiryId}/answers/audit-logs", inquiryId)
                        .param("from", "2026-02-14T23:59:59Z")
                        .param("to", "2026-02-14T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    private String createInquiry() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "audit log integration test",
                                  "customerChannel": "email"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode body = objectMapper.readTree(createResponse);
        return body.path("inquiryId").asText();
    }
}
