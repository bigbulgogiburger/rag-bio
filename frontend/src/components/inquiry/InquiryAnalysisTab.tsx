"use client";

import { useState } from "react";
import {
  analyzeInquiry,
  type AnalyzeResult,
} from "@/lib/api/client";
import {
  labelVerdict,
  labelRiskFlag,
} from "@/lib/i18n/labels";
import { Badge } from "@/components/ui";

interface InquiryAnalysisTabProps {
  inquiryId: string;
}

export default function InquiryAnalysisTab({ inquiryId }: InquiryAnalysisTabProps) {
  const [analysisQuestion, setAnalysisQuestion] = useState("");
  const [analysisResult, setAnalysisResult] = useState<AnalyzeResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleAnalyze = async () => {
    if (!analysisQuestion.trim()) {
      setError("분석 질문을 입력해 주세요.");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const result = await analyzeInquiry(inquiryId, analysisQuestion.trim(), 5);
      setAnalysisResult(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "분석 중 오류가 발생했습니다.");
      setAnalysisResult(null);
    } finally {
      setLoading(false);
    }
  };

  const getVerdictBadgeVariant = (verdict: string): "info" | "success" | "warn" | "danger" | "neutral" => {
    if (verdict === "SUPPORTED") return "success";
    if (verdict === "NOT_SUPPORTED") return "danger";
    if (verdict === "CONDITIONAL") return "warn";
    return "info";
  };

  return (
    <div className="stack">
      <div className="card stack">
        <h3 className="section-title">근거 검색 + 판정</h3>

        <label className="label">
          분석 질문
          <textarea
            className="textarea"
            rows={3}
            value={analysisQuestion}
            onChange={(e) => setAnalysisQuestion(e.target.value)}
            placeholder="예: 해당 프로토콜이 타당한가요?"
          />
        </label>

        <button
          className="btn btn-primary"
          onClick={handleAnalyze}
          disabled={loading}
        >
          {loading ? "분석 중..." : "근거 검색 + 판정"}
        </button>

        {error && (
          <p className="status-banner status-danger" role="alert">
            {error}
          </p>
        )}
      </div>

      {analysisResult && (
        <div className="card stack">
          <h3 className="section-title">분석 결과</h3>

          <div className="kv">
            <div>
              <b>판정:</b>{" "}
              <Badge variant={getVerdictBadgeVariant(analysisResult.verdict)}>
                {labelVerdict(analysisResult.verdict)}
              </Badge>
            </div>
            <div>
              <b>신뢰도:</b> {analysisResult.confidence}
            </div>
            <div>
              <b>사유:</b> {analysisResult.reason}
            </div>
            {analysisResult.riskFlags.length > 0 && (
              <div>
                <b>리스크 플래그:</b>{" "}
                {analysisResult.riskFlags.map((flag, idx) => (
                  <Badge key={idx} variant="warn" style={{ marginRight: "0.5rem" }}>
                    {labelRiskFlag(flag)}
                  </Badge>
                ))}
              </div>
            )}
          </div>

          {analysisResult.evidences.length > 0 && (
            <div style={{ marginTop: "var(--space-md)" }}>
              <h4 className="section-title">근거 목록 ({analysisResult.evidences.length}건)</h4>
              <ul style={{ margin: "var(--space-sm) 0 0", paddingLeft: "var(--space-lg)" }}>
                {analysisResult.evidences.map((ev) => (
                  <li key={ev.chunkId} style={{ marginBottom: "var(--space-sm)" }}>
                    <div>
                      {ev.sourceType && (
                        <Badge
                          variant={ev.sourceType === "KNOWLEDGE_BASE" ? "info" : "neutral"}
                          style={{ marginRight: "var(--space-xs)" }}
                        >
                          {ev.sourceType === "KNOWLEDGE_BASE" ? "[지식 기반]" : "[문의 첨부]"}
                        </Badge>
                      )}
                      <b>점수:</b> {ev.score.toFixed(3)} |{" "}
                      <b>청크:</b> {ev.chunkId.slice(0, 8)}...
                    </div>
                    <div style={{ color: "var(--color-text-secondary)", fontSize: "var(--font-size-sm)" }}>
                      {ev.excerpt}
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
