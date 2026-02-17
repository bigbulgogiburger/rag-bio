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

          <hr className="divider" />

          {/* Verdict & Confidence metrics */}
          <div className="metrics-grid cols-3">
            <div className="metric-card">
              <p className="metric-label">판정</p>
              <div className="metric-value" style={{ fontSize: "var(--font-size-lg)" }}>
                <Badge variant={getVerdictBadgeVariant(analysisResult.verdict)}>
                  {labelVerdict(analysisResult.verdict)}
                </Badge>
              </div>
            </div>
            <div className="metric-card">
              <p className="metric-label">신뢰도</p>
              <p className="metric-value">{analysisResult.confidence}</p>
            </div>
            <div className="metric-card">
              <p className="metric-label">근거 수</p>
              <p className="metric-value">{analysisResult.evidences.length}</p>
              <p className="metric-sub">검색된 근거</p>
            </div>
          </div>

          {/* Reason */}
          <div className="draft-box">
            <b>사유:</b> {analysisResult.reason}
          </div>

          {/* Risk Flags */}
          {analysisResult.riskFlags.length > 0 && (
            <div className="status-banner status-warn">
              <b>리스크 플래그:</b>{" "}
              {analysisResult.riskFlags.map((flag, idx) => (
                <Badge key={idx} variant="warn" style={{ marginLeft: idx > 0 ? "0.25rem" : "0.5rem" }}>
                  {labelRiskFlag(flag)}
                </Badge>
              ))}
            </div>
          )}

          {/* Evidence Items */}
          {analysisResult.evidences.length > 0 && (
            <div className="stack">
              <h4 className="section-title">근거 목록 ({analysisResult.evidences.length}건)</h4>
              <div className="stack">
                {analysisResult.evidences.map((ev) => (
                  <div key={ev.chunkId} className="evidence-item">
                    <div className="row" style={{ alignItems: "center", gap: "var(--space-sm)" }}>
                      {ev.sourceType && (
                        <Badge variant={ev.sourceType === "KNOWLEDGE_BASE" ? "info" : "neutral"}>
                          {ev.sourceType === "KNOWLEDGE_BASE" ? "지식 기반" : "문의 첨부"}
                        </Badge>
                      )}
                      <span style={{ fontWeight: 600, fontSize: "var(--font-size-sm)" }}>
                        {ev.fileName
                          ? `${ev.fileName}${
                              ev.pageStart != null
                                ? ev.pageEnd != null && ev.pageEnd !== ev.pageStart
                                  ? ` (p.${ev.pageStart}-${ev.pageEnd})`
                                  : ` (p.${ev.pageStart})`
                                : ""
                            }`
                          : `청크 ${ev.chunkId.slice(0, 8)}`}
                      </span>
                      <span className="muted" style={{ fontSize: "var(--font-size-xs)" }}>
                        유사도 {(ev.score * 100).toFixed(1)}%
                      </span>
                    </div>
                    <p className="muted" style={{ margin: "0.35rem 0 0", fontSize: "var(--font-size-sm)", lineHeight: 1.6 }}>
                      {ev.excerpt}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
