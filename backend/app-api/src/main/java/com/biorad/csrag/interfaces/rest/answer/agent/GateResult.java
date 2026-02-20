package com.biorad.csrag.interfaces.rest.answer.agent;

public record GateResult(
        String gate,
        boolean passed,
        String actualValue,
        String threshold
) {
}
