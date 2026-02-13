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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CsRagApplication.class)
@AutoConfigureMockMvc
class AnswerWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void draftReviewApproveSend_withRoleAndIdempotency() throws Exception {
        String inquiryId = createInquiry();

        String draftResponse = mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/draft", inquiryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "This protocol is fully validated with strong supporting data.",
                                  "tone": "professional",
                                  "channel": "email"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String answerId = objectMapper.readTree(draftResponse).path("answerId").asText();

        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/review", inquiryId, answerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"reviewer-1","comment":"looks fine"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWED"));

        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/approve", inquiryId, answerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"approver-1","comment":"approved"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/approve", inquiryId, answerId)
                        .header("X-Role", "APPROVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"approver-1","comment":"approved"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        String sendResponse1 = mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/send", inquiryId, answerId)
                        .header("X-Role", "SENDER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"sender-1","channel":"email","sendRequestId":"req-123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sendResponse2 = mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/send", inquiryId, answerId)
                        .header("X-Role", "SENDER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"sender-1","channel":"email","sendRequestId":"req-123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode first = objectMapper.readTree(sendResponse1);
        JsonNode second = objectMapper.readTree(sendResponse2);
        assertThat(second.path("sendMessageId").asText()).isEqualTo(first.path("sendMessageId").asText());
    }

    private String createInquiry() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "workflow integration test",
                                  "customerChannel": "email"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(createResponse).path("inquiryId").asText();
    }
}
