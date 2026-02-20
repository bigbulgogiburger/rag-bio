package com.biorad.csrag.common.exception;

import org.springframework.http.HttpStatus;

public class ExternalServiceException extends BusinessException {

    private final String serviceName;

    public ExternalServiceException(String serviceName, String message) {
        super("EXTERNAL_SERVICE_ERROR", serviceName + ": " + message, HttpStatus.BAD_GATEWAY);
        this.serviceName = serviceName;
    }

    public ExternalServiceException(String serviceName, String message, Throwable cause) {
        super("EXTERNAL_SERVICE_ERROR", serviceName + ": " + message, HttpStatus.BAD_GATEWAY, cause);
        this.serviceName = serviceName;
    }

    public String getServiceName() { return serviceName; }
}
