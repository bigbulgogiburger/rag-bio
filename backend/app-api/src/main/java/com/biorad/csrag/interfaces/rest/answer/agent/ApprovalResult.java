package com.biorad.csrag.interfaces.rest.answer.agent;

import java.util.List;

public record ApprovalResult(
        String decision,
        String reason,
        List<GateResult> gateResults
) {
}
