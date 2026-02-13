package com.biorad.csrag.interfaces.rest.answer;

public record SendAnswerRequest(
        String actor,
        String channel,
        String sendRequestId
) {
}
