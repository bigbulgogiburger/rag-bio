package com.biorad.csrag.interfaces.rest.ops;

import java.util.Map;

public record ProcessingTimeResponse(
        String period,
        String from,
        String to,
        double avgProcessingTimeHours,
        double medianProcessingTimeHours,
        double minProcessingTimeHours,
        double maxProcessingTimeHours,
        long totalCompleted,
        Map<String, Double> avgByStep
) {}
