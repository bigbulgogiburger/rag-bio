package com.biorad.csrag.testutil;

import com.biorad.csrag.inquiry.application.usecase.AskQuestionCommand;
import com.biorad.csrag.inquiry.application.usecase.AskQuestionResult;
import com.biorad.csrag.inquiry.domain.model.Inquiry;
import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.model.InquiryStatus;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Factory for creating pre-configured mocks for common dependencies.
 * Reduces boilerplate in test classes.
 */
public final class MockFactory {

    private MockFactory() {}

    // ===== InquiryRepository =====

    /**
     * Creates a mock InquiryRepository that returns a default inquiry for any findById call.
     */
    public static InquiryRepository inquiryRepositoryReturning(Inquiry inquiry) {
        InquiryRepository repo = mock(InquiryRepository.class);
        lenient().when(repo.findById(any(InquiryId.class))).thenReturn(Optional.of(inquiry));
        lenient().when(repo.save(any(Inquiry.class))).thenAnswer(inv -> inv.getArgument(0));
        return repo;
    }

    /**
     * Creates a mock InquiryRepository that returns empty for any findById call.
     */
    public static InquiryRepository inquiryRepositoryEmpty() {
        InquiryRepository repo = mock(InquiryRepository.class);
        lenient().when(repo.findById(any(InquiryId.class))).thenReturn(Optional.empty());
        return repo;
    }

    // ===== AskQuestionResult =====

    public static AskQuestionResult askQuestionResult() {
        return askQuestionResult(UUID.randomUUID());
    }

    public static AskQuestionResult askQuestionResult(UUID inquiryId) {
        return new AskQuestionResult(
                inquiryId.toString(),
                "RECEIVED",
                "Inquiry created"
        );
    }

    // ===== AskQuestionCommand =====

    public static AskQuestionCommand askQuestionCommand() {
        return new AskQuestionCommand("테스트 질문입니다", "email", null);
    }

    public static AskQuestionCommand askQuestionCommand(String question, String channel) {
        return new AskQuestionCommand(question, channel, null);
    }

    // ===== MockMultipartFile =====

    public static MockMultipartFile pdfFile() {
        return new MockMultipartFile(
                "file",
                "test-document.pdf",
                "application/pdf",
                "dummy-pdf-content-for-testing".getBytes()
        );
    }

    public static MockMultipartFile wordFile() {
        return new MockMultipartFile(
                "file",
                "test-document.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "dummy-docx-content".getBytes()
        );
    }

    public static MockMultipartFile largeFile(int sizeInMB) {
        byte[] content = new byte[sizeInMB * 1024 * 1024];
        return new MockMultipartFile(
                "file",
                "large-file.pdf",
                "application/pdf",
                content
        );
    }

    // ===== Inquiry reconstitution helpers =====

    public static Inquiry inquiryWithId(UUID id) {
        return Inquiry.reconstitute(
                new InquiryId(id),
                "테스트 질문",
                "email",
                "professional",
                InquiryStatus.RECEIVED,
                Instant.now()
        );
    }

    public static Inquiry inquiryInReview(UUID id) {
        return Inquiry.reconstitute(
                new InquiryId(id),
                "검토 중인 질문",
                "portal",
                "professional",
                InquiryStatus.IN_REVIEW,
                Instant.now()
        );
    }
}
