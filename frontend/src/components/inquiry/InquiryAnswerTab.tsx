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

const PdfViewer = dynamic(() => import("@/components/ui/PdfViewer"), {
  ssr: false,
  loading: () => <div className="pdf-loading">PDF 뷰어 로딩 중...</div>,
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
  const fileMatch = raw.match(/fileName=(\S+)/);
  const psMatch = raw.match(/pageStart=(\d+)/);
  const peMatch = raw.match(/pageEnd=(\d+)/);
  return {
    chunkId: chunkMatch?.[1] ?? raw,
    score: scoreMatch ? Number(scoreMatch[1]) : null,
    documentId: docMatch?.[1] ?? null,
    fileName: fileMatch?.[1] ?? null,
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
    <div className="stack">
      {toast && (
        <Toast message={toast.message} variant={toast.variant} onClose={() => setToast(null)} />
      )}

      {/* Draft Generation Form */}
      <div className="card stack">
        <h3 className="section-title">답변 초안 생성</h3>

        <label className="label">
          질문
          <textarea
            className="textarea"
            rows={3}
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder="예: 해당 프로토콜이 타당한가요?"
          />
        </label>

        <div className="row">
          <label className="label" style={{ flex: 1, minWidth: "140px", maxWidth: "200px" }}>
            톤
            <select
              className="select"
              value={answerTone}
              onChange={(e) => setAnswerTone(e.target.value as "professional" | "technical" | "brief")}
            >
              <option value="professional">{labelTone("professional")}</option>
              <option value="technical">{labelTone("technical")}</option>
              <option value="brief">{labelTone("brief")}</option>
            </select>
          </label>

          <label className="label" style={{ flex: 1, minWidth: "140px", maxWidth: "200px" }}>
            채널
            <select
              className="select"
              value={answerChannel}
              onChange={(e) => setAnswerChannel(e.target.value as "email" | "messenger")}
            >
              <option value="email">{labelChannel("email")}</option>
              <option value="messenger">{labelChannel("messenger")}</option>
            </select>
          </label>

          <button
            className="btn btn-primary"
            onClick={handleDraftAnswer}
            disabled={loading}
            style={{ alignSelf: "flex-end" }}
          >
            {loading ? "생성 중..." : "답변 초안 생성"}
          </button>
        </div>

        {error && (
          <p className="status-banner status-danger" role="alert">
            {error}
          </p>
        )}
      </div>

      {/* Draft Result - Split Pane */}
      {answerDraft && (
        <div className="split-pane">
          {/* Left: Answer + Citations + Workflow */}
          <div className="split-left">
            <div className="card stack">
              <div className="page-header">
                <h3 className="section-title">답변 초안 - v{answerDraft.version}</h3>
                <Badge variant={getAnswerStatusBadgeVariant(answerDraft.status)}>
                  {labelAnswerStatus(answerDraft.status)}
                </Badge>
              </div>

              <hr className="divider" />

              {/* Status Metrics */}
              <div className="metrics-grid cols-3">
                <div className="metric-card">
                  <p className="metric-label">판정</p>
                  <div className="metric-value" style={{ fontSize: "var(--font-size-lg)" }}>
                    <Badge variant={getVerdictBadgeVariant(answerDraft.verdict)}>
                      {labelVerdict(answerDraft.verdict)}
                    </Badge>
                  </div>
                </div>
                <div className="metric-card">
                  <p className="metric-label">신뢰도</p>
                  <p className="metric-value">{answerDraft.confidence}</p>
                </div>
                <div className="metric-card">
                  <p className="metric-label">채널 / 톤</p>
                  <p className="metric-value" style={{ fontSize: "var(--font-size-md)" }}>
                    {labelChannel(answerDraft.channel)} / {labelTone(answerDraft.tone)}
                  </p>
                </div>
              </div>

              {/* Timeline */}
              <div className="timeline">
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
                      className={`timeline-row${done ? " done" : ""}${active ? " active" : ""}`}
                    >
                      <span className="timeline-dot" />
                      <span className="timeline-title">{step.label}</span>
                      <span className="muted" style={{ fontSize: "var(--font-size-xs)" }}>{done ? "완료" : "대기"}</span>
                    </div>
                  );
                })}
              </div>

              <hr className="divider" />

              {/* Answer Body */}
              <div className="stack">
                <h4 className="section-title">답변 본문</h4>
                <div className="draft-box" style={{ whiteSpace: "pre-wrap" }}>
                  {answerDraft.draft}
                </div>
              </div>

              {/* Citations */}
              {answerDraft.citations.length > 0 && (
                <div className="stack">
                  <h4 className="section-title">참조 자료 ({answerDraft.citations.length}건)</h4>
                  <div className="stack">
                    {answerDraft.citations.map((c, idx) => {
                      const parsed = parseCitation(c);
                      const isSelected =
                        selectedEvidence?.chunkId === parsed.chunkId;
                      return (
                        <div
                          key={`${parsed.chunkId}-${idx}`}
                          className={`evidence-item clickable${isSelected ? " selected" : ""}`}
                          onClick={() => setSelectedEvidence(parsed)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter" || e.key === " ") setSelectedEvidence(parsed);
                          }}
                          role="button"
                          tabIndex={0}
                        >
                          <div className="row" style={{ alignItems: "center", gap: "var(--space-sm)" }}>
                            <span style={{ fontWeight: 500, fontSize: "var(--font-size-sm)" }}>
                              {formatCitationLabel(parsed)}
                            </span>
                            {parsed.score != null && (
                              <span className="muted" style={{ fontSize: "var(--font-size-xs)" }}>
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
                <div className="status-banner status-danger">
                  <b>리스크 플래그:</b>{" "}
                  {answerDraft.riskFlags.map((flag, idx) => (
                    <Badge key={idx} variant="danger" style={{ marginLeft: idx > 0 ? "0.25rem" : "0.5rem" }}>
                      {labelRiskFlag(flag)}
                    </Badge>
                  ))}
                </div>
              )}

              {/* Format Warnings */}
              {answerDraft.formatWarnings.length > 0 && (
                <div className="status-banner status-warn">
                  <b>형식 경고:</b> {answerDraft.formatWarnings.join(", ")}
                </div>
              )}

              {/* Review Info */}
              {answerDraft.reviewedBy && (
                <div className="evidence-item">
                  <b>리뷰:</b> {answerDraft.reviewedBy}
                  {answerDraft.reviewComment && <span className="muted"> - {answerDraft.reviewComment}</span>}
                </div>
              )}

              {/* Approval Info */}
              {answerDraft.approvedBy && (
                <div className="evidence-item">
                  <b>승인:</b> {answerDraft.approvedBy}
                  {answerDraft.approveComment && <span className="muted"> - {answerDraft.approveComment}</span>}
                </div>
              )}

              {/* Sent Info */}
              {answerDraft.sentBy && (
                <div className="status-banner status-success">
                  <b>발송:</b> {answerDraft.sentBy} | {answerDraft.sendChannel} | {answerDraft.sendMessageId}
                </div>
              )}

              <hr className="divider" />

              {/* Workflow Actions */}
              <div className="stack">
                <h4 className="section-title">워크플로우 액션</h4>

                {/* Review Action */}
                <div className="action-group">
                  <input
                    className="input"
                    value={reviewActor}
                    onChange={(e) => setReviewActor(e.target.value)}
                    placeholder="리뷰어"
                    style={{ maxWidth: "160px" }}
                  />
                  <input
                    className="input"
                    value={reviewComment}
                    onChange={(e) => setReviewComment(e.target.value)}
                    placeholder="리뷰 코멘트"
                    style={{ flex: 1 }}
                  />
                  <button
                    className="btn btn-sm"
                    onClick={handleReview}
                    disabled={loading || answerDraft.status !== "DRAFT"}
                  >
                    리뷰
                  </button>
                </div>

                {/* Approve Action */}
                <div className="action-group">
                  <input
                    className="input"
                    value={approveActor}
                    onChange={(e) => setApproveActor(e.target.value)}
                    placeholder="승인자"
                    style={{ maxWidth: "160px" }}
                  />
                  <input
                    className="input"
                    value={approveComment}
                    onChange={(e) => setApproveComment(e.target.value)}
                    placeholder="승인 코멘트"
                    style={{ flex: 1 }}
                  />
                  <button
                    className="btn btn-sm"
                    onClick={handleApprove}
                    disabled={loading || !["DRAFT", "REVIEWED"].includes(answerDraft.status)}
                  >
                    승인
                  </button>
                </div>

                {/* Send Action */}
                <div className="action-group">
                  <input
                    className="input"
                    value={sendActor}
                    onChange={(e) => setSendActor(e.target.value)}
                    placeholder="발송자"
                    style={{ maxWidth: "160px" }}
                  />
                  <button
                    className="btn btn-primary btn-sm"
                    onClick={handleSend}
                    disabled={loading || answerDraft.status !== "APPROVED"}
                  >
                    발송
                  </button>
                </div>
              </div>
            </div>
          </div>

          {/* Right: Document Preview */}
          <div className="split-right">
            <div className="card stack">
              <h4 className="section-title">문서 미리보기</h4>
              <hr className="divider" />
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
                  <div className="stack" style={{ alignItems: "center", padding: "var(--space-lg)" }}>
                    <p className="muted" style={{ textAlign: "center" }}>
                      {selectedEvidence.fileName ?? "문서"} 파일은 PDF가 아니므로 미리보기가 지원되지 않습니다.
                    </p>
                    <a
                      href={getDocumentDownloadUrl(selectedEvidence.documentId)}
                      className="btn btn-primary"
                      download
                    >
                      원본 파일 다운로드
                    </a>
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
