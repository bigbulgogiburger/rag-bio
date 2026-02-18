"use client";

import { useState } from "react";
import dynamic from "next/dynamic";
import {
  draftInquiryAnswer,
  reviewAnswerDraft,
  approveAnswerDraft,
  sendAnswerDraft,
  getDocumentDownloadUrl,
  getDocumentPagesUrl,
  type AnswerDraftResult,
} from "@/lib/api/client";
import {
  labelVerdict,
  labelAnswerStatus,
  labelRiskFlag,
  labelTone,
  labelChannel,
} from "@/lib/i18n/labels";
import { Badge, Toast, EmptyState } from "@/components/ui";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const PdfViewer = dynamic(() => import("@/components/ui/PdfViewer"), {
  ssr: false,
  loading: () => <div className="flex items-center justify-center p-8 text-sm text-muted-foreground" role="status" aria-live="polite">PDF 뷰어 로딩 중...</div>,
});

interface InquiryAnswerTabProps {
  inquiryId: string;
}

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

export default function InquiryAnswerTab({ inquiryId }: InquiryAnswerTabProps) {
  const [question, setQuestion] = useState("");
  const [answerTone, setAnswerTone] = useState<"professional" | "technical" | "brief">("professional");
  const [answerChannel, setAnswerChannel] = useState<"email" | "messenger">("email");
  const [answerDraft, setAnswerDraft] = useState<AnswerDraftResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; variant: "success" | "error" | "warn" | "info" } | null>(null);

  // Selected evidence for preview
  const [selectedEvidence, setSelectedEvidence] = useState<CitationView | null>(null);

  // Review/Approve/Send actors
  const [reviewActor, setReviewActor] = useState("cs-agent");
  const [reviewComment, setReviewComment] = useState("");
  const [approveActor, setApproveActor] = useState("cs-lead");
  const [approveComment, setApproveComment] = useState("");
  const [sendActor, setSendActor] = useState("cs-sender");

  const handleDraftAnswer = async () => {
    if (!question.trim()) {
      setError("답변 생성을 위한 질문을 입력해 주세요.");
      return;
    }

    setLoading(true);
    setError(null);
    setSelectedEvidence(null);

    try {
      const draft = await draftInquiryAnswer(inquiryId, question.trim(), answerTone, answerChannel);
      setAnswerDraft(draft);
      setToast({ message: `답변 초안 v${draft.version} 생성 완료`, variant: "success" });
    } catch (err) {
      setError(err instanceof Error ? err.message : "답변 초안 생성 중 오류가 발생했습니다.");
      setAnswerDraft(null);
    } finally {
      setLoading(false);
    }
  };

  const handleReview = async () => {
    if (!answerDraft) return;
    setLoading(true);
    setError(null);
    try {
      const reviewed = await reviewAnswerDraft(inquiryId, answerDraft.answerId, reviewActor.trim() || undefined, reviewComment.trim() || undefined);
      setAnswerDraft(reviewed);
      setToast({ message: `리뷰 완료 - v${reviewed.version}`, variant: "success" });
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
      setToast({ message: `승인 완료 - v${approved.version}`, variant: "success" });
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
      const sendRequestId = `${answerDraft.answerId}-send-v1`;
      const sent = await sendAnswerDraft(inquiryId, answerDraft.answerId, sendActor.trim() || undefined, answerChannel, sendRequestId);
      setAnswerDraft(sent);
      setToast({ message: `발송 완료 - v${sent.version}`, variant: "success" });
    } catch (err) {
      setError(err instanceof Error ? err.message : "발송 처리 중 오류가 발생했습니다.");
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

  return (
    <div className="space-y-6">
      {toast && (
        <Toast message={toast.message} variant={toast.variant} onClose={() => setToast(null)} />
      )}

      {/* Draft Generation Form */}
      <div className="rounded-xl border bg-card p-6 shadow-sm space-y-4">
        <h3 className="text-base font-semibold">답변 초안 생성</h3>

        <label className="space-y-1.5 text-sm font-medium">
          질문
          <textarea
            className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            rows={3}
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder="예: 해당 프로토콜이 타당한가요?"
          />
        </label>

        <div className="flex items-center gap-3">
          <label className="flex-1 min-w-[140px] max-w-[200px] space-y-1.5 text-sm font-medium">
            톤
            <select
              className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm"
              value={answerTone}
              onChange={(e) => setAnswerTone(e.target.value as "professional" | "technical" | "brief")}
            >
              <option value="professional">{labelTone("professional")}</option>
              <option value="technical">{labelTone("technical")}</option>
              <option value="brief">{labelTone("brief")}</option>
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
            {loading && <svg className="mr-2 h-4 w-4 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-hidden="true"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" /><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" /></svg>}
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
          {/* Left: Answer + Citations + Workflow */}
          <div>
            <div className="rounded-xl border bg-card p-6 shadow-sm space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-base font-semibold">답변 초안 - v{answerDraft.version}</h3>
                <Badge variant={getAnswerStatusBadgeVariant(answerDraft.status)}>
                  {labelAnswerStatus(answerDraft.status)}
                </Badge>
              </div>

              <hr className="border-t border-border" />

              {/* Status Metrics */}
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
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">채널 / 톤</p>
                  <p className="text-base font-bold tracking-tight text-foreground">
                    {labelChannel(answerDraft.channel)} / {labelTone(answerDraft.tone)}
                  </p>
                </div>
              </div>

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
                <h4 className="text-base font-semibold">답변 본문</h4>
                <div className="rounded-lg border bg-muted/20 p-4 text-sm leading-relaxed whitespace-pre-wrap">
                  {answerDraft.draft}
                </div>
              </div>

              {/* Citations */}
              {answerDraft.citations.length > 0 && (
                <div className="space-y-4">
                  <h4 className="text-base font-semibold">참조 자료 ({answerDraft.citations.length}건)</h4>
                  <div className="space-y-4">
                    {answerDraft.citations.map((c, idx) => {
                      const parsed = parseCitation(c);
                      const isSelected =
                        selectedEvidence?.chunkId === parsed.chunkId;
                      return (
                        <div
                          key={`${parsed.chunkId}-${idx}`}
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

              {/* Format Warnings */}
              {answerDraft.formatWarnings.length > 0 && (
                <div className="rounded-lg border border-warning/30 bg-warning-light px-4 py-3 text-sm text-warning-foreground">
                  <b>형식 경고:</b> {answerDraft.formatWarnings.join(", ")}
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

                {/* Send Action */}
                <div className="flex items-center gap-2">
                  <input
                    className="flex h-9 max-w-[160px] rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm"
                    value={sendActor}
                    onChange={(e) => setSendActor(e.target.value)}
                    placeholder="발송자"
                    aria-label="발송자"
                  />
                  <Button
                    size="sm"
                    onClick={handleSend}
                    disabled={loading || answerDraft.status !== "APPROVED"}
                  >
                    발송
                  </Button>
                </div>
              </div>
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
