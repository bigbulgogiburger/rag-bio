package com.biorad.csrag.interfaces.rest.document;

import com.biorad.csrag.app.CsRagApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = CsRagApplication.class)
@AutoConfigureMockMvc
class DocumentWorkflowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void uploadDocument_success() throws Exception {
        String inquiryId = createInquiry();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "dummy content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/inquiries/{id}/documents", inquiryId)
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("test.pdf"))
                .andExpect(jsonPath("$.status").value("UPLOADED"));
    }

    @Test
    void uploadDocument_emptyFile_returns400() throws Exception {
        String inquiryId = createInquiry();
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/inquiries/{id}/documents", inquiryId)
                        .file(emptyFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listDocuments_returnsArray() throws Exception {
        String inquiryId = createInquiry();
        mockMvc.perform(get("/api/v1/inquiries/{id}/documents", inquiryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listDocuments_afterUpload_containsDocument() throws Exception {
        String inquiryId = createInquiry();
        MockMultipartFile file = new MockMultipartFile(
                "file", "manual.pdf", "application/pdf", "pdf data".getBytes()
        );
        mockMvc.perform(multipart("/api/v1/inquiries/{id}/documents", inquiryId)
                        .file(file))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/inquiries/{id}/documents", inquiryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("manual.pdf"));
    }

    @Test
    void indexingStatus_beforeIndexing_returnsNotReady() throws Exception {
        String inquiryId = createInquiry();
        mockMvc.perform(get("/api/v1/inquiries/{id}/documents/indexing-status", inquiryId))
                .andExpect(status().isOk());
    }

    @Test
    void triggerIndexing_afterUpload() throws Exception {
        String inquiryId = createInquiry();
        MockMultipartFile file = new MockMultipartFile(
                "file", "index-test.pdf", "application/pdf", "pdf data".getBytes()
        );
        mockMvc.perform(multipart("/api/v1/inquiries/{id}/documents", inquiryId)
                        .file(file))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/inquiries/{id}/documents/indexing/run", inquiryId))
                .andExpect(status().isOk());
    }

    @Test
    void uploadDocument_inquiryNotFound_returns404() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "orphan.pdf", "application/pdf", "data".getBytes()
        );
        mockMvc.perform(multipart("/api/v1/inquiries/{id}/documents", "00000000-0000-0000-0000-000000000099")
                        .file(file))
                .andExpect(status().isNotFound());
    }

    private String createInquiry() throws Exception {
        String body = mockMvc.perform(post("/api/v1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"doc workflow test","customerChannel":"email"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("inquiryId").asText();
    }
}
