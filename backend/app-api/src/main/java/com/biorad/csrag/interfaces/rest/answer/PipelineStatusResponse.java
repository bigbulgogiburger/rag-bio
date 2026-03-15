package com.biorad.csrag.interfaces.rest.answer;

import java.util.List;

public record PipelineStatusResponse(
    String status,
    String startedAt,
    String error,
    List<PipelineStepResponse> steps
) {
    public record PipelineStepResponse(
        String step,
        String status,
        String message,
        String updatedAt
    ) {}
}
