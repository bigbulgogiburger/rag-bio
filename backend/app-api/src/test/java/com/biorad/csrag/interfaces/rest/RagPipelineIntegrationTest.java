package com.biorad.csrag.interfaces.rest;

import com.biorad.csrag.app.CsRagApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RAG 파이프라인 전체 E2E 통합 테스트.
 *
 * 문서 업로드 → 파싱 → 청킹 → 임베딩 → 벡터 저장 → 검색 → 검증 → 답변 생성
 * 전 과정을 MockVectorStore + MockEmbeddingService 환경에서 검증한다.
 * (100MB 미만의 소규모 데이터)
 */
@SpringBootTest(classes = CsRagApplication.class)
@AutoConfigureMockMvc
class RagPipelineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    private String inquiryId;

    @BeforeEach
    void setUp() throws Exception {
        // 1. 문의 생성
        String createResponse = mockMvc.perform(post("/api/v1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Is the CFX96 qPCR system compatible with SYBR Green supermix for multiplex assays?",
                                  "customerChannel": "email"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        inquiryId = objectMapper.readTree(createResponse).path("inquiryId").asText();
    }

    @Test
    void fullRagPipeline_textDocument_indexToAnswerDraft() throws Exception {
        // 2. 텍스트 문서 생성 (Bio-Rad 기술 문서 시뮬레이션)
        UUID documentId = UUID.randomUUID();
        Path docFile = tempDir.resolve(documentId + "_protocol.txt");
        String documentContent = """
                Bio-Rad CFX96 Touch Real-Time PCR Detection System Protocol Guide.

                The CFX96 system is fully compatible with SYBR Green supermix for singleplex assays.
                For multiplex assays, Bio-Rad recommends using SsoAdvanced Universal Probes Supermix instead of SYBR Green.
                SYBR Green is a non-specific dye that binds to all double-stranded DNA, making it unsuitable for multiplex detection.

                Recommended setup parameters for CFX96:
                - Denaturation: 95C for 10 seconds
                - Annealing: 60C for 30 seconds
                - Extension: 72C for 30 seconds
                - Total cycles: 40

                Quality control: Always include a no-template control (NTC) and positive control in each run.
                Melt curve analysis is required when using SYBR Green to verify amplification specificity.

                For multiplex applications, use fluorophore-labeled probes such as FAM, HEX, and Cy5.
                The CFX96 supports up to 5 fluorescence channels for simultaneous detection.
                """;
        Files.writeString(docFile, documentContent);

        // 3. 문서 메타데이터 DB 삽입
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO documents(id, inquiry_id, file_name, content_type, file_size, storage_path, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                documentId,
                UUID.fromString(inquiryId),
                "protocol.txt",
                "text/plain",
                documentContent.length(),
                docFile.toString(),
                "UPLOADED",
                now,
                now
        );

        // 4. 인덱싱 실행 (파싱 → 청킹 → 임베딩 → 벡터 저장)
        String indexingResponse = mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/documents/indexing/run", inquiryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failed").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 5. 인덱싱 상태 확인 - INDEXED 상태
        mockMvc.perform(get("/api/v1/inquiries/{inquiryId}/documents/indexing-status", inquiryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexed").value(1))
                .andExpect(jsonPath("$.failed").value(0));

        // 6. 분석 실행 (Retrieve + Verify)
        String analysisResponse = mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/analysis", inquiryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Is the CFX96 qPCR system compatible with SYBR Green supermix for multiplex assays?",
                                  "topK": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").isNotEmpty())
                .andExpect(jsonPath("$.confidence").isNumber())
                .andExpect(jsonPath("$.evidences").isArray())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode analysis = objectMapper.readTree(analysisResponse);
        assertThat(analysis.path("evidences").size()).isGreaterThan(0);
        assertThat(analysis.path("verdict").asText()).isIn("SUPPORTED", "REFUTED", "CONDITIONAL");

        // 7. 답변 초안 생성 (전체 오케스트레이션: Retrieve → Verify → Compose)
        String draftResponse = mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/draft", inquiryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Is the CFX96 qPCR system compatible with SYBR Green supermix for multiplex assays?",
                                  "tone": "professional",
                                  "channel": "email"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.draft").isNotEmpty())
                .andExpect(jsonPath("$.verdict").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode draft = objectMapper.readTree(draftResponse);
        assertThat(draft.path("draft").asText()).isNotBlank();
        assertThat(draft.path("answerId").asText()).isNotBlank();
    }

    @Test
    void fullRagPipeline_multipleDocuments_retrieveFromBoth() throws Exception {
        // 두 개의 문서를 인덱싱하고 검색에서 모두 반영되는지 확인

        // 문서 1: 프로토콜 가이드
        UUID doc1Id = UUID.randomUUID();
        Path doc1File = tempDir.resolve(doc1Id + "_protocol.txt");
        Files.writeString(doc1File, """
                Bio-Rad ddPCR System Protocol.
                The QX200 Droplet Digital PCR system provides absolute quantification of target DNA.
                Sample preparation requires 20 uL reaction volume per well.
                Use Bio-Rad ddPCR Supermix for Probes for optimal performance.
                """);

        // 문서 2: FAQ
        UUID doc2Id = UUID.randomUUID();
        Path doc2File = tempDir.resolve(doc2Id + "_faq.txt");
        Files.writeString(doc2File, """
                Frequently Asked Questions about Bio-Rad ddPCR.
                Q: What is the minimum copy number detectable by QX200?
                A: The QX200 system can detect as few as 1-10 copies per reaction.
                Q: Can I use SYBR Green with ddPCR?
                A: No, ddPCR requires probe-based chemistry such as TaqMan probes.
                """);

        Instant now = Instant.now();
        UUID iq = UUID.fromString(inquiryId);

        jdbcTemplate.update(
                "INSERT INTO documents(id, inquiry_id, file_name, content_type, file_size, storage_path, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                doc1Id, iq, "protocol.txt", "text/plain", 200L, doc1File.toString(), "UPLOADED", now, now
        );
        jdbcTemplate.update(
                "INSERT INTO documents(id, inquiry_id, file_name, content_type, file_size, storage_path, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                doc2Id, iq, "faq.txt", "text/plain", 300L, doc2File.toString(), "UPLOADED", now, now
        );

        // 인덱싱
        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/documents/indexing/run", inquiryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(2))
                .andExpect(jsonPath("$.succeeded").value(2));

        // 분석
        String analysisResponse = mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/analysis", inquiryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Can I use SYBR Green with the QX200 ddPCR system?",
                                  "topK": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidences").isArray())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode analysis = objectMapper.readTree(analysisResponse);
        // 두 문서에서 청크가 생성되어 검색 결과에 포함되어야 함
        assertThat(analysis.path("evidences").size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void fullRagPipeline_answerWorkflow_draftToSend() throws Exception {
        // 인덱싱 없이도 오케스트레이션이 동작하는지 검증
        // (벡터 스토어가 비어있으면 CONDITIONAL verdict + fallback draft)

        String draftResponse = mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/draft", inquiryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "What temperature should I use for annealing?",
                                  "tone": "brief",
                                  "channel": "messenger"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.draft").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String answerId = objectMapper.readTree(draftResponse).path("answerId").asText();

        // Review → Approve → Send
        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/review", inquiryId, answerId)
                        .header("X-User-Id", "reviewer-1")
                        .header("X-User-Roles", "REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"reviewer-1","comment":"lgtm"}
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

        mockMvc.perform(post("/api/v1/inquiries/{inquiryId}/answers/{answerId}/send", inquiryId, answerId)
                        .header("X-User-Id", "sender-1")
                        .header("X-User-Roles", "SENDER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"sender-1","channel":"messenger","sendRequestId":"req-rag-test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));
    }
}
