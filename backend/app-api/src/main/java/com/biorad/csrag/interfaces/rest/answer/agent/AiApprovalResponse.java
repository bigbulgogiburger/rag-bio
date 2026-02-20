package com.biorad.csrag.interfaces.rest.answer.agent;

import java.util.List;

public record AiApprovalResponse(
        String decision,
        String reason,
        List<GateResult> gateResults,
        String status,
        String approvedBy
) {
}
