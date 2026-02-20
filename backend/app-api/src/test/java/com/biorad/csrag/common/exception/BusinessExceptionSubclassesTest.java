package com.biorad.csrag.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionSubclassesTest {

    @Test
    void notFoundException_singleArg() {
        NotFoundException ex = new NotFoundException("Resource not found");

        assertThat(ex.getCode()).isEqualTo("NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo("Resource not found");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void notFoundException_withCodeAndMessage() {
        NotFoundException ex = new NotFoundException("INQUIRY_NOT_FOUND", "Inquiry abc-123 not found");

        assertThat(ex.getCode()).isEqualTo("INQUIRY_NOT_FOUND");
        assertThat(ex.getMessage()).contains("Inquiry").contains("abc-123");
    }

    @Test
    void forbiddenException_singleArg() {
        ForbiddenException ex = new ForbiddenException("No access");

        assertThat(ex.getCode()).isEqualTo("FORBIDDEN");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void forbiddenException_withCode() {
        ForbiddenException ex = new ForbiddenException("AUTH_ROLE_FORBIDDEN", "Missing role");

        assertThat(ex.getCode()).isEqualTo("AUTH_ROLE_FORBIDDEN");
    }

    @Test
    void conflictException_singleArg() {
        ConflictException ex = new ConflictException("Duplicate");

        assertThat(ex.getCode()).isEqualTo("CONFLICT");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void conflictException_withCode() {
        ConflictException ex = new ConflictException("DUPLICATE_SEND", "Already sent");

        assertThat(ex.getCode()).isEqualTo("DUPLICATE_SEND");
    }

    @Test
    void validationException_singleArg() {
        ValidationException ex = new ValidationException("Bad input");

        assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getFieldErrors()).isEmpty();
    }

    @Test
    void validationException_withFieldErrors() {
        Map<String, List<String>> fields = Map.of("question", List.of("must not be blank"));
        ValidationException ex = new ValidationException("Validation", fields);

        assertThat(ex.getFieldErrors()).containsKey("question");
    }

    @Test
    void externalServiceException() {
        ExternalServiceException ex = new ExternalServiceException("OpenAI", "timeout");

        assertThat(ex.getCode()).isEqualTo("EXTERNAL_SERVICE_ERROR");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ex.getServiceName()).isEqualTo("OpenAI");
        assertThat(ex.getMessage()).contains("OpenAI").contains("timeout");
    }

    @Test
    void externalServiceException_withCause() {
        RuntimeException cause = new RuntimeException("connection refused");
        ExternalServiceException ex = new ExternalServiceException("VectorDB", "connection error", cause);

        assertThat(ex.getCause()).isEqualTo(cause);
        assertThat(ex.getServiceName()).isEqualTo("VectorDB");
    }
}
