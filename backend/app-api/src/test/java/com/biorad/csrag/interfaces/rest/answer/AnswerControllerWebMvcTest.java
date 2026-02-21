package com.biorad.csrag.interfaces.rest.answer;

import com.biorad.csrag.infrastructure.persistence.answer.AiReviewResultJpaRepository;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaRepository;
import com.biorad.csrag.interfaces.rest.answer.agent.ApprovalAgentService;
import com.biorad.csrag.interfaces.rest.answer.agent.ReviewAgentService;
import com.biorad.csrag.inquiry.domain.model.Inquiry;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AnswerController.class)
@ContextConfiguration(classes = AnswerController.class)
@Import({JacksonAutoConfiguration.class, com.biorad.csrag.common.exception.GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class AnswerControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InquiryRepository inquiryRepository;

    @MockBean
    private AnswerComposerService answerComposerService;

    @MockBean
    private AnswerDraftJpaRepository answerDraftRepository;

    @MockBean
    private AiReviewResultJpaRepository aiReviewResultRepository;

    @MockBean
    private ReviewAgentService reviewAgentService;

    @MockBean
    private ApprovalAgentService approvalAgentService;

    @Test
    void review_returns403_whenUserIdMissing() throws Exception {
        String inquiryId = UUID.randomUUID().toString();
        String answerId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/review", inquiryId, answerId)
                        .header("X-User-Roles", "REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"review"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_USER_ID_REQUIRED"));
    }

    @Test
    void approve_returns403_whenRoleInsufficient() throws Exception {
        String inquiryId = UUID.randomUUID().toString();
        String answerId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/approve", inquiryId, answerId)
                        .header("X-User-Id", "reviewer-1")
                        .header("X-User-Roles", "REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"approve"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_ROLE_FORBIDDEN"));
    }

    @Test
    void send_returns400_whenInquiryIdInvalidFormat() throws Exception {
        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/send", "not-uuid", UUID.randomUUID())
                        .header("X-User-Id", "sender-1")
                        .header("X-User-Roles", "SENDER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"email","sendRequestId":"req-1"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approve_returns200_whenHeaderAndPayloadValid() throws Exception {
        UUID inquiryId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();

        when(inquiryRepository.findById(any())).thenReturn(Optional.of(org.mockito.Mockito.mock(Inquiry.class)));
        when(answerComposerService.approve(eq(inquiryId), eq(answerId), eq("approver-1"), eq("ok")))
                .thenReturn(sampleResponse(inquiryId.toString(), answerId.toString(), "APPROVED"));

        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/approve", inquiryId, answerId)
                        .header("X-User-Id", "approver-1")
                        .header("X-User-Roles", "APPROVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"approver-1","comment":"ok"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    private AnswerDraftResponse sampleResponse(String inquiryId, String answerId, String status) {
        return new AnswerDraftResponse(
                answerId,
                inquiryId,
                1,
                status,
                "CONDITIONAL",
                0.5,
                "draft",
                List.of(),
                List.of(),
                "professional",
                "email",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                List.of()
        );
    }
}
