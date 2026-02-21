"use client";

import { useEffect, useState, type ReactNode } from "react";
import dynamic from "next/dynamic";
import {
  listAnswerDraftHistory,
  getDocumentDownloadUrl,
  getDocumentPagesUrl,
  draftInquiryAnswer,
  reviewAnswerDraft,
  approveAnswerDraft,
  sendAnswerDraft,
  autoWorkflow,
  type AnswerDraftResult,
  type AnalyzeEvidenceItem,
  type AutoWorkflowResult,
  type ReviewIssue,
} from "@/lib/api/client";
import {
  labelAnswerStatus,
  labelVerdict,
  labelChannel,
  labelTone,
  labelApprovalDecision,
  labelReviewDecision,
  labelIssueSeverity,
  labelIssueCategory,
} from "@/lib/i18n/labels";
import { DataTable, Badge, EmptyState, Skeleton } from "@/components/ui";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { showToast } from "@/lib/toast";

const PdfViewer = dynamic(() => import("@/components/ui/PdfViewer"), {
  ssr: false,
  loading: () => <div className="flex items-center justify-center p-8 text-sm text-muted-foreground" role="status" aria-live="polite">PDF 뷰어 로딩 중...</div>,
});

interface CitationView {
  chunkId: string;
  score: number | null;
  documentId: string | null;
  fileName: string | null;
  pageStart: number | null;
  pageEnd: number | null;
}

function parseCitation(raw: string): CitationView {
  const chunkMatch = raw.match(/chunk=([^\s]+)/);
  const scoreMatch = raw.match(/score=([0-9.]+)/);
  const docMatch = raw.match(/documentId=([^\s]+)/);
  // fileName은 공백을 포함할 수 있으므로, 다음 key= 패턴이나 문자열 끝까지 매칭
  const fileMatch = raw.match(/fileName=(.+?)(?=\s+\w+=|$)/);
  const psMatch = raw.match(/pageStart=(\d+)/);
  const peMatch = raw.match(/pageEnd=(\d+)/);
  return {
    chunkId: chunkMatch?.[1] ?? raw,
    score: scoreMatch ? Number(scoreMatch[1]) : null,
    documentId: docMatch?.[1] ?? null,
    fileName: fileMatch?.[1]?.trim() ?? null,
    pageStart: psMatch ? Number(psMatch[1]) : null,
    pageEnd: peMatch ? Number(peMatch[1]) : null,
  };
}

function formatCitationLabel(c: CitationView): string {
  if (c.fileName) {
    const pageStr =
      c.pageStart != null
        ? c.pageEnd != null && c.pageEnd !== c.pageStart
          ? ` (p.${c.pageStart}-${c.pageEnd})`
          : ` (p.${c.pageStart})`
        : "";
    return `${c.fileName}${pageStr}`;
  }
  if (c.documentId) {
    return `문서 ${c.documentId.slice(0, 8)}`;
  }
  return `청크 ${c.chunkId.slice(0, 8)}`;
}

function isPdf(fileName: string | null): boolean {
  return fileName != null && fileName.toLowerCase().endsWith(".pdf");
}

function evidenceToCitationView(ev: AnalyzeEvidenceItem): CitationView {
  return {
    chunkId: ev.chunkId,
    score: ev.score,
    documentId: ev.documentId,
    fileName: ev.fileName ?? null,
    pageStart: ev.pageStart ?? null,
    pageEnd: ev.pageEnd ?? null,
  };
}

interface InquiryHistoryTabProps {
  inquiryId: string;
  inquiryQuestion?: string;
}

const SpinnerIcon = () => (
  <svg className="h-4 w-4 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-hidden="true">
    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
  </svg>
);

function getSeverityIcon(severity: string): string {
  switch (severity) {
    case "CRITICAL": return "\u{1F534}";
    case "HIGH": return "\u{1F7E0}";
    case "MEDIUM": return "\u{1F7E1}";
    case "LOW": return "\u{1F535}";
    default: return "\u{26AA}";
  }
}

function getSeverityBadgeVariant(severity: string): "danger" | "warn" | "info" | "neutral" {
  switch (severity) {
    case "CRITICAL": return "danger";
    case "HIGH": return "danger";
    case "MEDIUM": return "warn";
    case "LOW": return "info";
    default: return "neutral";
  }
}

function getScoreBadgeVariant(score: number): "success" | "warn" | "danger" {
  if (score >= 90) return "success";
  if (score >= 70) return "warn";
  return "danger";
}

export default function InquiryHistoryTab({ inquiryId, inquiryQuestion }: InquiryHistoryTabProps) {
  const [history, setHistory] = useState<AnswerDraftResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<AnswerDraftResult | null>(null);
  const [selectedEvidence, setSelectedEvidence] = useState<CitationView | null>(null);

  // Workflow action states
  const [actionLoading, setActionLoading] = useState(false);
  const [workflowLoading, setWorkflowLoading] = useState(false);
  const [workflowResult, setWorkflowResult] = useState<AutoWorkflowResult | null>(null);
  const [reviewDetailExpanded, setReviewDetailExpanded] = useState(false);
  const [manualMode, setManualMode] = useState(false);
  const [reviewActor, setReviewActor] = useState("cs-agent");
  const [reviewComment, setReviewComment] = useState("");
  const [approveActor, setApproveActor] = useState("cs-lead");
  const [approveComment, setApproveComment] = useState("");

  // Refinement request
  const MAX_REFINEMENTS = 5;
  const [refinementInstructions, setRefinementInstructions] = useState("");

  useEffect(() => {
    fetchHistory();
  }, [inquiryId]);

  // Reset state when switching versions
  useEffect(() => {
    setSelectedEvidence(null);
    setWorkflowResult(null);
    setReviewDetailExpanded(false);
    setManualMode(false);
    setRefinementInstructions("");
  }, [selected]);

  const fetchHistory = async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await listAnswerDraftHistory(inquiryId);
      setHistory(data);
      if (data.length > 0) setSelected(data[0]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "버전 이력 조회 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const refreshAndSelect = async (answerId: string) => {
    const data = await listAnswerDraftHistory(inquiryId);
    setHistory(data);
    const updated = data.find((d) => d.answerId === answerId);
    if (updated) setSelected(updated);
  };

  const handleAutoWorkflow = async () => {
    if (!selected) return;
    setWorkflowLoading(true);
    try {
      const result = await autoWorkflow(inquiryId, selected.answerId);
      setWorkflowResult(result);
      await refreshAndSelect(selected.answerId);
      if (result.approval.decision === "AUTO_APPROVED") {
        showToast(`AI 리뷰 통과 (${result.review.score}점) - 자동 승인 완료`, "success");
      } else if (result.approval.decision === "ESCALATED") {
        showToast("AI 리뷰 완료 - 사람 확인이 필요합니다", "warn");
      } else {
        showToast("AI 리뷰 결과 답변 품질이 기준 미달입니다", "error");
      }
    } catch (err) {
      showToast(err instanceof Error ? err.message : "자동 워크플로우 처리 중 오류가 발생했습니다.", "error");
    } finally {
      setWorkflowLoading(false);
    }
  };

  const handleReview = async () => {
    if (!selected) return;
    setActionLoading(true);
    try {
      await reviewAnswerDraft(inquiryId, selected.answerId, reviewActor.trim() || undefined, reviewComment.trim() || undefined);
      await refreshAndSelect(selected.answerId);
      showToast("리뷰 완료", "success");
    } catch (err) {
      showToast(err instanceof Error ? err.message : "리뷰 처리 중 오류가 발생했습니다.", "error");
    } finally {
      setActionLoading(false);
    }
  };

  const handleApprove = async () => {
    if (!selected) return;
    setActionLoading(true);
    try {
      await approveAnswerDraft(inquiryId, selected.answerId, approveActor.trim() || undefined, approveComment.trim() || undefined);
      await refreshAndSelect(selected.answerId);
      showToast("승인 완료", "success");
    } catch (err) {
      showToast(err instanceof Error ? err.message : "승인 처리 중 오류가 발생했습니다.", "error");
    } finally {
      setActionLoading(false);
    }
  };

  const handleSend = async () => {
    if (!selected) return;
    setActionLoading(true);
    try {
      const sendRequestId = `${selected.answerId}-${Date.now()}`;
      await sendAnswerDraft(inquiryId, selected.answerId, "cs-sender", selected.channel, sendRequestId);
      await refreshAndSelect(selected.answerId);
      showToast("발송 완료", "success");
    } catch (err) {
      showToast(err instanceof Error ? err.message : "발송 처리 중 오류가 발생했습니다.", "error");
    } finally {
      setActionLoading(false);
    }
  };

  const handleRefineDraft = async () => {
    if (!selected || !refinementInstructions.trim()) return;
    setActionLoading(true);
    try {
      const draft = await draftInquiryAnswer(
        inquiryId,
        inquiryQuestion ?? "",
        selected.tone,
        selected.channel,
        refinementInstructions.trim(),
        selected.answerId
      );
      setRefinementInstructions("");
      // Refresh history and select new version
      const data = await listAnswerDraftHistory(inquiryId);
      setHistory(data);
      const newVersion = data.find((d) => d.answerId === draft.answerId);
      if (newVersion) setSelected(newVersion);
      showToast(`보완 답변 v${draft.version} 생성 완료`, "success");
    } catch (err) {
      showToast(err instanceof Error ? err.message : "보완 답변 생성 중 오류가 발생했습니다.", "error");
    } finally {
      setActionLoading(false);
    }
  };

  // Group issues by category
  const groupIssuesByCategory = (issues: ReviewIssue[]): Record<string, ReviewIssue[]> => {
    return issues.reduce<Record<string, ReviewIssue[]>>((acc, issue) => {
      const cat = issue.category;
      if (!acc[cat]) acc[cat] = [];
      acc[cat].push(issue);
      return acc;
    }, {});
  };

  // Build evidence list from selected version
  const evidenceItems: CitationView[] = selected
    ? selected.evidences
      ? selected.evidences.map(evidenceToCitationView)
      : selected.citations.map(parseCitation)
    : [];

  // Render answer body with clickable citation links
  const renderDraftWithCitations = (text: string): ReactNode => {
    const citationRegex = /\(([^,]+\.pdf),\s*p\.(\d+)(?:-(\d+))?\)/gi;
    const parts: ReactNode[] = [];
    let lastIndex = 0;
    let match: RegExpExecArray | null;

    while ((match = citationRegex.exec(text)) !== null) {
      if (match.index > lastIndex) {
        parts.push(text.slice(lastIndex, match.index));
      }

      const matchedFileName = match[1].trim();
      const pageStart = parseInt(match[2], 10);
      const pageEnd = match[3] ? parseInt(match[3], 10) : pageStart;
      const fullMatch = match[0];

      const matchingEvidence = evidenceItems.find((ev) => {
        if (!ev.fileName) return false;
        const nameMatch = ev.fileName.toLowerCase() === matchedFileName.toLowerCase();
        if (!nameMatch) return false;
        if (ev.pageStart == null) return true;
        return ev.pageStart === pageStart || (ev.pageStart <= pageStart && (ev.pageEnd ?? ev.pageStart) >= pageEnd);
      });

      if (matchingEvidence) {
        parts.push(
          <button
            key={`citation-${match.index}`}
            type="button"
            className="text-primary underline cursor-pointer hover:text-primary/80"
            onClick={() => setSelectedEvidence(matchingEvidence)}
          >
            {fullMatch}
          </button>
        );
      } else {
        parts.push(fullMatch);
      }

      lastIndex = match.index + fullMatch.length;
    }

    if (lastIndex < text.length) {
      parts.push(text.slice(lastIndex));
    }

    return parts.length > 0 ? parts : text;
  };

  const getAnswerStatusBadgeVariant = (status: string): "info" | "success" | "warn" | "danger" | "neutral" => {
    if (["SENT", "APPROVED"].includes(status)) return "success";
    if (status === "REVIEWED") return "warn";
    return "info";
  };

  const getVerdictBadgeVariant = (verdict: string): "info" | "success" | "warn" | "danger" | "neutral" => {
    if (verdict === "SUPPORTED") return "success";
    if (verdict === "NOT_SUPPORTED") return "danger";
    if (verdict === "CONDITIONAL") return "warn";
    return "info";
  };

  const columns = [
    {
      key: "version",
      header: "버전",
      render: (item: AnswerDraftResult) => `v${item.version}`,
      width: "80px",
    },
    {
      key: "status",
      header: "상태",
      render: (item: AnswerDraftResult) => (
        <span className="inline-flex items-center gap-1.5">
          <Badge variant={getAnswerStatusBadgeVariant(item.status)}>
            {labelAnswerStatus(item.status)}
          </Badge>
          {item.additionalInstructions && (
            <Badge variant="warn">보완</Badge>
          )}
        </span>
      ),
      width: "150px",
    },
    {
      key: "verdict",
      header: "판정",
      render: (item: AnswerDraftResult) => (
        <Badge variant={getVerdictBadgeVariant(item.verdict)}>
          {labelVerdict(item.verdict)}
        </Badge>
      ),
      width: "120px",
    },
    {
      key: "confidence",
      header: "신뢰도",
      render: (item: AnswerDraftResult) => item.confidence,
      width: "100px",
    },
    {
      key: "channel",
      header: "채널",
      render: (item: AnswerDraftResult) => labelChannel(item.channel),
      width: "100px",
    },
    {
      key: "tone",
      header: "톤",
      render: (item: AnswerDraftResult) => labelTone(item.tone),
      width: "100px",
    },
  ];

  if (loading) {
    return (
      <div className="space-y-6" role="status" aria-label="버전 이력 로딩 중">
        <Skeleton className="h-12" />
        <Skeleton className="h-[200px]" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-xl border bg-card p-6 shadow-sm">
        <p className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive" role="alert">
          {error}
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="rounded-xl border bg-card p-6 shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-base font-semibold">
            답변 버전 이력 ({history.length}건)
          </h3>
          <Button variant="ghost" size="sm" onClick={fetchHistory} disabled={loading}>
            새로고침
          </Button>
        </div>

        <hr className="border-t border-border" />

        <DataTable
          columns={columns}
          data={history}
          onRowClick={(item) => setSelected(item)}
          emptyMessage="생성된 답변 버전이 없습니다"
        />
      </div>

      {selected && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_400px]">
          {/* Left: Version detail */}
          <div>
            <div className="rounded-xl border bg-card p-6 shadow-sm space-y-4">
              <h3 className="text-base font-semibold">v{selected.version} 버전 상세</h3>

              <hr className="border-t border-border" />

              {/* Version summary metrics */}
              <div className="grid grid-cols-3 gap-4">
                <div className="rounded-xl border bg-card p-5 shadow-sm">
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">버전</p>
                  <p className="text-2xl font-bold tracking-tight text-foreground">v{selected.version}</p>
                </div>
                <div className="rounded-xl border bg-card p-5 shadow-sm">
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">상태</p>
                  <div className="text-lg font-bold tracking-tight text-foreground">
                    <Badge variant={getAnswerStatusBadgeVariant(selected.status)}>
                      {labelAnswerStatus(selected.status)}
                    </Badge>
                  </div>
                </div>
                <div className="rounded-xl border bg-card p-5 shadow-sm">
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">판정 / 신뢰도</p>
                  <div className="text-lg font-bold tracking-tight text-foreground">
                    <Badge variant={getVerdictBadgeVariant(selected.verdict)}>
                      {labelVerdict(selected.verdict)}
                    </Badge>
                    <span className="ml-2 text-sm text-muted-foreground">
                      ({selected.confidence})
                    </span>
                  </div>
                </div>
              </div>

              <div className="space-y-2 text-sm">
                <div>
                  <b>채널:</b> {labelChannel(selected.channel)} | <b>톤:</b> {labelTone(selected.tone)}
                </div>
              </div>

              {/* Refinement Instructions */}
              {selected.additionalInstructions && (
                <div className="space-y-2">
                  <h4 className="text-sm font-semibold">보완 요청 내용</h4>
                  <div className="rounded-lg border-l-4 border-amber-400 bg-amber-50/50 dark:bg-amber-950/20 px-4 py-3 text-sm leading-relaxed text-foreground/80">
                    {selected.additionalInstructions}
                  </div>
                  {selected.previousAnswerId && (
                    <p className="text-xs text-muted-foreground">
                      이전 버전:{" "}
                      <button
                        type="button"
                        className="text-primary underline hover:text-primary/80"
                        onClick={() => {
                          const prev = history.find((h) => h.answerId === selected.previousAnswerId);
                          if (prev) setSelected(prev);
                        }}
                      >
                        v{history.find((h) => h.answerId === selected.previousAnswerId)?.version ?? "?"}
                      </button>
                    </p>
                  )}
                </div>
              )}

              {/* Draft content */}
              {selected.draft && (
                <>
                  <hr className="border-t border-border" />
                  <h4 className="text-base font-semibold">답변 초안</h4>
                  <div className="rounded-lg border border-border/50 bg-muted/30 p-4 whitespace-pre-wrap text-sm leading-relaxed">
                    {renderDraftWithCitations(selected.draft)}
                  </div>
                </>
              )}

              {/* Citations - clickable */}
              {selected.citations && selected.citations.length > 0 && (
                <>
                  <hr className="border-t border-border" />
                  <h4 className="text-base font-semibold">참조 자료 ({selected.citations.length}건)</h4>
                  <div className="space-y-1">
                    {selected.citations.map((cite, i) => {
                      const parsed = parseCitation(cite);
                      const isSelected =
                        selectedEvidence?.chunkId === parsed.chunkId;
                      return (
                        <div
                          key={`${parsed.chunkId}-${i}`}
                          className={cn(
                            "rounded-lg border border-border/50 bg-muted/30 p-4 cursor-pointer transition-colors hover:border-primary/30 hover:bg-primary/5",
                            isSelected && "border-primary/50 bg-primary/5 ring-1 ring-primary/20"
                          )}
                          onClick={() => setSelectedEvidence(parsed)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter" || e.key === " ") {
                              e.preventDefault();
                              setSelectedEvidence(parsed);
                            }
                          }}
                          role="button"
                          tabIndex={0}
                          aria-label={`참조 자료 미리보기: ${formatCitationLabel(parsed)}`}
                          aria-pressed={isSelected}
                        >
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-medium">
                              {formatCitationLabel(parsed)}
                            </span>
                            {parsed.score != null && (
                              <span className="text-xs text-muted-foreground">
                                유사도 {(parsed.score * 100).toFixed(1)}%
                              </span>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </>
              )}

              {/* Workflow trail */}
              {(selected.reviewedBy || selected.approvedBy || selected.sentBy) && (
                <>
                  <hr className="border-t border-border" />
                  <h4 className="text-base font-semibold">워크플로우 이력</h4>
                  <div className="space-y-4">
                    {selected.reviewedBy && (
                      <div className="rounded-lg border border-border/50 bg-muted/30 p-4">
                        <span className="mr-2"><Badge variant="info">리뷰</Badge></span>
                        {selected.reviewedBy}
                        {selected.reviewComment && (
                          <span className="text-sm text-muted-foreground"> - {selected.reviewComment}</span>
                        )}
                      </div>
                    )}
                    {selected.approvedBy && (
                      <div className="rounded-lg border border-border/50 bg-muted/30 p-4">
                        <span className="mr-2"><Badge variant="success">승인</Badge></span>
                        {selected.approvedBy}
                        {selected.approveComment && (
                          <span className="text-sm text-muted-foreground"> - {selected.approveComment}</span>
                        )}
                      </div>
                    )}
                    {selected.sentBy && (
                      <div className="rounded-lg border border-border/50 bg-muted/30 p-4">
                        <span className="mr-2"><Badge variant="success">발송</Badge></span>
                        {selected.sentBy} | {selected.sendChannel}
                      </div>
                    )}
                  </div>
                </>
              )}

              {/* Refinement Request */}
              {selected.status !== "SENT" && (
                <>
                  <hr className="border-t border-border" />
                  <div className="space-y-3">
                    <div className="flex items-center justify-between">
                      <h4 className="text-base font-semibold">보완 요청</h4>
                      <span className="text-xs text-muted-foreground">
                        {selected.refinementCount ?? 0}/{MAX_REFINEMENTS}회 사용
                      </span>
                    </div>
                    <textarea
                      className="w-full min-h-[80px] rounded-lg border border-input bg-transparent p-3 text-sm leading-relaxed shadow-sm focus:outline-none focus:ring-2 focus:ring-ring"
                      value={refinementInstructions}
                      onChange={(e) => setRefinementInstructions(e.target.value)}
                      placeholder="추가 요청사항을 입력하세요 (예: 더 간결하게, 특정 제품에 대한 내용 추가, 톤 변경 등)"
                      aria-label="보완 요청사항 입력"
                      disabled={(selected.refinementCount ?? 0) >= MAX_REFINEMENTS || actionLoading}
                    />
                    <div className="flex items-center gap-3">
                      <Button
                        onClick={handleRefineDraft}
                        disabled={
                          actionLoading ||
                          !refinementInstructions.trim() ||
                          (selected.refinementCount ?? 0) >= MAX_REFINEMENTS
                        }
                        aria-busy={actionLoading}
                      >
                        {actionLoading && <SpinnerIcon />}
                        {actionLoading ? "보완 답변 생성 중..." : "보완 답변 생성"}
                      </Button>
                      {(selected.refinementCount ?? 0) >= MAX_REFINEMENTS && (
                        <p className="text-xs text-destructive">
                          보완 요청 횟수를 모두 사용했습니다
                        </p>
                      )}
                    </div>
                  </div>
                </>
              )}

              {/* Workflow Actions */}
              {selected.status !== "SENT" && (
                <>
                  <hr className="border-t border-border" />
                  <div className="space-y-4">
                    <h4 className="text-base font-semibold">워크플로우 액션</h4>

                    {/* AI Auto Workflow */}
                    {selected.status === "DRAFT" && !workflowResult && (
                      <div className="space-y-3">
                        <Button
                          onClick={handleAutoWorkflow}
                          disabled={workflowLoading || actionLoading}
                          aria-busy={workflowLoading}
                          className="w-full"
                        >
                          {workflowLoading && <SpinnerIcon />}
                          {workflowLoading ? "AI가 답변을 검토하고 있습니다..." : "자동 검토 + 승인 실행"}
                        </Button>

                        <button
                          onClick={() => setManualMode(!manualMode)}
                          className="text-xs text-muted-foreground hover:text-foreground hover:underline transition-colors"
                        >
                          {manualMode ? "자동 모드로 전환" : "수동 모드로 전환"}
                        </button>
                      </div>
                    )}

                    {/* AI Workflow Result */}
                    {workflowResult && (
                      <div className="space-y-4">
                        {workflowResult.approval.decision === "AUTO_APPROVED" && (
                          <div className="rounded-xl border border-emerald-300 bg-emerald-50 p-5 dark:border-emerald-800 dark:bg-emerald-950" role="status">
                            <div className="flex items-center gap-3">
                              <div className="flex-1">
                                <p className="font-semibold text-emerald-800 dark:text-emerald-200">
                                  AI 리뷰 통과 ({workflowResult.review.score}점) &rarr; 자동 승인 완료
                                </p>
                                <p className="text-sm text-emerald-700 dark:text-emerald-300">{workflowResult.summary}</p>
                              </div>
                              <Badge variant="success">{workflowResult.review.score}점</Badge>
                            </div>
                            <div className="mt-4 flex items-center gap-2">
                              <Button size="sm" onClick={handleSend} disabled={actionLoading}>
                                {actionLoading && <SpinnerIcon />}
                                발송하기
                              </Button>
                              <Button variant="outline" size="sm" onClick={() => setReviewDetailExpanded(!reviewDetailExpanded)}>
                                {reviewDetailExpanded ? "상세 리뷰 닫기" : "상세 리뷰 보기"}
                              </Button>
                            </div>
                          </div>
                        )}

                        {workflowResult.approval.decision === "ESCALATED" && (
                          <div className="rounded-xl border border-amber-300 bg-amber-50 p-5 dark:border-amber-800 dark:bg-amber-950" role="alert">
                            <div className="flex items-center gap-3">
                              <div className="flex-1">
                                <p className="font-semibold text-amber-800 dark:text-amber-200">
                                  {labelApprovalDecision("ESCALATED")}
                                </p>
                                <p className="text-sm text-amber-700 dark:text-amber-300">{workflowResult.approval.reason}</p>
                              </div>
                              <Badge variant="warn">{workflowResult.review.score}점</Badge>
                            </div>
                            <div className="mt-4 flex items-center gap-2">
                              <Button size="sm" onClick={handleApprove} disabled={actionLoading}>
                                {actionLoading && <SpinnerIcon />}
                                검토 후 승인
                              </Button>
                              <Button variant="ghost" size="sm" onClick={() => setReviewDetailExpanded(!reviewDetailExpanded)}>
                                {reviewDetailExpanded ? "상세 리뷰 닫기" : "상세 리뷰 보기"}
                              </Button>
                            </div>
                          </div>
                        )}

                        {workflowResult.approval.decision === "REJECTED" && (
                          <div className="rounded-xl border border-red-300 bg-red-50 p-5 dark:border-red-800 dark:bg-red-950" role="alert">
                            <div className="flex items-center gap-3">
                              <div className="flex-1">
                                <p className="font-semibold text-red-800 dark:text-red-200">
                                  답변 품질이 기준 미달입니다
                                </p>
                                <p className="text-sm text-red-700 dark:text-red-300">{workflowResult.approval.reason}</p>
                              </div>
                              <Badge variant="danger">{workflowResult.review.score}점</Badge>
                            </div>
                            <div className="mt-4 space-y-1.5">
                              {workflowResult.review.issues.slice(0, 3).map((issue, idx) => (
                                <div key={idx} className="flex items-start gap-2 text-sm text-red-700 dark:text-red-300">
                                  <span className="shrink-0" aria-hidden="true">{getSeverityIcon(issue.severity)}</span>
                                  <span>
                                    <Badge variant={getSeverityBadgeVariant(issue.severity)} className="mr-1.5">
                                      {labelIssueSeverity(issue.severity)}
                                    </Badge>
                                    {issue.description}
                                  </span>
                                </div>
                              ))}
                            </div>
                            <div className="mt-4 flex items-center gap-2">
                              <Button variant="ghost" size="sm" onClick={() => setReviewDetailExpanded(!reviewDetailExpanded)}>
                                {reviewDetailExpanded ? "상세 리뷰 닫기" : "상세 리뷰 보기"}
                              </Button>
                            </div>
                          </div>
                        )}

                        {/* Review Detail */}
                        {reviewDetailExpanded && (
                          <div className="rounded-xl border bg-card p-5 shadow-sm space-y-4">
                            <div className="flex items-center justify-between">
                              <h4 className="text-sm font-semibold">AI 리뷰 상세</h4>
                              <div className="flex items-center gap-2">
                                <Badge variant={getScoreBadgeVariant(workflowResult.review.score)}>
                                  {workflowResult.review.score}점
                                </Badge>
                                <Badge variant={
                                  workflowResult.review.decision === "PASS" ? "success" :
                                  workflowResult.review.decision === "REVISE" ? "warn" : "danger"
                                }>
                                  {labelReviewDecision(workflowResult.review.decision)}
                                </Badge>
                              </div>
                            </div>
                            <p className="text-sm text-muted-foreground">{workflowResult.review.summary}</p>
                            {workflowResult.review.issues.length > 0 && (
                              <div className="space-y-3">
                                <h5 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">발견된 이슈</h5>
                                {Object.entries(groupIssuesByCategory(workflowResult.review.issues)).map(([category, issues]) => (
                                  <div key={category} className="space-y-2">
                                    <p className="text-xs font-medium text-muted-foreground">
                                      {labelIssueCategory(category)} ({issues.length})
                                    </p>
                                    {issues.map((issue, idx) => (
                                      <div key={idx} className="rounded-lg border border-border/50 bg-muted/20 p-3">
                                        <div className="flex items-center gap-2">
                                          <span className="shrink-0" aria-hidden="true">{getSeverityIcon(issue.severity)}</span>
                                          <Badge variant={getSeverityBadgeVariant(issue.severity)}>
                                            {labelIssueSeverity(issue.severity)}
                                          </Badge>
                                          <span className="text-sm">{issue.description}</span>
                                        </div>
                                        {issue.suggestion && (
                                          <p className="mt-1.5 ml-7 text-xs text-muted-foreground">
                                            제안: {issue.suggestion}
                                          </p>
                                        )}
                                      </div>
                                    ))}
                                  </div>
                                ))}
                              </div>
                            )}
                            <div className="space-y-3">
                              <h5 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">승인 게이트 결과</h5>
                              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                                {workflowResult.approval.gateResults.map((gate) => (
                                  <div
                                    key={gate.gate}
                                    className={cn(
                                      "rounded-lg border p-3 text-center",
                                      gate.passed
                                        ? "border-emerald-200 bg-emerald-50/50 dark:border-emerald-800 dark:bg-emerald-950/30"
                                        : "border-red-200 bg-red-50/50 dark:border-red-800 dark:bg-red-950/30"
                                    )}
                                  >
                                    <span className="text-lg" aria-hidden="true">{gate.passed ? "\u2705" : "\u274C"}</span>
                                    <p className="mt-1 text-xs font-medium">{gate.gate}</p>
                                    <p className="text-xs text-muted-foreground">{gate.actualValue} / {gate.threshold}</p>
                                  </div>
                                ))}
                              </div>
                            </div>
                          </div>
                        )}

                        {workflowResult.approval.decision !== "AUTO_APPROVED" && (
                          <button
                            onClick={() => setManualMode(!manualMode)}
                            className="text-xs text-muted-foreground hover:text-foreground hover:underline transition-colors"
                          >
                            {manualMode ? "자동 모드로 전환" : "수동 모드로 전환"}
                          </button>
                        )}
                      </div>
                    )}

                    {/* Manual Mode Fallback */}
                    {manualMode && selected.status !== "APPROVED" && (
                      <div className="space-y-4 rounded-lg border border-border/50 bg-muted/10 p-4">
                        <p className="text-xs font-medium text-muted-foreground">수동 워크플로우</p>
                        <div className="flex items-center gap-2">
                          <input
                            className="flex h-9 max-w-[160px] rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm"
                            value={reviewActor}
                            onChange={(e) => setReviewActor(e.target.value)}
                            placeholder="리뷰어"
                            aria-label="리뷰어"
                          />
                          <input
                            className="flex h-9 w-full flex-1 rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm"
                            value={reviewComment}
                            onChange={(e) => setReviewComment(e.target.value)}
                            placeholder="리뷰 코멘트"
                            aria-label="리뷰 코멘트"
                          />
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={handleReview}
                            disabled={actionLoading || selected.status !== "DRAFT"}
                          >
                            리뷰
                          </Button>
                        </div>
                        <div className="flex items-center gap-2">
                          <input
                            className="flex h-9 max-w-[160px] rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm"
                            value={approveActor}
                            onChange={(e) => setApproveActor(e.target.value)}
                            placeholder="승인자"
                            aria-label="승인자"
                          />
                          <input
                            className="flex h-9 w-full flex-1 rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm"
                            value={approveComment}
                            onChange={(e) => setApproveComment(e.target.value)}
                            placeholder="승인 코멘트"
                            aria-label="승인 코멘트"
                          />
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={handleApprove}
                            disabled={actionLoading || !["DRAFT", "REVIEWED"].includes(selected.status)}
                          >
                            승인
                          </Button>
                        </div>
                      </div>
                    )}

                    {/* Send Action */}
                    {selected.status === "APPROVED" && (
                      <div className="flex items-center gap-2">
                        <Button onClick={handleSend} disabled={actionLoading}>
                          {actionLoading && <SpinnerIcon />}
                          발송하기
                        </Button>
                      </div>
                    )}
                  </div>
                </>
              )}
            </div>
          </div>

          {/* Right: Document Preview */}
          <div className="lg:sticky lg:top-20 lg:self-start">
            <div className="rounded-xl border bg-card p-6 shadow-sm space-y-4">
              <h4 className="text-base font-semibold">문서 미리보기</h4>
              <hr className="border-t border-border" />
              {selectedEvidence && selectedEvidence.documentId ? (
                isPdf(selectedEvidence.fileName) ? (
                  <PdfViewer
                    url={
                      selectedEvidence.pageStart != null
                        ? getDocumentPagesUrl(
                            selectedEvidence.documentId,
                            selectedEvidence.pageStart,
                            selectedEvidence.pageEnd ?? selectedEvidence.pageStart
                          )
                        : getDocumentDownloadUrl(selectedEvidence.documentId)
                    }
                    initialPage={1}
                    downloadUrl={getDocumentDownloadUrl(selectedEvidence.documentId)}
                    pagesDownloadUrl={
                      selectedEvidence.pageStart != null
                        ? getDocumentPagesUrl(
                            selectedEvidence.documentId,
                            selectedEvidence.pageStart,
                            selectedEvidence.pageEnd ?? selectedEvidence.pageStart
                          ) + "&download=true"
                        : undefined
                    }
                    fileName={selectedEvidence.fileName ?? undefined}
                  />
                ) : (
                  <div className="flex flex-col items-center space-y-6 p-6">
                    <p className="text-center text-sm text-muted-foreground">
                      {selectedEvidence.fileName ?? "문서"} 파일은 PDF가 아니므로 미리보기가 지원되지 않습니다.
                    </p>
                    <Button asChild>
                      <a
                        href={getDocumentDownloadUrl(selectedEvidence.documentId)}
                        download
                      >
                        원본 파일 다운로드
                      </a>
                    </Button>
                  </div>
                )
              ) : (
                <EmptyState title="참조 자료를 클릭하면 문서 미리보기가 표시됩니다" />
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
