package com.biorad.csrag.interfaces.rest.answer;

import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.analysis.AnalysisService;
import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AnswerComposerService {

    private final AnalysisService analysisService;

    public AnswerComposerService(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    public AnswerDraftResponse compose(UUID inquiryId, String question) {
        AnalyzeResponse analysis = analysisService.analyze(inquiryId, question, 5);

        String draft = switch (analysis.verdict()) {
            case "SUPPORTED" -> "문의 주신 내용은 현재 확보된 근거 기준으로 타당한 방향입니다. " +
                    "다만 실제 적용 전 샘플 조건과 장비 설정을 최종 점검해 주세요.";
            case "REFUTED" -> "문의 주신 해석은 현재 근거 기준으로 권장되지 않습니다. " +
                    "대안 프로토콜 또는 조건 재설정을 권장드립니다.";
            default -> "문의 주신 내용은 일부 근거가 있으나 조건 의존성이 있어 단정이 어렵습니다. " +
                    "아래 확인 항목을 점검한 뒤 재판정을 권장드립니다.";
        };

        List<String> citations = new ArrayList<>();
        for (EvidenceItem ev : analysis.evidences()) {
            citations.add("chunk=" + ev.chunkId() + " score=" + String.format("%.3f", ev.score()));
        }

        return new AnswerDraftResponse(
                inquiryId.toString(),
                analysis.verdict(),
                analysis.confidence(),
                draft,
                citations,
                analysis.riskFlags()
        );
    }
}
