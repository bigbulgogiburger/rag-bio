package com.biorad.csrag.interfaces.rest;

import com.biorad.csrag.application.InquiryListService;
import com.biorad.csrag.inquiry.application.usecase.AskQuestionUseCase;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import com.biorad.csrag.interfaces.rest.dto.InquiryListResponse;
import com.biorad.csrag.interfaces.rest.dto.InquiryListResponse.InquiryListItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * InquiryController WebMvc 테스트
 */
@WebMvcTest(controllers = InquiryController.class)
@org.springframework.test.context.ContextConfiguration(classes = InquiryController.class)
@AutoConfigureMockMvc(addFilters = false)
class InquiryControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AskQuestionUseCase askQuestionUseCase;

    @MockBean
    private InquiryRepository inquiryRepository;

    @MockBean
    private InquiryListService inquiryListService;

    @Test
    void listInquiries_returns200_withDefaultPagination() throws Exception {
        // given
        InquiryListResponse response = new InquiryListResponse(
            Collections.emptyList(),
            0,
            20,
            0,
            0
        );
        when(inquiryListService.list(any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/inquiries"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.totalPages").value(0))
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void listInquiries_returns200_withCustomPageSize() throws Exception {
        // given
        InquiryListResponse response = new InquiryListResponse(
            Collections.emptyList(),
            1,
            50,
            0,
            0
        );
        when(inquiryListService.list(any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/inquiries")
                .param("page", "1")
                .param("size", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void listInquiries_returns200_withFilters() throws Exception {
        // given
        UUID inquiryId = UUID.randomUUID();
        InquiryListItem item = new InquiryListItem(
            inquiryId,
            "Test question",
            "email",
            "RECEIVED",
            2,
            "DRAFT",
            Instant.parse("2026-02-13T12:00:00Z")
        );
        InquiryListResponse response = new InquiryListResponse(
            List.of(item),
            0,
            20,
            1,
            1
        );
        when(inquiryListService.list(
            eq(List.of("RECEIVED")),
            eq("email"),
            eq("test"),
            any(Instant.class),
            any(Instant.class),
            any(Pageable.class)
        )).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/inquiries")
                .param("status", "RECEIVED")
                .param("channel", "email")
                .param("keyword", "test")
                .param("from", "2026-02-01T00:00:00Z")
                .param("to", "2026-02-28T23:59:59Z"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].inquiryId").value(inquiryId.toString()))
            .andExpect(jsonPath("$.content[0].question").value("Test question"))
            .andExpect(jsonPath("$.content[0].customerChannel").value("email"))
            .andExpect(jsonPath("$.content[0].status").value("RECEIVED"))
            .andExpect(jsonPath("$.content[0].documentCount").value(2))
            .andExpect(jsonPath("$.content[0].latestAnswerStatus").value("DRAFT"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listInquiries_returns200_withMultipleStatusFilters() throws Exception {
        // given
        InquiryListResponse response = new InquiryListResponse(
            Collections.emptyList(),
            0,
            20,
            0,
            0
        );
        when(inquiryListService.list(
            eq(List.of("RECEIVED", "ANALYZED")),
            any(),
            any(),
            any(),
            any(),
            any(Pageable.class)
        )).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/inquiries")
                .param("status", "RECEIVED")
                .param("status", "ANALYZED"))
            .andExpect(status().isOk());
    }

    @Test
    void listInquiries_returns200_withSortParameter() throws Exception {
        // given
        InquiryListResponse response = new InquiryListResponse(
            Collections.emptyList(),
            0,
            20,
            0,
            0
        );
        when(inquiryListService.list(any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/inquiries")
                .param("sort", "createdAt,asc"))
            .andExpect(status().isOk());
    }

    @Test
    void listInquiries_returns200_withNullLatestAnswerStatus() throws Exception {
        // given
        UUID inquiryId = UUID.randomUUID();
        InquiryListItem item = new InquiryListItem(
            inquiryId,
            "No answer yet",
            "messenger",
            "RECEIVED",
            0,
            null,  // 답변 없음
            Instant.parse("2026-02-13T12:00:00Z")
        );
        InquiryListResponse response = new InquiryListResponse(
            List.of(item),
            0,
            20,
            1,
            1
        );
        when(inquiryListService.list(any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/inquiries"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].latestAnswerStatus").isEmpty());
    }
}
