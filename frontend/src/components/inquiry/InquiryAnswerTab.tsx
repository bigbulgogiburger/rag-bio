"use client";

import { useState } from "react";
import {
  draftInquiryAnswer,
  reviewAnswerDraft,
  approveAnswerDraft,
  sendAnswerDraft,
  type AnswerDraftResult,
} from "@/lib/api/client";
import {
  labelVerdict,
  labelAnswerStatus,
  labelRiskFlag,
  labelTone,
  labelChannel,
} from "@/lib/i18n/labels";
import { Badge, Toast } from "@/components/ui";

interface InquiryAnswerTabProps {
  inquiryId: string;
}

type CitationView = { chunkId: string; score: number | null };

function parseCitation(raw: string): CitationView {
  const chunkMatch = raw.match(/chunk=([^\s]+)/);
  const scoreMatch = raw.match(/score=([0-9.]+)/);
  return {
    chunkId: chunkMatch?.[1] ?? raw,
    score: scoreMatch ? Number(scoreMatch[1]) : null,
  };
}

export default function InquiryAnswerTab({ inquiryId }: InquiryAnswerTabProps) {
  const [question, setQuestion] = useState("");
  const [answerTone, setAnswerTone] = useState<"professional" | "technical" | "brief">("professional");
  const [answerChannel, setAnswerChannel] = useState<"email" | "messenger">("email");
  const [answerDraft, setAnswerDraft] = useState<AnswerDraftResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; variant: "success" | "error" | "warn" | "info" } | null>(null);

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
      const reviewed = await reviewAnswerDraft(
        inquiryId,
        answerDraft.answerId,
        reviewActor.trim() || undefined,
        reviewComment.trim() || undefined
      );
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
      const approved = await approveAnswerDraft(
        inquiryId,
        answerDraft.answerId,
        approveActor.trim() || undefined,
        approveComment.trim() || undefined
      );
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
      const sent = await sendAnswerDraft(
        inquiryId,
        answerDraft.answerId,
        sendActor.trim() || undefined,
        answerChannel,
        sendRequestId
      );
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
        <Toast
          message={toast.message}
          variant={toast.variant}
          onClose={() => setToast(null)}
        />
      )}

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
          <label className="label" style={{ maxWidth: "180px" }}>
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

          <label className="label" style={{ maxWidth: "180px" }}>
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

      {answerDraft && (
        <div className="card stack">
          <h3 className="section-title">답변 초안 - v{answerDraft.version}</h3>

          <div className="kv">
            <div>
              <b>상태:</b>{" "}
              <Badge variant={getAnswerStatusBadgeVariant(answerDraft.status)}>
                {labelAnswerStatus(answerDraft.status)}
              </Badge>
            </div>
            <div>
              <b>채널:</b> {labelChannel(answerDraft.channel)} | <b>톤:</b> {labelTone(answerDraft.tone)}
            </div>
            <div>
              <b>판정:</b>{" "}
              <Badge variant={getVerdictBadgeVariant(answerDraft.verdict)}>
                {labelVerdict(answerDraft.verdict)}
              </Badge>{" "}
              | <b>신뢰도:</b> {answerDraft.confidence}
            </div>
          </div>

          {/* 타임라인 */}
          <div className="timeline" style={{ marginTop: "var(--space-md)" }}>
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
                  <span className="muted">{done ? "완료" : "대기"}</span>
                </div>
              );
            })}
          </div>

          {/* 답변 본문 */}
          <div style={{ marginTop: "var(--space-md)" }}>
            <b>답변 본문:</b>
            <p
              style={{
                marginTop: "var(--space-xs)",
                padding: "var(--space-md)",
                background: "var(--color-bg-soft)",
                borderRadius: "var(--radius-sm)",
                whiteSpace: "pre-wrap",
              }}
            >
              {answerDraft.draft}
            </p>
          </div>

          {/* 출처 */}
          {answerDraft.citations.length > 0 && (
            <div style={{ marginTop: "var(--space-md)" }}>
              <b>출처 ({answerDraft.citations.length}건):</b>
              <ul style={{ margin: "var(--space-xs) 0 0", paddingLeft: "var(--space-lg)" }}>
                {answerDraft.citations.map((c, idx) => {
                  const parsed = parseCitation(c);
                  return (
                    <li key={`${parsed.chunkId}-${idx}`}>
                      chunk={parsed.chunkId.slice(0, 8)}
                      {parsed.score != null ? ` · score=${parsed.score.toFixed(3)}` : ""}
                    </li>
                  );
                })}
              </ul>
            </div>
          )}

          {/* 리스크 플래그 */}
          {answerDraft.riskFlags.length > 0 && (
            <div style={{ marginTop: "var(--space-md)" }}>
              <b>리스크 플래그:</b>{" "}
              {answerDraft.riskFlags.map((flag, idx) => (
                <Badge key={idx} variant="danger" style={{ marginRight: "0.5rem" }}>
                  {labelRiskFlag(flag)}
                </Badge>
              ))}
            </div>
          )}

          {/* 형식 경고 */}
          {answerDraft.formatWarnings.length > 0 && (
            <div style={{ marginTop: "var(--space-md)", color: "var(--color-warn)" }}>
              <b>형식 경고:</b> {answerDraft.formatWarnings.join(", ")}
            </div>
          )}

          {/* 리뷰 정보 */}
          {answerDraft.reviewedBy && (
            <div style={{ marginTop: "var(--space-md)" }}>
              <b>리뷰:</b> {answerDraft.reviewedBy}
              {answerDraft.reviewComment && ` - ${answerDraft.reviewComment}`}
            </div>
          )}

          {/* 승인 정보 */}
          {answerDraft.approvedBy && (
            <div style={{ marginTop: "var(--space-md)" }}>
              <b>승인:</b> {answerDraft.approvedBy}
              {answerDraft.approveComment && ` - ${answerDraft.approveComment}`}
            </div>
          )}

          {/* 발송 정보 */}
          {answerDraft.sentBy && (
            <div style={{ marginTop: "var(--space-md)" }}>
              <b>발송:</b> {answerDraft.sentBy} | {answerDraft.sendChannel} | {answerDraft.sendMessageId}
            </div>
          )}

          {/* 워크플로우 액션 */}
          <div className="stack" style={{ marginTop: "var(--space-lg)" }}>
            <h4 className="section-title">워크플로우 액션</h4>

            {/* 리뷰 */}
            <div className="row">
              <input
                className="input"
                value={reviewActor}
                onChange={(e) => setReviewActor(e.target.value)}
                placeholder="리뷰어"
                style={{ maxWidth: "180px" }}
              />
              <input
                className="input"
                value={reviewComment}
                onChange={(e) => setReviewComment(e.target.value)}
                placeholder="리뷰 코멘트"
                style={{ flex: 1, minWidth: "260px" }}
              />
              <button
                className="btn"
                onClick={handleReview}
                disabled={loading || answerDraft.status !== "DRAFT"}
              >
                리뷰
              </button>
            </div>

            {/* 승인 */}
            <div className="row">
              <input
                className="input"
                value={approveActor}
                onChange={(e) => setApproveActor(e.target.value)}
                placeholder="승인자"
                style={{ maxWidth: "180px" }}
              />
              <input
                className="input"
                value={approveComment}
                onChange={(e) => setApproveComment(e.target.value)}
                placeholder="승인 코멘트"
                style={{ flex: 1, minWidth: "260px" }}
              />
              <button
                className="btn"
                onClick={handleApprove}
                disabled={loading || !["DRAFT", "REVIEWED"].includes(answerDraft.status)}
              >
                승인
              </button>
            </div>

            {/* 발송 */}
            <div className="row">
              <input
                className="input"
                value={sendActor}
                onChange={(e) => setSendActor(e.target.value)}
                placeholder="발송자"
                style={{ maxWidth: "180px" }}
              />
              <button
                className="btn btn-primary"
                onClick={handleSend}
                disabled={loading || answerDraft.status !== "APPROVED"}
              >
                발송
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
