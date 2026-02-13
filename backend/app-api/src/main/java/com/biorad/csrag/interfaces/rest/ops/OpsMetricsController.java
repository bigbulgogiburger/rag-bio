package com.biorad.csrag.interfaces.rest.ops;

import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaRepository;
import com.biorad.csrag.infrastructure.persistence.orchestration.OrchestrationRunJpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/ops/metrics")
public class OpsMetricsController {

    private final AnswerDraftJpaRepository answerDraftRepository;
    private final OrchestrationRunJpaRepository orchestrationRunRepository;

    public OpsMetricsController(
            AnswerDraftJpaRepository answerDraftRepository,
            OrchestrationRunJpaRepository orchestrationRunRepository
    ) {
        this.answerDraftRepository = answerDraftRepository;
        this.orchestrationRunRepository = orchestrationRunRepository;
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public OpsMetricsResponse getMetrics(@RequestParam(defaultValue = "5") int topFailures) {
        long approvedOrSent = answerDraftRepository.countByStatusIn(List.of("APPROVED", "SENT"));
        long sent = answerDraftRepository.countByStatus("SENT");
        long totalDraft = answerDraftRepository.count();
        long fallbackDraft = answerDraftRepository.countByRiskFlagsContaining("FALLBACK_DRAFT_USED");

        double sendSuccessRate = approvedOrSent == 0 ? 0.0 : round2((sent * 100.0) / approvedOrSent);
        double fallbackDraftRate = totalDraft == 0 ? 0.0 : round2((fallbackDraft * 100.0) / totalDraft);

        List<OpsMetricsResponse.FailureReasonCount> topReasons = orchestrationRunRepository.findAll().stream()
                .filter(r -> "FAILED".equalsIgnoreCase(r.getStatus()))
                .map(r -> {
                    String msg = r.getErrorMessage();
                    if (msg == null || msg.isBlank()) {
                        return "UNKNOWN_ERROR";
                    }
                    return msg.length() > 80 ? msg.substring(0, 80) : msg;
                })
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(Math.max(1, topFailures))
                .map(e -> new OpsMetricsResponse.FailureReasonCount(e.getKey(), e.getValue()))
                .toList();

        return new OpsMetricsResponse(
                approvedOrSent,
                sent,
                sendSuccessRate,
                fallbackDraft,
                totalDraft,
                fallbackDraftRate,
                topReasons
        );
    }

    private double round2(double value) {
        return Double.parseDouble(String.format(Locale.US, "%.2f", value));
    }
}
