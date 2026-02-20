package com.biorad.csrag.common.exception;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

public class ValidationException extends BusinessException {

    private final Map<String, List<String>> fieldErrors;

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
        this.fieldErrors = Map.of();
    }

    public ValidationException(String code, String message) {
        super(code, message, HttpStatus.BAD_REQUEST);
        this.fieldErrors = Map.of();
    }

    public ValidationException(String message, Map<String, List<String>> fieldErrors) {
        super("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
        this.fieldErrors = fieldErrors;
    }

    public Map<String, List<String>> getFieldErrors() { return fieldErrors; }
}
