package com.biorad.csrag.interfaces.rest;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CsRagApplication.class)
@AutoConfigureMockMvc
class InquiryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createInquiryAndFetchById() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Is this qPCR setup valid?",
                                  "customerChannel": "email"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inquiryId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String inquiryId = objectMapper.readTree(createResponse).path("inquiryId").asText();

        mockMvc.perform(get("/api/v1/inquiries/{inquiryId}", inquiryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inquiryId").value(inquiryId))
                .andExpect(jsonPath("$.question").value("Is this qPCR setup valid?"))
                .andExpect(jsonPath("$.customerChannel").value("email"))
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    void uploadDocumentAndListStatuses() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Please verify this protocol",
                                  "customerChannel": "portal"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String inquiryId = objectMapper.readTree(createResponse).path("inquiryId").asText();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "protocol.pdf",
                "application/pdf",
                "dummy-pdf-content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/inquiries/{inquiryId}/documents", inquiryId)
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inquiryId").value(inquiryId))
                .andExpect(jsonPath("$.fileName").value("protocol.pdf"))
                .andExpect(jsonPath("$.status").value("UPLOADED"));

        String listResponse = mockMvc.perform(get("/api/v1/inquiries/{inquiryId}/documents", inquiryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inquiryId").value(inquiryId))
                .andExpect(jsonPath("$[0].fileName").value("protocol.pdf"))
                .andExpect(jsonPath("$[0].status").value("UPLOADED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode items = objectMapper.readTree(listResponse);
        assertThat(items).isNotEmpty();
    }
}
