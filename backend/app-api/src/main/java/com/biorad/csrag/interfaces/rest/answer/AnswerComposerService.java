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

    public AnswerDraftResponse compose(UUID inquiryId, String question, String tone, String channel) {
        AnalyzeResponse analysis = analysisService.analyze(inquiryId, question, 5);
        String normalizedTone = (tone == null || tone.isBlank()) ? "professional" : tone.trim().toLowerCase();
        String normalizedChannel = (channel == null || channel.isBlank()) ? "email" : channel.trim().toLowerCase();

        String draft = createDraftByTone(analysis, normalizedTone);
        draft = applyGuardrails(draft, analysis.confidence(), analysis.riskFlags());
        draft = formatByChannel(draft, normalizedChannel, analysis.riskFlags());

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

    private String createDraftByTone(AnalyzeResponse analysis, String tone) {
        return switch (tone) {
            case "brief" -> switch (analysis.verdict()) {
                case "SUPPORTED" -> "근거상 타당합니다. 적용 전 핵심 조건만 최종 점검해 주세요.";
                case "REFUTED" -> "근거상 권장되지 않습니다. 조건 재설정 또는 대안 프로토콜을 권장합니다.";
                default -> "조건 의존성이 있어 단정이 어렵습니다. 추가 확인 후 재판정이 필요합니다.";
            };
            case "technical" -> switch (analysis.verdict()) {
                case "SUPPORTED" -> "현재 retrieval evidence 기준으로 질문 주장과 문서 일치도가 높습니다. " +
                        "다만 실제 실행 전 샘플 전처리 조건, 장비 파라미터, QC 기준을 교차 검증해 주세요.";
                case "REFUTED" -> "현재 evidence score 및 risk flag 기준으로 질문 주장은 문서 근거와 충돌합니다. " +
                        "프로토콜 파라미터를 재검토하고 대체 workflow를 적용하는 것이 적절합니다.";
                default -> "근거 간 상충 또는 신뢰도 부족이 감지되었습니다. " +
                        "추가 데이터 확보(샘플 조건/장비 설정/대조군 결과) 후 재평가를 권장합니다.";
            };
            default -> switch (analysis.verdict()) {
                case "SUPPORTED" -> "문의 주신 내용은 현재 확보된 근거 기준으로 타당한 방향입니다. " +
                        "다만 실제 적용 전 샘플 조건과 장비 설정을 최종 점검해 주세요.";
                case "REFUTED" -> "문의 주신 해석은 현재 근거 기준으로 권장되지 않습니다. " +
                        "대안 프로토콜 또는 조건 재설정을 권장드립니다.";
                default -> "문의 주신 내용은 일부 근거가 있으나 조건 의존성이 있어 단정이 어렵습니다. " +
                        "아래 확인 항목을 점검한 뒤 재판정을 권장드립니다.";
            };
        };
    }

    private String applyGuardrails(String draft, double confidence, List<String> riskFlags) {
        List<String> notices = new ArrayList<>();

        if (confidence < 0.75) {
            notices.add("현재 근거 신뢰도가 충분히 높지 않아 추가 확인이 필요합니다.");
        }

        if (!riskFlags.isEmpty()) {
            notices.add("위험 신호가 감지되어 단정적 결론 대신 보수적 안내가 필요합니다.");
        }

        if (notices.isEmpty()) {
            return draft;
        }

        return String.join(" ", notices) + " " + draft;
    }

    private String formatByChannel(String draft, String channel, List<String> riskFlags) {
        String cautionLine = riskFlags.isEmpty() ? ""
                : "\n- 주의: " + String.join(", ", riskFlags);

        return switch (channel) {
            case "messenger" -> "[요약]\n" + draft + cautionLine + "\n\n필요하시면 바로 조건별 체크리스트로 정리해드릴게요.";
            default -> "안녕하세요. Bio-Rad CS팀입니다.\n\n" + draft + cautionLine
                    + "\n\n추가로 샘플 조건(전처리/장비 설정)을 알려주시면 더 정확히 안내드리겠습니다.\n감사합니다.";
        };
    }
}
