package com.biorad.csrag.interfaces.rest.ops;

import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaEntity;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaRepository;
import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaEntity;
import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaRepository;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaRepository;
import com.biorad.csrag.infrastructure.persistence.orchestration.OrchestrationRunJpaEntity;
import com.biorad.csrag.infrastructure.persistence.orchestration.OrchestrationRunJpaRepository;
import com.biorad.csrag.infrastructure.persistence.retrieval.RetrievalEvidenceJpaEntity;
import com.biorad.csrag.infrastructure.persistence.retrieval.RetrievalEvidenceJpaRepository;
import com.biorad.csrag.infrastructure.persistence.sendattempt.SendAttemptJpaRepository;
import com.biorad.csrag.inquiry.infrastructure.persistence.jpa.InquiryJpaEntity;
import com.biorad.csrag.inquiry.infrastructure.persistence.jpa.SpringDataInquiryJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.biorad.csrag.common.exception.ExternalServiceException;
import com.biorad.csrag.common.exception.ValidationException;

import java.io.PrintWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "Ops Metrics", description = "운영 지표 및 대시보드 분석 API")
@RestController
@RequestMapping("/api/v1/ops/metrics")
public class OpsMetricsController {

    private final AnswerDraftJpaRepository answerDraftRepository;
    private final OrchestrationRunJpaRepository orchestrationRunRepository;
    private final SendAttemptJpaRepository sendAttemptRepository;
    private final SpringDataInquiryJpaRepository inquiryJpaRepository;
    private final RetrievalEvidenceJpaRepository retrievalEvidenceRepository;
    private final DocumentChunkJpaRepository documentChunkRepository;
    private final KnowledgeDocumentJpaRepository knowledgeDocumentRepository;

    public OpsMetricsController(
            AnswerDraftJpaRepository answerDraftRepository,
            OrchestrationRunJpaRepository orchestrationRunRepository,
            SendAttemptJpaRepository sendAttemptRepository,
            SpringDataInquiryJpaRepository inquiryJpaRepository,
            RetrievalEvidenceJpaRepository retrievalEvidenceRepository,
            DocumentChunkJpaRepository documentChunkRepository,
            KnowledgeDocumentJpaRepository knowledgeDocumentRepository
    ) {
        this.answerDraftRepository = answerDraftRepository;
        this.orchestrationRunRepository = orchestrationRunRepository;
        this.sendAttemptRepository = sendAttemptRepository;
        this.inquiryJpaRepository = inquiryJpaRepository;
        this.retrievalEvidenceRepository = retrievalEvidenceRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
    }

    @Operation(summary = "운영 지표 조회", description = "발송 성공률, 폴백 비율, 중복 차단 등 종합 운영 지표를 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public OpsMetricsResponse getMetrics(@RequestParam(defaultValue = "5") int topFailures) {
        long approvedOrSent = answerDraftRepository.countByStatusIn(List.of("APPROVED", "SENT"));
        long sent = answerDraftRepository.countByStatus("SENT");
        long totalDraft = answerDraftRepository.count();
        long fallbackDraft = answerDraftRepository.countByRiskFlagsContaining("FALLBACK_DRAFT_USED");
        long totalSendAttempts = sendAttemptRepository.count();
        long duplicateBlockedCount = sendAttemptRepository.countByOutcome("DUPLICATE_BLOCKED");

        double sendSuccessRate = approvedOrSent == 0 ? 0.0 : round2((sent * 100.0) / approvedOrSent);
        double fallbackDraftRate = totalDraft == 0 ? 0.0 : round2((fallbackDraft * 100.0) / totalDraft);
        double duplicateBlockRate = totalSendAttempts == 0 ? 0.0 : round2((duplicateBlockedCount * 100.0) / totalSendAttempts);

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
                duplicateBlockedCount,
                totalSendAttempts,
                duplicateBlockRate,
                fallbackDraft,
                totalDraft,
                fallbackDraftRate,
                topReasons
        );
    }

    @Operation(summary = "타임라인 조회", description = "일별 문의 접수/답변 발송/초안 생성 시계열 데이터를 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/timeline")
    @ResponseStatus(HttpStatus.OK)
    public TimelineResponse getTimeline(
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        DateRange range = resolveDateRange(period, from, to);
        List<InquiryJpaEntity> inquiries = inquiryJpaRepository.findAll().stream()
                .filter(i -> !i.getCreatedAt().isBefore(range.from) && !i.getCreatedAt().isAfter(range.to))
                .toList();
        List<AnswerDraftJpaEntity> drafts = answerDraftRepository.findByCreatedAtBetween(range.from, range.to);
        List<AnswerDraftJpaEntity> sentDrafts = answerDraftRepository.findByStatusAndSentAtBetween("SENT", range.from, range.to);

        Map<String, long[]> dailyMap = new LinkedHashMap<>();
        LocalDate start = range.from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = range.to.atZone(ZoneOffset.UTC).toLocalDate();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dailyMap.put(d.toString(), new long[]{0, 0, 0});
        }

        for (InquiryJpaEntity inq : inquiries) {
            String day = inq.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate().toString();
            long[] counts = dailyMap.get(day);
            if (counts != null) counts[0]++;
        }
        for (AnswerDraftJpaEntity draft : sentDrafts) {
            if (draft.getSentAt() != null) {
                String day = draft.getSentAt().atZone(ZoneOffset.UTC).toLocalDate().toString();
                long[] counts = dailyMap.get(day);
                if (counts != null) counts[1]++;
            }
        }
        for (AnswerDraftJpaEntity draft : drafts) {
            String day = draft.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate().toString();
            long[] counts = dailyMap.get(day);
            if (counts != null) counts[2]++;
        }

        List<TimelineResponse.DailyMetric> data = dailyMap.entrySet().stream()
                .map(e -> new TimelineResponse.DailyMetric(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();

        return new TimelineResponse(period, range.from.toString(), range.to.toString(), data);
    }

    @Operation(summary = "처리 시간 통계", description = "평균/중앙값/최소/최대 처리 시간과 파이프라인 단계별 평균 시간을 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/processing-time")
    @ResponseStatus(HttpStatus.OK)
    public ProcessingTimeResponse getProcessingTime(
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        DateRange range = resolveDateRange(period, from, to);
        List<AnswerDraftJpaEntity> sentDrafts = answerDraftRepository.findByStatusAndSentAtBetween("SENT", range.from, range.to);

        List<Double> processingHours = new ArrayList<>();
        for (AnswerDraftJpaEntity draft : sentDrafts) {
            if (draft.getSentAt() != null && draft.getCreatedAt() != null) {
                double hours = ChronoUnit.MINUTES.between(draft.getCreatedAt(), draft.getSentAt()) / 60.0;
                processingHours.add(hours);
            }
        }

        double avg = 0, median = 0, min = 0, max = 0;
        if (!processingHours.isEmpty()) {
            Collections.sort(processingHours);
            avg = round2(processingHours.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            min = round2(processingHours.get(0));
            max = round2(processingHours.get(processingHours.size() - 1));
            int mid = processingHours.size() / 2;
            median = processingHours.size() % 2 == 0
                    ? round2((processingHours.get(mid - 1) + processingHours.get(mid)) / 2.0)
                    : round2(processingHours.get(mid));
        }

        List<OrchestrationRunJpaEntity> runs = orchestrationRunRepository.findAll().stream()
                .filter(r -> !r.getCreatedAt().isBefore(range.from) && !r.getCreatedAt().isAfter(range.to))
                .filter(r -> "SUCCESS".equalsIgnoreCase(r.getStatus()))
                .toList();

        Map<String, Double> avgByStep = runs.stream()
                .collect(Collectors.groupingBy(
                        OrchestrationRunJpaEntity::getStep,
                        Collectors.averagingDouble(r -> r.getLatencyMs() / 1000.0)
                ));
        avgByStep.replaceAll((k, v) -> round2(v));

        return new ProcessingTimeResponse(
                period, range.from.toString(), range.to.toString(),
                avg, median, min, max,
                processingHours.size(),
                avgByStep
        );
    }

    @Operation(summary = "KB 인용 통계", description = "지식 기반 문서의 인용 비율과 가장 많이 참조된 문서를 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/kb-usage")
    @ResponseStatus(HttpStatus.OK)
    public KbUsageResponse getKbUsage(
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        DateRange range = resolveDateRange(period, from, to);
        List<RetrievalEvidenceJpaEntity> evidences = retrievalEvidenceRepository.findByCreatedAtBetween(range.from, range.to);

        Map<UUID, String> chunkSourceTypeCache = new LinkedHashMap<>();
        long kbCount = 0;
        for (RetrievalEvidenceJpaEntity ev : evidences) {
            String sourceType = chunkSourceTypeCache.computeIfAbsent(ev.getChunkId(), cid ->
                    documentChunkRepository.findById(cid)
                            .map(DocumentChunkJpaEntity::getSourceType)
                            .orElse("INQUIRY")
            );
            if ("KNOWLEDGE_BASE".equals(sourceType)) {
                kbCount++;
            }
        }

        double kbUsageRate = evidences.isEmpty() ? 0.0 : round2((kbCount * 100.0) / evidences.size());

        List<Object[]> topChunks = retrievalEvidenceRepository.findTopReferencedChunks(range.from, range.to);
        List<KbUsageResponse.TopDocument> topDocuments = new ArrayList<>();
        int count = 0;
        for (Object[] row : topChunks) {
            if (count >= 5) break;
            UUID chunkId = (UUID) row[0];
            long refCount = (Long) row[1];
            var chunk = documentChunkRepository.findById(chunkId).orElse(null);
            if (chunk != null && "KNOWLEDGE_BASE".equals(chunk.getSourceType())) {
                String fileName = knowledgeDocumentRepository.findById(chunk.getDocumentId())
                        .map(doc -> doc.getTitle())
                        .orElse("Unknown");
                topDocuments.add(new KbUsageResponse.TopDocument(
                        chunk.getDocumentId().toString(), fileName, refCount
                ));
                count++;
            }
        }

        return new KbUsageResponse(
                period, range.from.toString(), range.to.toString(),
                evidences.size(), kbCount, kbUsageRate, topDocuments
        );
    }

    @Operation(summary = "CSV 내보내기", description = "지정 기간의 타임라인 데이터를 CSV 파일로 내보냅니다")
    @ApiResponse(responseCode = "200", description = "CSV 다운로드")
    @GetMapping("/export/csv")
    public void exportCsv(
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            HttpServletResponse response
    ) {
        TimelineResponse timeline = getTimeline(period, from, to);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"metrics_" + period + "_" + LocalDate.now() + ".csv\"");

        try (PrintWriter writer = response.getWriter()) {
            writer.println("date,inquiries_created,answers_sent,drafts_created");
            for (TimelineResponse.DailyMetric m : timeline.data()) {
                writer.printf("%s,%d,%d,%d%n", m.date(), m.inquiriesCreated(), m.answersSent(), m.draftsCreated());
            }
        } catch (Exception e) {
            throw new ExternalServiceException("CSVExport", "CSV export failed");
        }
    }

    // ===== Helper Methods =====

    private DateRange resolveDateRange(String period, String fromParam, String toParam) {
        Instant now = Instant.now();
        Instant from;
        Instant to;

        if (fromParam != null && toParam != null) {
            try {
                from = Instant.parse(fromParam);
                to = Instant.parse(toParam);
            } catch (Exception e) {
                throw new ValidationException("INVALID_DATE_FORMAT", "Invalid date format (ISO-8601 expected)");
            }
        } else {
            to = now;
            from = switch (period) {
                case "today" -> now.truncatedTo(ChronoUnit.DAYS);
                case "7d" -> now.minus(7, ChronoUnit.DAYS);
                case "30d" -> now.minus(30, ChronoUnit.DAYS);
                case "90d" -> now.minus(90, ChronoUnit.DAYS);
                default -> now.minus(30, ChronoUnit.DAYS);
            };
        }
        return new DateRange(from, to);
    }

    private record DateRange(Instant from, Instant to) {}

    private double round2(double value) {
        return Double.parseDouble(String.format(Locale.US, "%.2f", value));
    }
}
