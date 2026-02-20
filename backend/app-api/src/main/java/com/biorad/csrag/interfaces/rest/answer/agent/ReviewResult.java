package com.biorad.csrag.interfaces.rest.answer.agent;

import java.util.List;

public record ReviewResult(
        String decision,
        int score,
        List<ReviewIssue> issues,
        String revisedDraft,
        String summary
) {
}
