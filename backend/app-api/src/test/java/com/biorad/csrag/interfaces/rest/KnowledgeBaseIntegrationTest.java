package com.biorad.csrag.interfaces.rest;

import com.biorad.csrag.app.CsRagApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = CsRagApplication.class)
@AutoConfigureMockMvc
class KnowledgeBaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void listDocuments_defaultPagination() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge-base/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void listDocuments_withFilters() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge-base/documents")
                        .param("category", "MANUAL")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void uploadDocument_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "manual.pdf", "application/pdf", "PDF content".getBytes()
        );
        MockMultipartFile titlePart = new MockMultipartFile(
                "title", "", "text/plain", "Test Manual".getBytes()
        );
        MockMultipartFile categoryPart = new MockMultipartFile(
                "category", "", "text/plain", "MANUAL".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/knowledge-base/documents")
                        .file(file)
                        .file(titlePart)
                        .file(categoryPart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Test Manual"))
                .andExpect(jsonPath("$.category").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("UPLOADED"));
    }

    @Test
    void uploadDocument_emptyFile_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]
        );
        MockMultipartFile titlePart = new MockMultipartFile(
                "title", "", "text/plain", "Empty doc".getBytes()
        );
        MockMultipartFile categoryPart = new MockMultipartFile(
                "category", "", "text/plain", "FAQ".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/knowledge-base/documents")
                        .file(emptyFile)
                        .file(titlePart)
                        .file(categoryPart))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getDocument_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge-base/documents/00000000-0000-0000-0000-000000000099"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteDocument_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/knowledge-base/documents/00000000-0000-0000-0000-000000000099"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getStats_success() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge-base/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").isNumber())
                .andExpect(jsonPath("$.indexedDocuments").isNumber())
                .andExpect(jsonPath("$.totalChunks").isNumber());
    }

    @Test
    void uploadAndGetDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "protocol.pdf", "application/pdf", "content".getBytes()
        );
        MockMultipartFile titlePart = new MockMultipartFile(
                "title", "", "text/plain", "Protocol Doc".getBytes()
        );
        MockMultipartFile categoryPart = new MockMultipartFile(
                "category", "", "text/plain", "PROTOCOL".getBytes()
        );
        MockMultipartFile productFamilyPart = new MockMultipartFile(
                "productFamily", "", "text/plain", "qPCR".getBytes()
        );

        String uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge-base/documents")
                        .file(file)
                        .file(titlePart)
                        .file(categoryPart)
                        .file(productFamilyPart))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readTree(uploadResponse).path("documentId").asText();

        mockMvc.perform(get("/api/v1/knowledge-base/documents/{docId}", docId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Protocol Doc"))
                .andExpect(jsonPath("$.category").value("PROTOCOL"))
                .andExpect(jsonPath("$.productFamily").value("qPCR"));
    }

    @Test
    void uploadAndDeleteDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "to-delete.pdf", "application/pdf", "content".getBytes()
        );
        MockMultipartFile titlePart = new MockMultipartFile(
                "title", "", "text/plain", "To Delete".getBytes()
        );
        MockMultipartFile categoryPart = new MockMultipartFile(
                "category", "", "text/plain", "FAQ".getBytes()
        );

        String uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge-base/documents")
                        .file(file)
                        .file(titlePart)
                        .file(categoryPart))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readTree(uploadResponse).path("documentId").asText();

        mockMvc.perform(delete("/api/v1/knowledge-base/documents/{docId}", docId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/knowledge-base/documents/{docId}", docId))
                .andExpect(status().isNotFound());
    }
}
