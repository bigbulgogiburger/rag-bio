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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = CsRagApplication.class)
@AutoConfigureMockMvc
class InquiryControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void createInquiry_success() throws Exception {
        mockMvc.perform(post("/api/v1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "qPCR 프로토콜 검증", "customerChannel": "email"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inquiryId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    void createInquiry_blankQuestion_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "", "customerChannel": "email"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createInquiry_withPreferredTone() throws Exception {
        mockMvc.perform(post("/api/v1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "테스트", "customerChannel": "email", "preferredTone": "technical"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    void getInquiry_success() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "조회 테스트", "customerChannel": "portal"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(createResponse).path("inquiryId").asText();

        mockMvc.perform(get("/api/v1/inquiries/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inquiryId").value(id))
                .andExpect(jsonPath("$.question").value("조회 테스트"))
                .andExpect(jsonPath("$.customerChannel").value("portal"));
    }

    @Test
    void getInquiry_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/inquiries/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getInquiry_invalidId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/inquiries/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateInquiry_success() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "원래 질문", "customerChannel": "email"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(createResponse).path("inquiryId").asText();

        mockMvc.perform(patch("/api/v1/inquiries/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "수정된 질문"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("수정된 질문"));
    }

    @Test
    void listInquiries_defaultPagination() throws Exception {
        mockMvc.perform(get("/api/v1/inquiries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void listInquiries_withFilters() throws Exception {
        mockMvc.perform(get("/api/v1/inquiries")
                        .param("status", "RECEIVED")
                        .param("channel", "email")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void listInquiries_withKeyword() throws Exception {
        // Create an inquiry first
        mockMvc.perform(post("/api/v1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "키워드검색테스트용문의", "customerChannel": "email"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/inquiries")
                        .param("keyword", "키워드검색"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }
}
