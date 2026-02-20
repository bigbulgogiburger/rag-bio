package com.biorad.csrag.interfaces.rest.answer.agent;

public record ReviewIssue(
        String category,
        String severity,
        String description,
        String suggestion
) {
}
