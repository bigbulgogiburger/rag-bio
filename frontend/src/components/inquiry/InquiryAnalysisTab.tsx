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
import { Button } from "@/components/ui/button";

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
    <div className="space-y-6">
      <div className="rounded-xl border bg-card p-6 shadow-sm space-y-4">
        <h3 className="text-base font-semibold">근거 검색 + 판정</h3>

        <label className="space-y-1.5 text-sm font-medium">
          분석 질문
          <textarea
            className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            rows={3}
            value={analysisQuestion}
            onChange={(e) => setAnalysisQuestion(e.target.value)}
            placeholder="예: 해당 프로토콜이 타당한가요?"
          />
        </label>

        <Button
          onClick={handleAnalyze}
          disabled={loading}
          aria-busy={loading}
        >
          {loading && <svg className="mr-2 h-4 w-4 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-hidden="true"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" /><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" /></svg>}
          {loading ? "분석 중..." : "근거 검색 + 판정"}
        </Button>

        {error && (
          <p className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive" role="alert">
            {error}
          </p>
        )}
      </div>

      {analysisResult && (
        <div className="rounded-xl border bg-card p-6 shadow-sm space-y-4" aria-live="polite">
          <h3 className="text-base font-semibold">분석 결과</h3>

          <hr className="border-t border-border" />

          {/* Verdict & Confidence metrics */}
          <div className="grid grid-cols-3 gap-4">
            <div className="rounded-xl border bg-card p-5 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">판정</p>
              <div className="text-lg font-bold tracking-tight text-foreground">
                <Badge variant={getVerdictBadgeVariant(analysisResult.verdict)}>
                  {labelVerdict(analysisResult.verdict)}
                </Badge>
              </div>
            </div>
            <div className="rounded-xl border bg-card p-5 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">신뢰도</p>
              <p className="text-2xl font-bold tracking-tight text-foreground">{analysisResult.confidence}</p>
            </div>
            <div className="rounded-xl border bg-card p-5 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">근거 수</p>
              <p className="text-2xl font-bold tracking-tight text-foreground">{analysisResult.evidences.length}</p>
              <p className="text-xs text-muted-foreground">검색된 근거</p>
            </div>
          </div>

          {/* Reason */}
          <div className="rounded-lg border bg-muted/20 p-4 text-sm leading-relaxed">
            <b>사유:</b> {analysisResult.reason}
          </div>

          {/* Risk Flags */}
          {analysisResult.riskFlags.length > 0 && (
            <div className="rounded-lg border border-warning/30 bg-warning-light px-4 py-3 text-sm text-warning-foreground">
              <b>리스크 플래그:</b>{" "}
              {analysisResult.riskFlags.map((flag, idx) => (
                <span key={idx} className={idx > 0 ? "ml-1" : "ml-2"}>
                  <Badge variant="warn">
                    {labelRiskFlag(flag)}
                  </Badge>
                </span>
              ))}
            </div>
          )}

          {/* Evidence Items */}
          {analysisResult.evidences.length > 0 && (
            <div className="space-y-6">
              <h4 className="text-base font-semibold">근거 목록 ({analysisResult.evidences.length}건)</h4>
              <div className="space-y-6">
                {analysisResult.evidences.map((ev) => (
                  <div key={ev.chunkId} className="rounded-lg border border-border/50 bg-muted/30 p-4">
                    <div className="flex items-center gap-2">
                      {ev.sourceType && (
                        <Badge variant={ev.sourceType === "KNOWLEDGE_BASE" ? "info" : "neutral"}>
                          {ev.sourceType === "KNOWLEDGE_BASE" ? "지식 기반" : "문의 첨부"}
                        </Badge>
                      )}
                      <span className="text-sm font-semibold">
                        {ev.fileName
                          ? `${ev.fileName}${
                              ev.pageStart != null
                                ? ev.pageEnd != null && ev.pageEnd !== ev.pageStart
                                  ? ` (p.${ev.pageStart}-${ev.pageEnd})`
                                  : ` (p.${ev.pageStart})`
                                : ""
                            }`
                          : ev.documentId
                            ? `문서 ${ev.documentId.slice(0, 8)}`
                            : `청크 ${ev.chunkId.slice(0, 8)}`}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        유사도 {(ev.score * 100).toFixed(1)}%
                      </span>
                    </div>
                    <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
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
