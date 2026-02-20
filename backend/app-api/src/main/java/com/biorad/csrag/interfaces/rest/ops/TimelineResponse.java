package com.biorad.csrag.interfaces.rest.ops;

import java.util.List;

public record TimelineResponse(
        String period,
        String from,
        String to,
        List<DailyMetric> data
) {
    public record DailyMetric(
            String date,
            long inquiriesCreated,
            long answersSent,
            long draftsCreated
    ) {}
}
