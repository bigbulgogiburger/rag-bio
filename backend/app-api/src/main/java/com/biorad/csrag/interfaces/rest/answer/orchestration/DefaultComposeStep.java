package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DefaultComposeStep implements ComposeStep {

    @Override
    public ComposeStepResult execute(AnalyzeResponse analysis, String tone, String channel) {
        String normalizedTone = (tone == null || tone.isBlank()) ? "professional" : tone.trim().toLowerCase();
        String normalizedChannel = (channel == null || channel.isBlank()) ? "email" : channel.trim().toLowerCase();

        String draft = createDraftByTone(analysis, normalizedTone);
        draft = applyGuardrails(draft, analysis.confidence(), analysis.riskFlags());
        draft = formatByChannel(draft, normalizedChannel, analysis.riskFlags());

        return new ComposeStepResult(draft, validateFormatWarnings(draft, normalizedChannel));
    }

    private List<String> validateFormatWarnings(String draft, String channel) {
        String text = draft == null ? "" : draft;
        List<String> warnings = new ArrayList<>();

        if ("email".equals(channel)) {
            if (!text.contains("안녕하세요")) warnings.add("EMAIL_GREETING_MISSING");
            if (!text.contains("감사합니다")) warnings.add("EMAIL_CLOSING_MISSING");
        } else if ("messenger".equals(channel)) {
            if (!text.contains("[요약]")) warnings.add("MESSENGER_SUMMARY_TAG_MISSING");
            if (text.length() > 260) warnings.add("MESSENGER_LENGTH_OVERFLOW");
        }
        return warnings;
    }

    private String createDraftByTone(AnalyzeResponse analysis, String tone) {
        return switch (tone) {
            case "brief" -> switch (analysis.verdict()) {
                case "SUPPORTED" -> "사내 자료를 참고한 결과, 문의 내용은 근거 기준으로 타당한 것으로 판단됩니다.\n적용 전 핵심 조건에 대한 최종 점검을 권장드립니다.";
                case "REFUTED" -> "사내 자료를 참고한 결과, 해당 내용은 근거 기준으로 권장되지 않는 것으로 확인됩니다.\n조건 재설정 또는 대안 프로토콜 적용을 검토하여 주시기 바랍니다.";
                default -> "사내 자료를 참고한 결과, 조건 의존성이 있어 단정이 어려운 상황입니다.\n추가 확인 후 재판정이 필요할 것으로 판단됩니다.";
            };
            case "technical" -> switch (analysis.verdict()) {
                case "SUPPORTED" -> "사내 자료를 참고한 결과, 현재 검색된 근거 기준으로 문의 내용과 문서 간 일치도가 높은 것으로 확인됩니다.\n다만, 실제 실행 전 샘플 전처리 조건, 장비 파라미터, QC 기준에 대한 교차 검증을 권장드립니다.";
                case "REFUTED" -> "사내 자료를 참고한 결과, 현재 근거 점수 및 위험 신호 기준으로 문의 내용은 문서 근거와 충돌하는 것으로 확인됩니다.\n프로토콜 파라미터를 재검토하시고, 대체 워크플로우 적용을 검토하여 주시기 바랍니다.";
                default -> "사내 자료를 참고한 결과, 근거 간 상충 또는 신뢰도 부족이 감지되었습니다.\n추가 데이터 확보(샘플 조건, 장비 설정, 대조군 결과) 후 재평가를 권장드립니다.";
            };
            default -> switch (analysis.verdict()) {
                case "SUPPORTED" -> "문의하여 주신 내용에 대하여 확인한 결과를 안내드립니다.\n\n사내 자료를 참고한 결과, 문의 내용은 현재 확보된 근거 기준으로 타당한 방향으로 판단됩니다.\n다만, 실제 적용 전 샘플 조건과 장비 설정에 대한 최종 점검을 권장드립니다.";
                case "REFUTED" -> "문의하여 주신 내용에 대하여 확인한 결과를 안내드립니다.\n\n사내 자료를 참고한 결과, 문의 내용은 현재 근거 기준으로 권장되지 않는 것으로 확인됩니다.\n대안 프로토콜 또는 조건 재설정을 검토하여 주시기 바랍니다.";
                default -> "문의하여 주신 내용에 대하여 확인한 결과를 안내드립니다.\n\n사내 자료를 참고한 결과, 일부 근거가 확인되었으나 조건 의존성이 있어 단정이 어려운 상황입니다.\n아래 확인 항목을 점검하신 뒤 재판정을 요청하여 주시기 바랍니다.";
            };
        };
    }

    private String applyGuardrails(String draft, double confidence, List<String> riskFlags) {
        List<String> notices = new ArrayList<>();
        if (confidence < 0.75) notices.add("현재 근거 신뢰도가 충분히 높지 않아 추가 확인이 필요한 점 안내드립니다.");
        if (!riskFlags.isEmpty()) notices.add("일부 위험 신호가 감지되어, 단정적 결론 대신 보수적으로 안내드립니다.");
        return notices.isEmpty() ? draft : String.join("\n", notices) + "\n\n" + draft;
    }

    private String formatByChannel(String draft, String channel, List<String> riskFlags) {
        String cautionLine = riskFlags.isEmpty() ? "" : "\n\n참고로, " + String.join(", ", riskFlags) + " 항목이 감지되었습니다.";
        return switch (channel) {
            case "messenger" -> "[요약]\n" + draft + cautionLine + "\n\n추가 확인이 필요하시면 말씀하여 주시기 바랍니다.";
            default -> "안녕하세요.\nBio-Rad CS 기술지원팀입니다.\n\n" + draft + cautionLine + "\n\n추가 확인이 필요하신 사항이 있으시면 말씀하여 주시기 바랍니다.\n감사합니다.";
        };
    }
}
