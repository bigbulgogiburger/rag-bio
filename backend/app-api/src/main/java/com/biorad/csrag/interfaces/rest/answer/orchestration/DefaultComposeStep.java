package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DefaultComposeStep implements ComposeStep {

    @Override
    public ComposeStepResult execute(AnalyzeResponse analysis, String tone, String channel) {
        return execute(analysis, tone, channel, null, null);
    }

    @Override
    public ComposeStepResult execute(AnalyzeResponse analysis, String tone, String channel,
                                      String additionalInstructions, String previousAnswerDraft) {
        String normalizedTone = (tone == null || tone.isBlank()) ? "gilseon" : tone.trim().toLowerCase();
        String normalizedChannel = (channel == null || channel.isBlank()) ? "email" : channel.trim().toLowerCase();

        String draft;
        if (previousAnswerDraft != null && !previousAnswerDraft.isBlank()
                && additionalInstructions != null && !additionalInstructions.isBlank()) {
            draft = createRefinedDraft(previousAnswerDraft, additionalInstructions, analysis, normalizedTone, normalizedChannel);
        } else {
            draft = createDraftByTone(analysis, normalizedTone);
            draft = insertCitations(draft, analysis.evidences());
            draft = applyGuardrails(draft, analysis.confidence(), analysis.riskFlags());
            draft = formatByChannel(draft, normalizedChannel, analysis.riskFlags(), normalizedTone);
        }

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

    private String insertCitations(String draft, List<EvidenceItem> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return draft;
        }

        // Cluster evidences by documentId to merge duplicate document references
        List<EvidenceItem> clustered = clusterEvidences(evidences);

        List<String> citations = clustered.stream()
                .filter(ev -> ev.fileName() != null)
                .map(this::formatCitation)
                .toList();

        if (citations.isEmpty()) {
            return draft;
        }

        String citationText = " (" + String.join("; ", citations) + ")";
        // "사내 자료를 참고한 결과" 뒤에 citation 삽입
        int idx = draft.indexOf("사내 자료를 참고한 결과");
        if (idx >= 0) {
            int insertPos = idx + "사내 자료를 참고한 결과".length();
            return draft.substring(0, insertPos) + citationText + draft.substring(insertPos);
        }

        return draft;
    }

    /**
     * Cluster evidences by documentId: merge page ranges from the same document.
     */
    List<EvidenceItem> clusterEvidences(List<EvidenceItem> evidences) {
        if (evidences == null || evidences.size() <= 1) {
            return evidences == null ? List.of() : evidences;
        }

        // Group by documentId
        Map<String, List<EvidenceItem>> byDoc = new LinkedHashMap<>();
        for (EvidenceItem ev : evidences) {
            String key = ev.documentId() != null ? ev.documentId() : ev.chunkId();
            byDoc.computeIfAbsent(key, k -> new ArrayList<>()).add(ev);
        }

        List<EvidenceItem> result = new ArrayList<>();
        for (List<EvidenceItem> group : byDoc.values()) {
            if (group.size() == 1) {
                result.add(group.getFirst());
                continue;
            }

            // Merge: take the highest-scoring item, expand page range
            EvidenceItem best = group.stream()
                    .max(Comparator.comparingDouble(EvidenceItem::score))
                    .orElse(group.getFirst());

            Integer minPage = group.stream()
                    .map(EvidenceItem::pageStart)
                    .filter(Objects::nonNull)
                    .min(Integer::compareTo)
                    .orElse(best.pageStart());

            Integer maxPage = group.stream()
                    .map(EvidenceItem::pageEnd)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(best.pageEnd());

            // Merge excerpts: take unique key info
            String mergedExcerpt = group.stream()
                    .map(EvidenceItem::excerpt)
                    .filter(Objects::nonNull)
                    .distinct()
                    .reduce((a, b) -> a + " | " + b)
                    .orElse(best.excerpt());

            result.add(new EvidenceItem(
                    best.chunkId(), best.documentId(), best.score(),
                    mergedExcerpt, best.sourceType(), best.fileName(),
                    minPage, maxPage
            ));
        }
        return result;
    }

    private String formatCitation(EvidenceItem ev) {
        StringBuilder sb = new StringBuilder(ev.fileName());
        if (ev.pageStart() != null) {
            sb.append(", p.").append(ev.pageStart());
            if (ev.pageEnd() != null && !ev.pageEnd().equals(ev.pageStart())) {
                sb.append("-").append(ev.pageEnd());
            }
        }
        return sb.toString();
    }

    private String createRefinedDraft(String previousDraft, String instructions,
                                        AnalyzeResponse analysis, String tone, String channel) {
        StringBuilder sb = new StringBuilder();

        // 보완 안내 헤더
        if ("gilseon".equals(tone)) {
            sb.append("문의하여 주신 내용에 대하여 보완 확인한 결과를 하기와 같이 안내드립니다.\n\n");
        } else {
            sb.append("이전 답변을 보완하여 안내드립니다.\n\n");
        }

        // 보완 지시사항 반영
        sb.append("[보완 사항]\n");
        sb.append(instructions.trim());
        sb.append("\n\n");

        // 기존 답변 본문 유지 (채널 래핑 제거 후 핵심만 추출)
        String core = extractCoreContent(previousDraft);
        sb.append("[기존 답변 기반 내용]\n");
        sb.append(core);

        // 새 근거 기반 추가 내용
        if (analysis.evidences() != null && !analysis.evidences().isEmpty()) {
            sb.append("\n\n[추가 확인된 근거]\n");
            sb.append("사내 자료를 참고한 결과, 추가로 확인된 내용을 포함하여 안내드립니다.");
            String citationDraft = insertCitations("사내 자료를 참고한 결과", analysis.evidences());
            sb.append("\n").append(citationDraft);
        }

        String draft = sb.toString();
        draft = applyGuardrails(draft, analysis.confidence(), analysis.riskFlags());
        draft = formatByChannel(draft, channel, analysis.riskFlags(), tone);
        return draft;
    }

    private String extractCoreContent(String previousDraft) {
        String core = previousDraft;
        // 이메일 인사/마무리 래핑 제거
        String[] greetings = {"안녕하세요\n한국바이오래드 차길선 입니다.\n\n",
                "안녕하세요.\nBio-Rad CS 기술지원팀입니다.\n\n"};
        for (String g : greetings) {
            if (core.startsWith(g)) {
                core = core.substring(g.length());
                break;
            }
        }
        String[] closings = {"\n\n추가 문의 건 회신 주십시오.\n\n감사합니다.\n차길선 드림.",
                "\n\n추가 확인이 필요하신 사항이 있으시면 말씀하여 주시기 바랍니다.\n감사합니다.",
                "\n\n추가 확인이 필요하시면 말씀하여 주시기 바랍니다."};
        for (String c : closings) {
            if (core.endsWith(c)) {
                core = core.substring(0, core.length() - c.length());
                break;
            }
        }
        // 메신저 래핑 제거
        if (core.startsWith("[요약]\n")) {
            core = core.substring("[요약]\n".length());
        }
        return core.trim();
    }

    private String createDraftByTone(AnalyzeResponse analysis, String tone) {
        return switch (tone) {
            case "brief" -> switch (analysis.verdict()) {
                case "SUPPORTED" -> "사내 자료를 참고한 결과, 문의 내용은 근거 기준으로 타당한 것으로 판단됩니다.\n적용 전 핵심 조건에 대한 최종 점검을 권장드립니다.";
                case "REFUTED" -> "사내 자료를 참고한 결과, 해당 내용은 근거 기준으로 권장되지 않는 것으로 확인됩니다.\n조건 재설정 또는 대안 프로토콜 적용을 검토하여 주시기 바랍니다.";
                default -> "사내 자료를 참고한 결과, 관련 사내 자료를 바탕으로 확인한 내용을 안내드립니다.\n확인된 사항을 안내드리며, 추가 정보가 필요한 부분이 있습니다.";
            };
            case "technical" -> switch (analysis.verdict()) {
                case "SUPPORTED" -> "사내 자료를 참고한 결과, 현재 검색된 근거 기준으로 문의 내용과 문서 간 일치도가 높은 것으로 확인됩니다.\n다만, 실제 실행 전 샘플 전처리 조건, 장비 파라미터, QC 기준에 대한 교차 검증을 권장드립니다.";
                case "REFUTED" -> "사내 자료를 참고한 결과, 현재 근거 점수 및 위험 신호 기준으로 문의 내용은 문서 근거와 충돌하는 것으로 확인됩니다.\n프로토콜 파라미터를 재검토하시고, 대체 워크플로우 적용을 검토하여 주시기 바랍니다.";
                default -> "사내 자료를 참고한 결과, 관련 사내 자료를 바탕으로 확인한 결과를 안내드립니다.\n확인된 내용을 안내드리며, 추가 확인이 필요한 사항이 있습니다.";
            };
            case "gilseon" -> switch (analysis.verdict()) {
                case "SUPPORTED" -> "문의하여 주신 내용에 대하여 확인한 결과를 하기와 같이 안내드립니다.\n\n사내 자료를 참고한 결과, 문의 내용은 현재 확보된 근거 기준으로 타당한 방향으로 판단됩니다.\n\n#1) 사내 자료 기반 확인 결과\n문의 주신 부분에 대하여 근거 자료가 확인되었으며, 해당 내용 바탕으로 진행에 문제가 없는 것을 확인했습니다.\n\n#2) 추가 확인 사항\n다만, 실제 적용 전 샘플 조건과 장비 설정에 대한 최종 점검을 권장드립니다.\n좀 더 구체적인 확인이 필요하시다면 유선으로 말씀해주십시오.";
                case "REFUTED" -> "문의하여 주신 내용에 대하여 확인한 결과를 하기와 같이 안내드립니다.\n\n사내 자료를 참고한 결과, 문의 내용은 현재 근거 기준으로 권장되지 않는 것으로 확인됩니다.\n\n#1) 확인 결과\n해당 조건에서는 기대하시는 결과를 얻기 어려울 것으로 예상합니다.\n\n#2) 대안 제안\n대안 프로토콜 또는 조건 재설정을 검토하여 주시기 바랍니다.\n해당 건에 대해서 좀 더 자세한 안내가 필요하시면 회신 주십시오.";
                default -> "문의하여 주신 내용에 대하여 확인한 결과를 하기와 같이 안내드립니다.\n\n사내 자료를 참고한 결과, 관련 사내 자료를 바탕으로 확인한 내용을 안내드립니다.\n\n#1) 확인 결과\n현재까지 확인된 근거 바탕으로 말씀드리자면, 확인된 내용을 바탕으로 안내드리며, 추가 확인이 필요한 부분은 하기와 같습니다.\n\n#2) 추가 확인 필요 항목\n추가로 확인이 필요한 부분은 하기와 같습니다.";
            };
            default -> switch (analysis.verdict()) {
                case "SUPPORTED" -> "문의하여 주신 내용에 대하여 확인한 결과를 안내드립니다.\n\n사내 자료를 참고한 결과, 문의 내용은 현재 확보된 근거 기준으로 타당한 방향으로 판단됩니다.\n다만, 실제 적용 전 샘플 조건과 장비 설정에 대한 최종 점검을 권장드립니다.";
                case "REFUTED" -> "문의하여 주신 내용에 대하여 확인한 결과를 안내드립니다.\n\n사내 자료를 참고한 결과, 문의 내용은 현재 근거 기준으로 권장되지 않는 것으로 확인됩니다.\n대안 프로토콜 또는 조건 재설정을 검토하여 주시기 바랍니다.";
                default -> "문의하여 주신 내용에 대하여 확인한 결과를 안내드립니다.\n\n사내 자료를 참고한 결과, 관련 사내 자료를 바탕으로 확인한 내용을 안내드립니다.\n확인된 내용을 안내드리며, 추가 확인이 필요한 사항이 있습니다.";
            };
        };
    }

    private String applyGuardrails(String draft, double confidence, List<String> riskFlags) {
        List<String> notices = new ArrayList<>();
        boolean hasSafetyOrRegulatory = riskFlags.stream()
                .anyMatch(f -> "SAFETY_CONCERN".equals(f) || "REGULATORY_RISK".equals(f));
        if (hasSafetyOrRegulatory) {
            notices.add("안전 또는 규제 관련 위험 신호가 감지되어 보수적으로 안내드립니다.");
        }
        return notices.isEmpty() ? draft : String.join("\n", notices) + "\n\n" + draft;
    }

    private String formatByChannel(String draft, String channel, List<String> riskFlags, String tone) {
        boolean showCaution = riskFlags.stream()
                .anyMatch(f -> "SAFETY_CONCERN".equals(f) || "REGULATORY_RISK".equals(f));
        String cautionLine = showCaution ? "\n\n참고로, 안전/규제 관련 위험 신호가 감지되었습니다." : "";
        return switch (channel) {
            case "messenger" -> "[요약]\n" + draft + cautionLine + "\n\n추가 확인이 필요하시면 말씀하여 주시기 바랍니다.";
            default -> {
                if ("gilseon".equals(tone)) {
                    yield "안녕하세요\n한국바이오래드 차길선 입니다.\n\n" + draft + cautionLine + "\n\n추가 문의 건 회신 주십시오.\n\n감사합니다.\n차길선 드림.";
                } else {
                    yield "안녕하세요.\nBio-Rad CS 기술지원팀입니다.\n\n" + draft + cautionLine + "\n\n추가 확인이 필요하신 사항이 있으시면 말씀하여 주시기 바랍니다.\n감사합니다.";
                }
            }
        };
    }
}
