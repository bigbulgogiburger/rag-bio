package com.biorad.csrag.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        log.warn("business.exception code={} message={}", ex.getCode(), ex.getMessage());

        Map<String, List<String>> details = null;
        if (ex instanceof ValidationException ve && !ve.getFieldErrors().isEmpty()) {
            details = ve.getFieldErrors();
        }

        ErrorResponse response = ErrorResponse.of(
                ex.getCode(), ex.getMessage(), ex.getStatus().value(), getRequestId(), details);
        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String code = status.name();
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();

        log.warn("response-status.exception status={} message={}", status.value(), message);

        ErrorResponse response = ErrorResponse.of(code, message, status.value(), getRequestId());
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, List<String>> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.computeIfAbsent(error.getField(), k -> new ArrayList<>())
                    .add(error.getDefaultMessage());
        }

        log.warn("validation.exception fields={}", fieldErrors.keySet());

        ErrorResponse response = ErrorResponse.of(
                "VALIDATION_ERROR", "Request validation failed",
                HttpStatus.BAD_REQUEST.value(), getRequestId(), fieldErrors);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("message.not-readable message={}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(
                "BAD_REQUEST", "Malformed request body",
                HttpStatus.BAD_REQUEST.value(), getRequestId());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("missing.param name={}", ex.getParameterName());
        ErrorResponse response = ErrorResponse.of(
                "BAD_REQUEST", "Missing required parameter: " + ex.getParameterName(),
                HttpStatus.BAD_REQUEST.value(), getRequestId());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("type.mismatch name={} value={}", ex.getName(), ex.getValue());
        ErrorResponse response = ErrorResponse.of(
                "BAD_REQUEST", "Invalid parameter value for: " + ex.getName(),
                HttpStatus.BAD_REQUEST.value(), getRequestId());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ErrorResponse response = ErrorResponse.of(
                "METHOD_NOT_ALLOWED", "HTTP method not supported: " + ex.getMethod(),
                HttpStatus.METHOD_NOT_ALLOWED.value(), getRequestId());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        ErrorResponse response = ErrorResponse.of(
                "UNSUPPORTED_MEDIA_TYPE", "Content type not supported: " + ex.getContentType(),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), getRequestId());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("upload.size-exceeded maxSize={}", ex.getMaxUploadSize());
        ErrorResponse response = ErrorResponse.of(
                "PAYLOAD_TOO_LARGE", "File upload exceeds maximum allowed size",
                HttpStatus.PAYLOAD_TOO_LARGE.value(), getRequestId());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("access.denied message={}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(
                "FORBIDDEN", "Access denied",
                HttpStatus.FORBIDDEN.value(), getRequestId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("auth.bad-credentials");
        ErrorResponse response = ErrorResponse.of(
                "UNAUTHORIZED", "Invalid credentials",
                HttpStatus.UNAUTHORIZED.value(), getRequestId());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        ErrorResponse response = ErrorResponse.of(
                "NOT_FOUND", "Resource not found",
                HttpStatus.NOT_FOUND.value(), getRequestId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("unhandled.exception type={} message={}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        ErrorResponse response = ErrorResponse.of(
                "INTERNAL_SERVER_ERROR", "An unexpected error occurred",
                HttpStatus.INTERNAL_SERVER_ERROR.value(), getRequestId());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private String getRequestId() {
        return MDC.get("requestId");
    }
}
