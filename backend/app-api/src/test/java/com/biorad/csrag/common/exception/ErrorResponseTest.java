package com.biorad.csrag.common.exception;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @Test
    void of_withoutDetails_createsErrorResponse() {
        ErrorResponse response = ErrorResponse.of("NOT_FOUND", "Not found", 404, "req-123");

        assertThat(response.error().code()).isEqualTo("NOT_FOUND");
        assertThat(response.error().message()).isEqualTo("Not found");
        assertThat(response.error().status()).isEqualTo(404);
        assertThat(response.error().requestId()).isEqualTo("req-123");
        assertThat(response.error().timestamp()).isNotNull();
        assertThat(response.error().details()).isNull();
    }

    @Test
    void of_withDetails_includesFieldErrors() {
        Map<String, List<String>> details = Map.of(
                "question", List.of("must not be blank"),
                "channel", List.of("invalid value")
        );
        ErrorResponse response = ErrorResponse.of("VALIDATION_ERROR", "Validation failed", 400, "req-456", details);

        assertThat(response.error().details()).hasSize(2);
        assertThat(response.error().details()).containsKey("question");
    }

    @Test
    void of_nullRequestId() {
        ErrorResponse response = ErrorResponse.of("ERROR", "msg", 500, null);

        assertThat(response.error().requestId()).isNull();
    }
}
