package com.biorad.csrag.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ErrorResponse(ErrorBody error) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(
            String code,
            String message,
            int status,
            String requestId,
            Instant timestamp,
            Map<String, List<String>> details
    ) {}

    public static ErrorResponse of(String code, String message, int status, String requestId) {
        return new ErrorResponse(new ErrorBody(code, message, status, requestId, Instant.now(), null));
    }

    public static ErrorResponse of(String code, String message, int status, String requestId,
                                    Map<String, List<String>> details) {
        return new ErrorResponse(new ErrorBody(code, message, status, requestId, Instant.now(), details));
    }
}
