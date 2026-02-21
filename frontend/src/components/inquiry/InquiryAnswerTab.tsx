"use client";

import { useState, useEffect, type ReactNode } from "react";
import dynamic from "next/dynamic";
import {
  draftInquiryAnswer,
  reviewAnswerDraft,
  approveAnswerDraft,
  sendAnswerDraft,
  autoWorkflow,
  updateAnswerDraft,
  getDocumentDownloadUrl,
  getDocumentPagesUrl,
  getInquiryIndexingStatus,
  type AnswerDraftResult,
  type InquiryDetail,
  type InquiryIndexingStatus,
  type AnalyzeEvidenceItem,
  type AutoWorkflowResult,
  type ReviewIssue,
  type GateResult,
} from "@/lib/api/client";
import {
  labelVerdict,
  labelAnswerStatus,
  labelRiskFlag,
  labelTone,
  labelChannel,
  labelReviewDecision,
  labelApprovalDecision,
  labelIssueSeverity,
  labelIssueCategory,
} from "@/lib/i18n/labels";
import { Badge, EmptyState } from "@/components/ui";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { showToast } from "@/lib/toast";
import { useInquiryEvents, type DraftStepData, type IndexingProgressData } from "@/hooks/useInquiryEvents";

const PdfViewer = dynamic(() => import("@/components/ui/PdfViewer"), {
  ssr: false,
  loading: () => <div className="flex items-center justify-center p-8 text-sm text-muted-foreground" role="status" aria-live="polite">PDF 뷰어 로딩 중...</div>,
});

interface InquiryAnswerTabProps {
  inquiryId: string;
  inquiry: InquiryDetail;
}

interface CitationView {
  chunkId: string;
  score: number | null;
  documentId: string | null;
  fileName: string | null;
  pageStart: number | null;
  pageEnd: number | null;
  sourceType?: "INQUIRY" | "KNOWLEDGE_BASE";
  excerpt?: string;
}

function parseCitation(raw: string): CitationView {
  const chunkMatch = raw.match(/chunk=([^\s]+)/);
  const scoreMatch = raw.match(/score=([0-9.]+)/);
  const docMatch = raw.match(/documentId=([^\s]+)/);
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

function evidenceToCitationView(ev: AnalyzeEvidenceItem): CitationView {
  return {
    chunkId: ev.chunkId,
    score: ev.score,
    documentId: ev.documentId,
    fileName: ev.fileName ?? null,
    pageStart: ev.pageStart ?? null,
    pageEnd: ev.pageEnd ?? null,
    sourceType: ev.sourceType,
    excerpt: ev.excerpt,
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

function getScoreBadgeVariant(score: number): "success" | "warn" | "danger" {
  if (score >= 90) return "success";
  if (score >= 70) return "warn";
  return "danger";
}

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

const SpinnerIcon = () => (
  <svg className="h-4 w-4 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-hidden="true">
    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
  </svg>
);

export default function InquiryAnswerTab({ inquiryId, inquiry }: InquiryAnswerTabProps) {
  const initialTone = (inquiry.preferredTone === "technical" || inquiry.preferredTone === "brief" || inquiry.preferredTone === "gilseon") ? inquiry.preferredTone : "gilseon";
  const [answerTone, setAnswerTone] = useState<"professional" | "technical" | "brief" | "gilseon">(initialTone);
  const [answerChannel, setAnswerChannel] = useState<"email" | "messenger">("email");
  const [answerDraft, setAnswerDraft] = useState<AnswerDraftResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Indexing status (SSE-driven, no polling)
  const [indexingStatus, setIndexingStatus] = useState<InquiryIndexingStatus | null>(null);
  const [indexingInProgress, setIndexingInProgress] = useState(false);

  // SSE draft step tracking
  const [draftSteps, setDraftSteps] = useState<DraftStepData[]>([]);
  const [draftGenerating, setDraftGenerating] = useState(false);

  // Evidence detail toggle
  const [evidenceExpanded, setEvidenceExpanded] = useState(false);

  // Selected evidence for preview
  const [selectedEvidence, setSelectedEvidence] = useState<CitationView | null>(null);

  // AI Workflow state
  const [workflowLoading, setWorkflowLoading] = useState(false);
  const [workflowResult, setWorkflowResult] = useState<AutoWorkflowResult | null>(null);
  const [reviewDetailExpanded, setReviewDetailExpanded] = useState(false);
  const [revisedDraftExpanded, setRevisedDraftExpanded] = useState(false);

  // Draft editing
  const [isEditing, setIsEditing] = useState(false);
  const [editedDraft, setEditedDraft] = useState("");
  const [savingDraft, setSavingDraft] = useState(false);

  // Draft question editing
  const [isEditingDraftQuestion, setIsEditingDraftQuestion] = useState(false);
  const [draftQuestion, setDraftQuestion] = useState(inquiry.question);

  // Manual mode fallback
  const [manualMode, setManualMode] = useState(false);
  const [reviewActor, setReviewActor] = useState("cs-agent");
  const [reviewComment, setReviewComment] = useState("");
  const [approveActor, setApproveActor] = useState("cs-lead");
  const [approveComment, setApproveComment] = useState("");

  // Refinement request
  const MAX_REFINEMENTS = 5;
  const [refinementInstructions, setRefinementInstructions] = useState("");

  // Initialize channel from inquiry
  useEffect(() => {
    if (inquiry.customerChannel === "messenger") {
      setAnswerChannel("messenger");
    }
  }, [inquiry.customerChannel]);

  // SSE: real-time indexing + draft step events
  useInquiryEvents(inquiryId, {
    onIndexingProgress: (data: IndexingProgressData) => {
      setIndexingInProgress(data.status !== "COMPLETED" && data.status !== "INDEXED" && data.status !== "FAILED");
      // Update indexing status display
      setIndexingStatus((prev) => prev ? {
        ...prev,
        indexed: data.indexed,
        total: data.total,
      } : {
        inquiryId,
        total: data.total,
        uploaded: 0,
        parsing: 0,
        parsed: 0,
        chunked: 0,
        indexed: data.indexed,
        failed: data.failed,
        documents: [],
      });
    },
    onEvent: (event) => {
      if (event.type === "INDEXING_COMPLETED") {
        setIndexingInProgress(false);
        // Refresh indexing status from API
        getInquiryIndexingStatus(inquiryId).then(setIndexingStatus).catch(() => {});
      } else if (event.type === "INDEXING_FAILED") {
        setIndexingInProgress(false);
      } else if (event.type === "INDEXING_STARTED") {
        setIndexingInProgress(true);
      }
    },
    onDraftStep: (data: DraftStepData) => {
      setDraftGenerating(true);
      setDraftSteps((prev) => {
        const existing = prev.findIndex((s) => s.step === data.step);
        if (existing >= 0) {
          const updated = [...prev];
          updated[existing] = data;
          return updated;
        }
        return [...prev, data];
      });
    },
    onDraftCompleted: () => {
      setDraftGenerating(false);
    },
  });

  // Fetch initial indexing status (one-time, no polling)
  useEffect(() => {
    getInquiryIndexingStatus(inquiryId).then((status) => {
      setIndexingStatus(status);
      const hasIndexing = status.documents.some(
        (d) => d.status === "INDEXING" || d.status === "UPLOADED" || d.status === "PARSING" || d.status === "PARSED" || d.status === "CHUNKED"
      );
      setIndexingInProgress(hasIndexing);
    }).catch(() => {});
  }, [inquiryId]);

  const handleDraftAnswer = async () => {
    setLoading(true);
    setError(null);
    setSelectedEvidence(null);
    setEvidenceExpanded(false);
    setWorkflowResult(null);
    setReviewDetailExpanded(false);
    setDraftSteps([]);
    setDraftGenerating(true);

    try {
      const draft = await draftInquiryAnswer(inquiryId, draftQuestion, answerTone, answerChannel);
      setAnswerDraft(draft);
      setDraftGenerating(false);
      showToast(`답변 초안 v${draft.version} 생성 완료`, "success");
    } catch (err) {
      setError(err instanceof Error ? err.message : "답변 초안 생성 중 오류가 발생했습니다.");
      setAnswerDraft(null);
      setDraftGenerating(false);
    } finally {
      setLoading(false);
    }
  };

  const handleAutoWorkflow = async () => {
    if (!answerDraft) return;
    setWorkflowLoading(true);
    setError(null);
    setWorkflowResult(null);

    try {
      const result = await autoWorkflow(inquiryId, answerDraft.answerId);
      setWorkflowResult(result);

      // Update draft status based on workflow result
      setAnswerDraft((prev) => prev ? { ...prev, status: result.finalStatus as AnswerDraftResult["status"] } : prev);

      if (result.approval.decision === "AUTO_APPROVED") {
        showToast(`AI 리뷰 통과 (${result.review.score}점) - 자동 승인 완료`, "success");
      } else if (result.approval.decision === "ESCALATED") {
        showToast("AI 리뷰 완료 - 사람 확인이 필요합니다", "warn");
      } else {
        showToast("AI 리뷰 결과 답변 품질이 기준 미달입니다", "error");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "자동 워크플로우 처리 중 오류가 발생했습니다.");
    } finally {
      setWorkflowLoading(false);
    }
  };

  // Manual fallback handlers
  const handleReview = async () => {
    if (!answerDraft) return;
    setLoading(true);
    setError(null);
    try {
      const reviewed = await reviewAnswerDraft(inquiryId, answerDraft.answerId, reviewActor.trim() || undefined, reviewComment.trim() || undefined);
      setAnswerDraft(reviewed);
      showToast(`리뷰 완료 - v${reviewed.version}`, "success");
    } catch (err) {
      setError(err instanceof Error ? err.message : "리뷰 처리 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const handleApprove = async () => {
    if (!answerDraft) return;
    setLoading(true);
    setError(null);
    try {
      const approved = await approveAnswerDraft(inquiryId, answerDraft.answerId, approveActor.trim() || undefined, approveComment.trim() || undefined);
      setAnswerDraft(approved);
      showToast(`승인 완료 - v${approved.version}`, "success");
    } catch (err) {
      setError(err instanceof Error ? err.message : "승인 처리 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const handleSend = async () => {
    if (!answerDraft) return;
    setLoading(true);
    setError(null);
    try {
      const sendRequestId = `${answerDraft.answerId}-${Date.now()}`;
      const sent = await sendAnswerDraft(inquiryId, answerDraft.answerId, "cs-sender", answerChannel, sendRequestId);
      setAnswerDraft(sent);
      showToast(`발송 완료 - v${sent.version}`, "success");
    } catch (err) {
      setError(err instanceof Error ? err.message : "발송 처리 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const handleAdoptRevised = () => {
    if (!answerDraft || !workflowResult?.review.revisedDraft) return;
    setAnswerDraft((prev) => prev ? { ...prev, draft: workflowResult.review.revisedDraft! } : prev);
    showToast("수정안이 채택되었습니다", "success");
  };

  const handleSaveDraft = async () => {
    if (!answerDraft || !editedDraft.trim()) return;
    setSavingDraft(true);
    try {
      const updated = await updateAnswerDraft(inquiryId, answerDraft.answerId, editedDraft);
      setAnswerDraft(updated);
      setIsEditing(false);
      showToast("답변 본문이 수정되었습니다", "success");
    } catch (err) {
      showToast(err instanceof Error ? err.message : "답변 수정 중 오류가 발생했습니다.", "error");
    } finally {
      setSavingDraft(false);
    }
  };

  const handleRefineDraft = async () => {
    if (!answerDraft || !refinementInstructions.trim()) return;
    setLoading(true);
    setError(null);
    setSelectedEvidence(null);
    setEvidenceExpanded(false);
    setWorkflowResult(null);
    setReviewDetailExpanded(false);
    setDraftSteps([]);
    setDraftGenerating(true);

    try {
      const draft = await draftInquiryAnswer(
        inquiryId,
        draftQuestion,
        answerTone,
        answerChannel,
        refinementInstructions.trim(),
        answerDraft.answerId
      );
      setAnswerDraft(draft);
      setRefinementInstructions("");
      setDraftGenerating(false);
      showToast(`보완 답변 v${draft.version} 생성 완료 (이력 탭에서 확인 가능)`, "success");
    } catch (err) {
      setError(err instanceof Error ? err.message : "보완 답변 생성 중 오류가 발생했습니다.");
      setDraftGenerating(false);
    } finally {
      setLoading(false);
    }
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

  // Build evidence list from draft result
  const evidenceItems: CitationView[] = answerDraft
    ? answerDraft.evidences
      ? answerDraft.evidences.map(evidenceToCitationView)
      : answerDraft.citations.map(parseCitation)
    : [];

  // Group issues by category
  const groupIssuesByCategory = (issues: ReviewIssue[]): Record<string, ReviewIssue[]> => {
    return issues.reduce<Record<string, ReviewIssue[]>>((acc, issue) => {
      const cat = issue.category;
      if (!acc[cat]) acc[cat] = [];
      acc[cat].push(issue);
      return acc;
    }, {});
  };

  // Render answer body with clickable citation links
  const renderDraftWithCitations = (text: string): ReactNode => {
    const citationRegex = /\(([^,]+\.pdf),\s*p\.(\d+)(?:-(\d+))?\)/gi;
    const parts: ReactNode[] = [];
    let lastIndex = 0;
    let match: RegExpExecArray | null;

    while ((match = citationRegex.exec(text)) !== null) {
      // Add text before the match
      if (match.index > lastIndex) {
        parts.push(text.slice(lastIndex, match.index));
      }

      const matchedFileName = match[1].trim();
      const pageStart = parseInt(match[2], 10);
      const pageEnd = match[3] ? parseInt(match[3], 10) : pageStart;
      const fullMatch = match[0];

      // Find matching evidence item
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

    // Add remaining text
    if (lastIndex < text.length) {
      parts.push(text.slice(lastIndex));
    }

    return parts.length > 0 ? parts : text;
  };

  // Draft step label mapping
  const draftStepLabel = (step: string): string => {
    switch (step) {
      case "RETRIEVE": return "검색 중";
      case "VERIFY": return "검증 중";
      case "COMPOSE": return "작성 중";
      case "SELF_REVIEW": return "자체 검증";
      default: return step;
    }
  };

  return (
    <div className="space-y-6">
      {/* SSE Draft Step Progress */}
      {draftGenerating && draftSteps.length > 0 && (
        <div
          className="rounded-lg border border-primary/30 bg-primary/5 px-4 py-3 space-y-3"
          role="status"
          aria-label="답변 생성 진행 중"
        >
          <div className="flex items-center gap-2 text-sm font-medium text-primary">
            <svg className="h-4 w-4 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-hidden="true">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
            답변 생성 중...
          </div>
          <div className="flex items-center gap-4">
            {(["RETRIEVE", "VERIFY", "COMPOSE", "SELF_REVIEW"] as const).map((step) => {
              const stepData = draftSteps.find((s) => s.step === step);
              const currentStep = draftSteps.filter((s) => s.status === "IN_PROGRESS")[0];
              const isActive = currentStep?.step === step;
              const isDone = stepData?.status === "COMPLETED";
              const isFailed = stepData?.status === "FAILED";
              const isRetrying = stepData?.status === "RETRY";
              return (
                <div
                  key={step}
                  className={cn(
                    "flex items-center gap-2 text-sm",
                    isDone && "text-emerald-600 dark:text-emerald-400",
                    (isActive || isRetrying) && "text-primary font-semibold",
                    isFailed && "text-destructive",
                    !isDone && !isActive && !isFailed && !isRetrying && "text-muted-foreground"
                  )}
                >
                  {isDone ? (
                    <svg className="h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" aria-hidden="true">
                      <path strokeLinecap="round" strokeLinejoin="round" d="m4.5 12.75 6 6 9-13.5" />
                    </svg>
                  ) : isActive || isRetrying ? (
                    <svg className="h-4 w-4 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-hidden="true">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                    </svg>
                  ) : isFailed ? (
                    <svg className="h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" aria-hidden="true">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
                    </svg>
                  ) : (
                    <span className="h-4 w-4 rounded-full border-2 border-current" aria-hidden="true" />
                  )}
                  <span>{draftStepLabel(step)}{isRetrying ? " (재시도)" : ""}</span>
                </div>
              );
            })}
          </div>
          {draftSteps.at(-1)?.message && (
            <p className="text-xs text-muted-foreground">{draftSteps.at(-1)!.message}</p>
          )}
        </div>
      )}

      {/* Indexing Warning Banner */}
      {indexingInProgress && (
        <div
          className="flex items-center gap-3 rounded-lg border border-yellow-500/30 bg-yellow-500/10 px-4 py-3 text-sm text-yellow-700 dark:text-yellow-400"
          role="alert"
        >
          <svg className="h-5 w-5 shrink-0 animate-pulse" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z" />
          </svg>
          <span>문서 처리 중 -- 근거가 부족할 수 있습니다</span>
          {indexingStatus && (
            <span className="ml-auto text-xs text-muted-foreground">
              {indexingStatus.indexed}/{indexingStatus.total} 완료
            </span>
          )}
        </div>
      )}

      {/* Draft Generation Form */}
      <div className="rounded-xl border bg-card p-6 shadow-sm space-y-4">
        <h3 className="text-base font-semibold">답변 초안 생성</h3>

        <div className="rounded-lg border border-border/50 bg-muted/20 p-4 text-sm leading-relaxed">
          <div className="flex items-center justify-between mb-1">
            <p className="text-xs font-medium text-muted-foreground">질문</p>
            {!isEditingDraftQuestion && (
              <Button
                variant="outline"
                size="sm"
                className="gap-1.5 h-7 text-xs"
                onClick={() => setIsEditingDraftQuestion(true)}
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/><path d="m15 5 4 4"/></svg>
                수정
              </Button>
            )}
          </div>
          {isEditingDraftQuestion ? (
            <div className="space-y-2">
              <textarea
                className="w-full min-h-[80px] rounded-lg border border-input bg-background p-3 text-sm leading-relaxed shadow-sm focus:outline-none focus:ring-2 focus:ring-ring"
                value={draftQuestion}
                onChange={(e) => setDraftQuestion(e.target.value)}
                aria-label="답변 생성용 질문 수정"
              />
              <div className="flex items-center gap-2">
                <Button size="sm" className="h-7 text-xs" onClick={() => setIsEditingDraftQuestion(false)} disabled={!draftQuestion.trim()}>
                  확인
                </Button>
                <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={() => { setDraftQuestion(inquiry.question); setIsEditingDraftQuestion(false); }}>
                  초기화
                </Button>
              </div>
            </div>
          ) : (
            <p className="whitespace-pre-wrap">{draftQuestion}</p>
          )}
          {draftQuestion !== inquiry.question && !isEditingDraftQuestion && (
            <p className="mt-1 text-xs text-blue-500">* 원본 질문에서 수정됨</p>
          )}
        </div>

        {/* Translated Query Display */}
        {answerDraft?.translatedQuery && answerDraft.translatedQuery !== draftQuestion && (
          <div className="rounded-lg border border-border/50 bg-muted/30 px-4 py-3 text-sm text-muted-foreground">
            <span className="font-medium text-foreground/70">번역된 질문:</span>{" "}
            <span className="italic">{answerDraft.translatedQuery}</span>
          </div>
        )}

        <div className="flex items-center gap-3">
          <label className="flex-1 min-w-[140px] max-w-[200px] space-y-1.5 text-sm font-medium">
            톤
            <select
              className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm"
              value={answerTone}
              onChange={(e) => setAnswerTone(e.target.value as "professional" | "technical" | "brief" | "gilseon")}
            >
              <option value="professional">{labelTone("professional")}</option>
              <option value="technical">{labelTone("technical")}</option>
              <option value="brief">{labelTone("brief")}</option>
              <option value="gilseon">{labelTone("gilseon")}</option>
            </select>
          </label>

          <label className="flex-1 min-w-[140px] max-w-[200px] space-y-1.5 text-sm font-medium">
            채널
            <select
              className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm"
              value={answerChannel}
              onChange={(e) => setAnswerChannel(e.target.value as "email" | "messenger")}
            >
              <option value="email">{labelChannel("email")}</option>
              <option value="messenger">{labelChannel("messenger")}</option>
            </select>
          </label>

          <Button
            onClick={handleDraftAnswer}
            disabled={loading}
            aria-busy={loading}
            className="self-end"
          >
            {loading && <SpinnerIcon />}
            {loading ? "생성 중..." : "답변 초안 생성"}
          </Button>
        </div>

        {error && (
          <p className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive" role="alert">
            {error}
          </p>
        )}
      </div>

      {/* Draft Result - Split Pane */}
      {answerDraft && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_400px]">
          {/* Left: Analysis Summary + Answer + Citations + Workflow */}
          <div className="space-y-6">
            {/* Analysis Summary Card */}
            <div className="rounded-xl border bg-card p-6 shadow-sm space-y-4">
              <h3 className="text-base font-semibold">분석 결과 요약</h3>
              <hr className="border-t border-border" />

              <div className="grid grid-cols-3 gap-4">
                <div className="rounded-xl border bg-card p-5 shadow-sm">
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">판정</p>
                  <div className="text-lg font-bold tracking-tight text-foreground">
                    <Badge variant={getVerdictBadgeVariant(answerDraft.verdict)}>
                      {labelVerdict(answerDraft.verdict)}
                    </Badge>
                  </div>
                </div>
                <div className="rounded-xl border bg-card p-5 shadow-sm">
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">신뢰도</p>
                  <p className="text-2xl font-bold tracking-tight text-foreground">{answerDraft.confidence}</p>
                </div>
                <div className="rounded-xl border bg-card p-5 shadow-sm">
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">근거 수</p>
                  <p className="text-2xl font-bold tracking-tight text-foreground">{evidenceItems.length}</p>
                  <p className="text-xs text-muted-foreground">검색된 근거</p>
                </div>
              </div>

              {/* Translated Query Info */}
              {answerDraft?.translatedQuery && (
                <div className="rounded-lg border border-border/50 bg-blue-50/50 dark:bg-blue-950/20 px-4 py-3 text-sm">
                  <span className="font-medium text-blue-700 dark:text-blue-400">검색 질문 (영어 번역):</span>{" "}
                  <span className="text-blue-600 dark:text-blue-300 italic">{answerDraft.translatedQuery}</span>
                </div>
              )}

              {/* Risk Flags */}
              {answerDraft.riskFlags.length > 0 && (
                <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
                  <b>리스크 플래그:</b>{" "}
                  {answerDraft.riskFlags.map((flag, idx) => (
                    <span key={idx} className={idx > 0 ? "ml-1" : "ml-2"}>
                      <Badge variant="danger">
                        {labelRiskFlag(flag)}
                      </Badge>
                    </span>
                  ))}
                </div>
              )}

              {/* Collapsible Evidence Detail */}
              {evidenceItems.length > 0 && (
                <div>
                  <button
                    onClick={() => setEvidenceExpanded(!evidenceExpanded)}
                    className="flex items-center gap-2 text-sm font-medium text-primary hover:underline"
                    aria-expanded={evidenceExpanded}
                  >
                    <svg
                      className={cn("h-4 w-4 transition-transform", evidenceExpanded && "rotate-90")}
                      xmlns="http://www.w3.org/2000/svg"
                      fill="none"
                      viewBox="0 0 24 24"
                      strokeWidth={2}
                      stroke="currentColor"
                      aria-hidden="true"
                    >
                      <path strokeLinecap="round" strokeLinejoin="round" d="m8.25 4.5 7.5 7.5-7.5 7.5" />
                    </svg>
                    근거 상세 보기 ({evidenceItems.length}건)
                  </button>

                  {evidenceExpanded && (
                    <div className="mt-3 space-y-3">
                      {evidenceItems.map((ev, idx) => {
                        const isSelected = selectedEvidence?.chunkId === ev.chunkId;
                        return (
                          <div
                            key={`${ev.chunkId}-${idx}`}
                            className={cn(
                              "rounded-lg border border-border/50 bg-muted/30 p-4 cursor-pointer transition-colors hover:border-primary/30 hover:bg-primary/5",
                              isSelected && "border-primary/50 bg-primary/5 ring-1 ring-primary/20"
                            )}
                            onClick={() => setSelectedEvidence(ev)}
                            onKeyDown={(e) => {
                              if (e.key === "Enter" || e.key === " ") {
                                e.preventDefault();
                                setSelectedEvidence(ev);
                              }
                            }}
                            role="button"
                            tabIndex={0}
                            aria-label={`근거 미리보기: ${formatCitationLabel(ev)}`}
                            aria-pressed={isSelected}
                          >
                            <div className="flex items-center gap-2">
                              {ev.sourceType && (
                                <Badge variant={ev.sourceType === "KNOWLEDGE_BASE" ? "info" : "neutral"}>
                                  {ev.sourceType === "KNOWLEDGE_BASE" ? "지식 기반" : "문의 첨부"}
                                </Badge>
                              )}
                              <span className="text-sm font-medium">
                                {formatCitationLabel(ev)}
                              </span>
                              {ev.score != null && (
                                <span className="text-xs text-muted-foreground">
                                  유사도 {(ev.score * 100).toFixed(1)}%
                                </span>
                              )}
                            </div>
                            {ev.excerpt && (
                              <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground line-clamp-2">
                                {ev.excerpt}
                              </p>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* Answer Draft Card */}
            <div className="rounded-xl border bg-card p-6 shadow-sm space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-base font-semibold">답변 초안 - v{answerDraft.version}</h3>
                <Badge variant={getAnswerStatusBadgeVariant(answerDraft.status)}>
                  {labelAnswerStatus(answerDraft.status)}
                </Badge>
              </div>

              <hr className="border-t border-border" />

              {/* Timeline */}
              <div className="flex items-center gap-2 py-4" role="group" aria-label="답변 워크플로우 진행 상태">
                {[
                  { key: "DRAFT", label: "초안 생성" },
                  { key: "REVIEWED", label: "검토 완료" },
                  { key: "APPROVED", label: "승인 완료" },
                  { key: "SENT", label: "발송 완료" },
                ].map((step) => {
                  const order: Record<string, number> = { DRAFT: 0, REVIEWED: 1, APPROVED: 2, SENT: 3 };
                  const current = order[answerDraft.status] ?? 0;
                  const idx = order[step.key] ?? 0;
                  const done = idx <= current;
                  const active = idx === current;
                  return (
                    <div
                      key={step.key}
                      className={cn(
                        "flex items-center gap-2 text-sm text-muted-foreground",
                        done && "text-foreground",
                        active && "text-primary font-semibold"
                      )}
                    >
                      <span
                        className={cn(
                          "h-2.5 w-2.5 rounded-full bg-muted-foreground",
                          done && "bg-primary"
                        )}
                        aria-hidden="true"
                      />
                      <span>{step.label}</span>
                      <span className="text-xs text-muted-foreground">{done ? "완료" : "대기"}</span>
                    </div>
                  );
                })}
              </div>

              <hr className="border-t border-border" />

              {/* Answer Body */}
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <h4 className="text-base font-semibold">답변 본문</h4>
                  <div className="flex items-center gap-2">
                    <p className="text-xs text-muted-foreground">
                      {labelChannel(answerDraft.channel)} / {labelTone(answerDraft.tone)}
                    </p>
                    {!isEditing && answerDraft.status !== "SENT" && (
                      <Button
                        variant="outline"
                        size="sm"
                        className="gap-1.5"
                        onClick={() => { setEditedDraft(answerDraft.draft); setIsEditing(true); }}
                      >
                        <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/><path d="m15 5 4 4"/></svg>
                        수정
                      </Button>
                    )}
                  </div>
                </div>
                {isEditing ? (
                  <div className="space-y-3">
                    <textarea
                      className="w-full min-h-[200px] rounded-lg border border-input bg-transparent p-4 text-sm leading-relaxed shadow-sm focus:outline-none focus:ring-2 focus:ring-ring"
                      value={editedDraft}
                      onChange={(e) => setEditedDraft(e.target.value)}
                      aria-label="답변 본문 수정"
                    />
                    <div className="flex items-center gap-2">
                      <Button size="sm" onClick={handleSaveDraft} disabled={savingDraft || !editedDraft.trim()}>
                        {savingDraft && <SpinnerIcon />}
                        {savingDraft ? "저장 중..." : "저장"}
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => setIsEditing(false)} disabled={savingDraft}>
                        취소
                      </Button>
                    </div>
                  </div>
                ) : (
                  <div className="rounded-lg border bg-muted/20 p-4 text-sm leading-relaxed whitespace-pre-wrap">
                    {renderDraftWithCitations(answerDraft.draft)}
                  </div>
                )}
              </div>

              {/* Format Warnings */}
              {answerDraft.formatWarnings.length > 0 && (
                <div className="rounded-lg border border-warning/30 bg-warning-light px-4 py-3 text-sm text-warning-foreground">
                  <b>형식 경고:</b> {answerDraft.formatWarnings.map(w => labelRiskFlag(w) || w).join(", ")}
                </div>
              )}

              {/* Self-Review Issues */}
              {answerDraft.selfReviewIssues && answerDraft.selfReviewIssues.length > 0 && (
                <div className="rounded-lg border border-border/50 bg-muted/20 p-4 space-y-2">
                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                    자체 검증 결과 ({answerDraft.selfReviewIssues.length}건)
                  </p>
                  {answerDraft.selfReviewIssues.map((issue, idx) => (
                    <div key={idx} className="flex items-start gap-2 text-sm">
                      <Badge variant={issue.severity === "CRITICAL" ? "danger" : issue.severity === "WARNING" ? "warn" : "info"}>
                        {labelIssueSeverity(issue.severity)}
                      </Badge>
                      <div>
                        <span className="font-medium">[{labelIssueCategory(issue.category)}]</span>{" "}
                        <span className="text-muted-foreground">{issue.description}</span>
                        {issue.suggestion && (
                          <p className="text-xs text-muted-foreground mt-0.5">제안: {issue.suggestion}</p>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Review Info */}
              {answerDraft.reviewedBy && (
                <div className="rounded-lg border border-border/50 bg-muted/30 p-4">
                  <b>리뷰:</b> {answerDraft.reviewedBy}
                  {answerDraft.reviewComment && <span className="text-sm text-muted-foreground"> - {answerDraft.reviewComment}</span>}
                </div>
              )}

              {/* Approval Info */}
              {answerDraft.approvedBy && (
                <div className="rounded-lg border border-border/50 bg-muted/30 p-4">
                  <b>승인:</b> {answerDraft.approvedBy}
                  {answerDraft.approveComment && <span className="text-sm text-muted-foreground"> - {answerDraft.approveComment}</span>}
                </div>
              )}

              {/* Sent Info */}
              {answerDraft.sentBy && (
                <div className="rounded-lg border border-success/30 bg-success-light px-4 py-3 text-sm text-success-foreground">
                  <b>발송:</b> {answerDraft.sentBy} | {answerDraft.sendChannel} | {answerDraft.sendMessageId}
                </div>
              )}

              <hr className="border-t border-border" />

              {/* Workflow Actions */}
              <div className="space-y-4">
                <h4 className="text-base font-semibold">워크플로우 액션</h4>

                {/* AI Auto Workflow - only show when status is DRAFT */}
                {answerDraft.status === "DRAFT" && !workflowResult && (
                  <div className="space-y-3">
                    <Button
                      onClick={handleAutoWorkflow}
                      disabled={workflowLoading || loading}
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

                {/* AI Workflow Result Cards */}
                {workflowResult && (
                  <div className="space-y-4">
                    {/* Case A: AUTO_APPROVED */}
                    {workflowResult.approval.decision === "AUTO_APPROVED" && (
                      <div className="rounded-xl border border-emerald-300 bg-emerald-50 p-5 dark:border-emerald-800 dark:bg-emerald-950" role="status">
                        <div className="flex items-center gap-3">
                          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-100 text-emerald-600 dark:bg-emerald-900 dark:text-emerald-400" aria-hidden="true">
                            <svg className="h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" d="m4.5 12.75 6 6 9-13.5" />
                            </svg>
                          </span>
                          <div className="flex-1">
                            <p className="font-semibold text-emerald-800 dark:text-emerald-200">
                              AI 리뷰 통과 ({workflowResult.review.score}점) &rarr; 자동 승인 완료
                            </p>
                            <p className="text-sm text-emerald-700 dark:text-emerald-300">{workflowResult.summary}</p>
                          </div>
                          <Badge variant="success">{workflowResult.review.score}점</Badge>
                        </div>

                        {/* Gate Results Summary */}
                        <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-4">
                          {workflowResult.approval.gateResults.map((gate) => (
                            <div key={gate.gate} className="flex items-center gap-1.5 text-xs text-emerald-700 dark:text-emerald-300">
                              <span aria-hidden="true">{gate.passed ? "\u2705" : "\u274C"}</span>
                              <span>{gate.gate}</span>
                            </div>
                          ))}
                        </div>

                        <div className="mt-4 flex items-center gap-2">
                          <Button size="sm" onClick={handleSend} disabled={loading}>
                            {loading && <SpinnerIcon />}
                            발송하기
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => setReviewDetailExpanded(!reviewDetailExpanded)}
                          >
                            {reviewDetailExpanded ? "상세 리뷰 닫기" : "상세 리뷰 보기"}
                          </Button>
                        </div>
                      </div>
                    )}

                    {/* Case B: ESCALATED */}
                    {workflowResult.approval.decision === "ESCALATED" && (
                      <div className="rounded-xl border border-amber-300 bg-amber-50 p-5 dark:border-amber-800 dark:bg-amber-950" role="alert">
                        <div className="flex items-center gap-3">
                          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-amber-100 text-amber-600 dark:bg-amber-900 dark:text-amber-400" aria-hidden="true">
                            <svg className="h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z" />
                            </svg>
                          </span>
                          <div className="flex-1">
                            <p className="font-semibold text-amber-800 dark:text-amber-200">
                              {labelApprovalDecision("ESCALATED")}
                            </p>
                            <p className="text-sm text-amber-700 dark:text-amber-300">{workflowResult.approval.reason}</p>
                          </div>
                          <Badge variant="warn">{workflowResult.review.score}점</Badge>
                        </div>

                        {/* Failed Gates */}
                        <div className="mt-4 space-y-2">
                          {workflowResult.approval.gateResults.filter((g) => !g.passed).map((gate) => (
                            <div key={gate.gate} className="flex items-center gap-2 rounded-lg border border-amber-200 bg-white/60 px-3 py-2 text-sm dark:border-amber-800 dark:bg-amber-950/50">
                              <span aria-hidden="true">{"\u274C"}</span>
                              <span className="font-medium">{gate.gate}</span>
                              <span className="text-muted-foreground">실제: {gate.actualValue} / 기준: {gate.threshold}</span>
                            </div>
                          ))}
                        </div>

                        <div className="mt-4 flex items-center gap-2">
                          <Button size="sm" onClick={handleApprove} disabled={loading}>
                            {loading && <SpinnerIcon />}
                            검토 후 승인
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={handleDraftAnswer}
                            disabled={loading}
                          >
                            거부하고 재생성
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setReviewDetailExpanded(!reviewDetailExpanded)}
                          >
                            {reviewDetailExpanded ? "상세 리뷰 닫기" : "상세 리뷰 보기"}
                          </Button>
                        </div>
                      </div>
                    )}

                    {/* Case C: REJECTED */}
                    {workflowResult.approval.decision === "REJECTED" && (
                      <div className="rounded-xl border border-red-300 bg-red-50 p-5 dark:border-red-800 dark:bg-red-950" role="alert">
                        <div className="flex items-center gap-3">
                          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-red-100 text-red-600 dark:bg-red-900 dark:text-red-400" aria-hidden="true">
                            <svg className="h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
                            </svg>
                          </span>
                          <div className="flex-1">
                            <p className="font-semibold text-red-800 dark:text-red-200">
                              답변 품질이 기준 미달입니다
                            </p>
                            <p className="text-sm text-red-700 dark:text-red-300">{workflowResult.approval.reason}</p>
                          </div>
                          <Badge variant="danger">{workflowResult.review.score}점</Badge>
                        </div>

                        {/* Issues Summary */}
                        <div className="mt-4 space-y-1.5">
                          {workflowResult.review.issues.slice(0, 5).map((issue, idx) => (
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
                          {workflowResult.review.revisedDraft && (
                            <Button size="sm" onClick={handleAdoptRevised}>
                              수정안 채택
                            </Button>
                          )}
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={handleDraftAnswer}
                            disabled={loading}
                          >
                            {loading && <SpinnerIcon />}
                            수정안으로 재생성
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setReviewDetailExpanded(!reviewDetailExpanded)}
                          >
                            {reviewDetailExpanded ? "상세 리뷰 닫기" : "상세 리뷰 보기"}
                          </Button>
                        </div>
                      </div>
                    )}

                    {/* Review Detail Section (Collapsible) */}
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

                        {/* Issues grouped by category */}
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

                        {/* Revised Draft Toggle */}
                        {workflowResult.review.decision === "REVISE" && workflowResult.review.revisedDraft && (
                          <div className="space-y-3">
                            <button
                              onClick={() => setRevisedDraftExpanded(!revisedDraftExpanded)}
                              className="flex items-center gap-2 text-sm font-medium text-primary hover:underline"
                              aria-expanded={revisedDraftExpanded}
                            >
                              <svg
                                className={cn("h-4 w-4 transition-transform", revisedDraftExpanded && "rotate-90")}
                                xmlns="http://www.w3.org/2000/svg"
                                fill="none"
                                viewBox="0 0 24 24"
                                strokeWidth={2}
                                stroke="currentColor"
                                aria-hidden="true"
                              >
                                <path strokeLinecap="round" strokeLinejoin="round" d="m8.25 4.5 7.5 7.5-7.5 7.5" />
                              </svg>
                              수정안 보기
                            </button>

                            {revisedDraftExpanded && (
                              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                                <div className="space-y-2">
                                  <p className="text-xs font-medium text-muted-foreground">원본</p>
                                  <div className="rounded-lg border bg-muted/20 p-3 text-sm leading-relaxed whitespace-pre-wrap max-h-64 overflow-y-auto">
                                    {answerDraft.draft}
                                  </div>
                                </div>
                                <div className="space-y-2">
                                  <p className="text-xs font-medium text-muted-foreground">수정안</p>
                                  <div className="rounded-lg border border-emerald-200 bg-emerald-50/50 p-3 text-sm leading-relaxed whitespace-pre-wrap max-h-64 overflow-y-auto dark:border-emerald-800 dark:bg-emerald-950/30">
                                    {workflowResult.review.revisedDraft}
                                  </div>
                                </div>
                              </div>
                            )}

                            <div className="flex items-center gap-2">
                              <Button size="sm" onClick={handleAdoptRevised}>
                                수정안 채택
                              </Button>
                              <Button variant="outline" size="sm" onClick={handleApprove} disabled={loading}>
                                원본 유지하고 승인 요청
                              </Button>
                            </div>
                          </div>
                        )}

                        {/* Gate Results Card */}
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
                                <p className="text-xs text-muted-foreground">
                                  {gate.actualValue} / {gate.threshold}
                                </p>
                              </div>
                            ))}
                          </div>
                        </div>
                      </div>
                    )}

                    {/* Manual fallback after workflow result */}
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
                {manualMode && answerDraft.status !== "APPROVED" && answerDraft.status !== "SENT" && (
                  <div className="space-y-4 rounded-lg border border-border/50 bg-muted/10 p-4">
                    <p className="text-xs font-medium text-muted-foreground">수동 워크플로우</p>

                    {/* Review Action */}
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
                        disabled={loading || answerDraft.status !== "DRAFT"}
                      >
                        리뷰
                      </Button>
                    </div>

                    {/* Approve Action */}
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
                        disabled={loading || !["DRAFT", "REVIEWED"].includes(answerDraft.status)}
                      >
                        승인
                      </Button>
                    </div>
                  </div>
                )}

                {/* Send Action - only when APPROVED */}
                {answerDraft.status === "APPROVED" && (
                  <div className="flex items-center gap-2">
                    <Button
                      onClick={handleSend}
                      disabled={loading}
                    >
                      {loading && <SpinnerIcon />}
                      발송하기
                    </Button>
                  </div>
                )}
              </div>

              {/* Refinement Request Section */}
              {answerDraft.status !== "SENT" && (
                <>
                  <hr className="border-t border-border" />
                  <div className="space-y-3">
                    <div className="flex items-center justify-between">
                      <h4 className="text-base font-semibold">보완 요청</h4>
                      <span className="text-xs text-muted-foreground">
                        {answerDraft.refinementCount ?? 0}/{MAX_REFINEMENTS}회 사용
                      </span>
                    </div>
                    <textarea
                      className="w-full min-h-[80px] rounded-lg border border-input bg-transparent p-3 text-sm leading-relaxed shadow-sm focus:outline-none focus:ring-2 focus:ring-ring"
                      value={refinementInstructions}
                      onChange={(e) => setRefinementInstructions(e.target.value)}
                      placeholder="추가 요청사항을 입력하세요 (예: 더 간결하게, 특정 제품에 대한 내용 추가, 톤 변경 등)"
                      aria-label="보완 요청사항 입력"
                      disabled={(answerDraft.refinementCount ?? 0) >= MAX_REFINEMENTS || loading}
                    />
                    <div className="flex items-center gap-3">
                      <Button
                        onClick={handleRefineDraft}
                        disabled={
                          loading ||
                          !refinementInstructions.trim() ||
                          (answerDraft.refinementCount ?? 0) >= MAX_REFINEMENTS
                        }
                        aria-busy={loading}
                      >
                        {loading && <SpinnerIcon />}
                        {loading ? "보완 답변 생성 중..." : "보완 답변 생성"}
                      </Button>
                      {(answerDraft.refinementCount ?? 0) >= MAX_REFINEMENTS && (
                        <p className="text-xs text-destructive">
                          보완 요청 횟수를 모두 사용했습니다
                        </p>
                      )}
                    </div>
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
                <EmptyState title="근거 상세 보기에서 항목을 클릭하면 문서 미리보기가 표시됩니다" />
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
