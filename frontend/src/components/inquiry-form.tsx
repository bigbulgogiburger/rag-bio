"use client";

import { FormEvent, useState } from "react";
import {
  analyzeInquiry,
  approveAnswerDraft,
  createInquiry,
  draftInquiryAnswer,
  getInquiry,
  getInquiryIndexingStatus,
  listAnswerDraftHistory,
  listInquiryDocuments,
  reviewAnswerDraft,
  runInquiryIndexing,
  sendAnswerDraft,
  uploadInquiryDocument,
  type AnalyzeResult,
  type AnswerDraftResult,
  type DocumentStatus,
  type InquiryDetail,
  type InquiryIndexingStatus
} from "@/lib/api/client";

type CitationView = { chunkId: string; score: number | null };

function parseCitation(raw: string): CitationView {
  const chunkMatch = raw.match(/chunk=([^\s]+)/);
  const scoreMatch = raw.match(/score=([0-9.]+)/);
  return {
    chunkId: chunkMatch?.[1] ?? raw,
    score: scoreMatch ? Number(scoreMatch[1]) : null
  };
}

function mapStatusLabel(status: string): string {
  const table: Record<string, string> = {
    RECEIVED: "접수됨",
    UPLOADED: "업로드됨",
    PARSING: "파싱 중",
    PARSED: "파싱 완료",
    CHUNKED: "청킹 완료",
    INDEXED: "벡터 저장 완료",
    FAILED: "실패",
    DRAFT: "초안",
    REVIEWED: "리뷰됨",
    APPROVED: "승인됨",
    SENT: "발송완료"
  };
  return table[status] ?? status;
}

function mapVerdictLabel(verdict: string): string {
  const table: Record<string, string> = {
    SUPPORTED: "근거 충분",
    REFUTED: "반박됨",
    CONDITIONAL: "조건부"
  };
  return table[verdict] ?? verdict;
}

function statusToneClass(message: string): string {
  if (message.includes("오류") || message.includes("실패") || message.includes("Forbidden") || message.includes("Unknown")) {
    return "status-danger";
  }
  if (message.includes("주의") || message.includes("경고")) {
    return "status-warn";
  }
  if (message.includes("완료") || message.includes("성공")) {
    return "status-success";
  }
  return "status-info";
}

function badgeClassByStatus(status: string): string {
  if (["SENT", "APPROVED", "INDEXED", "PARSED", "CHUNKED"].includes(status)) return "badge badge-success";
  if (["FAILED"].includes(status)) return "badge badge-danger";
  if (["PARSING", "REVIEWED"].includes(status)) return "badge badge-warn";
  return "badge badge-info";
}

export default function InquiryForm() {
  const [question, setQuestion] = useState("");
  const [customerChannel, setCustomerChannel] = useState("email");
  const [file, setFile] = useState<File | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [lookupInquiryId, setLookupInquiryId] = useState("");
  const [lookupLoading, setLookupLoading] = useState(false);
  const [lookupInquiry, setLookupInquiry] = useState<InquiryDetail | null>(null);
  const [lookupDocuments, setLookupDocuments] = useState<DocumentStatus[]>([]);
  const [indexingStatus, setIndexingStatus] = useState<InquiryIndexingStatus | null>(null);
  const [analysisQuestion, setAnalysisQuestion] = useState("");
  const [analysisResult, setAnalysisResult] = useState<AnalyzeResult | null>(null);
  const [answerTone, setAnswerTone] = useState<"professional" | "technical" | "brief">("professional");
  const [answerChannel, setAnswerChannel] = useState<"email" | "messenger">("email");
  const [answerDraft, setAnswerDraft] = useState<AnswerDraftResult | null>(null);
  const [answerHistory, setAnswerHistory] = useState<AnswerDraftResult[]>([]);
  const [reviewActor, setReviewActor] = useState("cs-agent");
  const [reviewComment, setReviewComment] = useState("");
  const [approveActor, setApproveActor] = useState("cs-lead");
  const [approveComment, setApproveComment] = useState("");
  const [sendActor, setSendActor] = useState("cs-sender");

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setStatus(null);

    try {
      const inquiry = await createInquiry({ question, customerChannel });
      setLookupInquiryId(inquiry.inquiryId);

      if (file) {
        const upload = await uploadInquiryDocument(inquiry.inquiryId, file);
        setStatus(`문의 ${inquiry.inquiryId} 생성 완료 / 파일 ${upload.fileName} 업로드 완료 (${mapStatusLabel(upload.status)})`);
      } else {
        setStatus(`문의 ${inquiry.inquiryId} 생성 완료 (${mapStatusLabel(inquiry.status)})`);
      }

      setQuestion("");
      setFile(null);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "알 수 없는 오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  const inquiryId = lookupInquiryId.trim();

  const onLookup = async () => {
    if (!inquiryId) {
      setStatus("조회할 문의 ID를 입력해 주세요.");
      return;
    }

    setLookupLoading(true);
    setStatus(null);

    try {
      const [inquiry, documents, idxStatus] = await Promise.all([
        getInquiry(inquiryId),
        listInquiryDocuments(inquiryId),
        getInquiryIndexingStatus(inquiryId)
      ]);
      setLookupInquiry(inquiry);
      setLookupDocuments(documents);
      setIndexingStatus(idxStatus);
      setStatus(`문의 ${inquiry.inquiryId} 상태 조회 완료`);
    } catch (error) {
      setLookupInquiry(null);
      setLookupDocuments([]);
      setIndexingStatus(null);
      setStatus(error instanceof Error ? error.message : "알 수 없는 오류가 발생했습니다.");
    } finally {
      setLookupLoading(false);
    }
  };

  const onRunIndexing = async (failedOnly = false) => {
    if (!inquiryId) {
      setStatus("인덱싱할 문의 ID를 먼저 입력해 주세요.");
      return;
    }

    setLookupLoading(true);
    setStatus(null);

    try {
      const run = await runInquiryIndexing(inquiryId, failedOnly);
      const idxStatus = await getInquiryIndexingStatus(inquiryId);
      setIndexingStatus(idxStatus);
      setLookupDocuments(idxStatus.documents);
      setStatus(`인덱싱 완료 · 처리 ${run.processed}건 / 성공 ${run.succeeded}건 / 실패 ${run.failed}건`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "알 수 없는 오류가 발생했습니다.");
    } finally {
      setLookupLoading(false);
    }
  };

  const onAnalyze = async () => {
    if (!inquiryId) {
      setStatus("분석할 문의 ID를 입력해 주세요.");
      return;
    }
    if (!analysisQuestion.trim()) {
      setStatus("분석 질문을 입력해 주세요.");
      return;
    }

    setLookupLoading(true);
    setStatus(null);

    try {
      const result = await analyzeInquiry(inquiryId, analysisQuestion.trim(), 5);
      setAnalysisResult(result);
      setStatus(`분석 완료 · 판정 ${mapVerdictLabel(result.verdict)} / 신뢰도 ${result.confidence}`);
    } catch (error) {
      setAnalysisResult(null);
      setStatus(error instanceof Error ? error.message : "알 수 없는 오류가 발생했습니다.");
    } finally {
      setLookupLoading(false);
    }
  };

  const onDraftAnswer = async () => {
    if (!inquiryId) {
      setStatus("답변 초안을 생성할 문의 ID를 입력해 주세요.");
      return;
    }
    if (!analysisQuestion.trim()) {
      setStatus("답변 초안용 질문을 입력해 주세요.");
      return;
    }

    setLookupLoading(true);
    setStatus(null);

    try {
      const draft = await draftInquiryAnswer(inquiryId, analysisQuestion.trim(), answerTone, answerChannel);
      const history = await listAnswerDraftHistory(inquiryId);
      setAnswerDraft(draft);
      setAnswerHistory(history);
      setStatus(`답변 초안 생성 완료 · v${draft.version} / ${mapStatusLabel(draft.status)}`);
    } catch (error) {
      setAnswerDraft(null);
      setStatus(error instanceof Error ? error.message : "알 수 없는 오류가 발생했습니다.");
    } finally {
      setLookupLoading(false);
    }
  };

  const onReviewDraft = async () => {
    if (!inquiryId || !answerDraft) return;
    setLookupLoading(true);
    setStatus(null);
    try {
      const reviewed = await reviewAnswerDraft(inquiryId, answerDraft.answerId, reviewActor.trim() || undefined, reviewComment.trim() || undefined);
      const history = await listAnswerDraftHistory(inquiryId);
      setAnswerDraft(reviewed);
      setAnswerHistory(history);
      setStatus(`리뷰 완료 · v${reviewed.version} (${mapStatusLabel(reviewed.status)})`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "알 수 없는 오류가 발생했습니다.");
    } finally {
      setLookupLoading(false);
    }
  };

  const onApproveDraft = async () => {
    if (!inquiryId || !answerDraft) return;
    setLookupLoading(true);
    setStatus(null);
    try {
      const approved = await approveAnswerDraft(inquiryId, answerDraft.answerId, approveActor.trim() || undefined, approveComment.trim() || undefined);
      const history = await listAnswerDraftHistory(inquiryId);
      setAnswerDraft(approved);
      setAnswerHistory(history);
      setStatus(`승인 완료 · v${approved.version} (${mapStatusLabel(approved.status)})`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "알 수 없는 오류가 발생했습니다.");
    } finally {
      setLookupLoading(false);
    }
  };

  const onSendDraft = async () => {
    if (!inquiryId || !answerDraft) return;
    setLookupLoading(true);
    setStatus(null);
    try {
      const sendRequestId = `${answerDraft.answerId}-send-v1`;
      const sent = await sendAnswerDraft(inquiryId, answerDraft.answerId, sendActor.trim() || undefined, answerChannel, sendRequestId);
      const history = await listAnswerDraftHistory(inquiryId);
      setAnswerDraft(sent);
      setAnswerHistory(history);
      setStatus(`발송 처리 완료 · v${sent.version} (${mapStatusLabel(sent.status)})`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "알 수 없는 오류가 발생했습니다.");
    } finally {
      setLookupLoading(false);
    }
  };

  return (
    <div className="stack">
      <form className="card stack" onSubmit={onSubmit}>
        <h2 className="card-title">새 고객 문의 등록</h2>
        <label className="label">
          문의 내용
          <textarea
            className="textarea"
            required
            rows={5}
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder="고객 기술 문의 내용을 입력하세요"
          />
        </label>

        <label className="label" style={{ maxWidth: 280 }}>
          채널
          <select className="select" value={customerChannel} onChange={(event) => setCustomerChannel(event.target.value)}>
            <option value="email">이메일</option>
            <option value="messenger">메신저</option>
            <option value="portal">포털</option>
          </select>
        </label>

        <label className="label">
          문서 첨부 (PDF/DOC/DOCX)
          <input
            className="input"
            type="file"
            accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
          />
        </label>

        <button className="btn btn-primary btn-pill" type="submit" disabled={submitting}>
          {submitting ? "등록 중..." : "문의 등록"}
        </button>
      </form>

      <section className="card stack">
        <h3 className="section-title">문의 상태 조회</h3>
        <div className="row">
          <input
            className="input"
            value={lookupInquiryId}
            onChange={(event) => setLookupInquiryId(event.target.value)}
            placeholder="문의 UUID"
            style={{ minWidth: 340 }}
          />
          <button className="btn" type="button" onClick={onLookup} disabled={lookupLoading}>조회</button>
          <button className="btn" type="button" onClick={() => onRunIndexing(false)} disabled={lookupLoading}>인덱싱 실행</button>
          <button className="btn" type="button" onClick={() => onRunIndexing(true)} disabled={lookupLoading}>실패 건 재처리</button>
        </div>

        {lookupInquiry && (
          <div className="kv">
            <div>상태: <span className={badgeClassByStatus(lookupInquiry.status)}>{mapStatusLabel(lookupInquiry.status)}</span></div>
            <div>채널: {lookupInquiry.customerChannel}</div>
            <div>생성 시각: {new Date(lookupInquiry.createdAt).toLocaleString()}</div>
          </div>
        )}

        {indexingStatus && (
          <div className="kv">
            <b>인덱싱 요약</b>
            <div>
              전체 {indexingStatus.total} / 업로드 {indexingStatus.uploaded} / 파싱 중 {indexingStatus.parsing} /
              파싱 완료 {indexingStatus.parsed} / 청킹 완료 {indexingStatus.chunked} / 벡터 저장 완료 {indexingStatus.indexed} /
              실패 {indexingStatus.failed}
            </div>
          </div>
        )}

        {lookupDocuments.length > 0 && (
          <ul style={{ margin: 0, paddingLeft: "1.1rem" }}>
            {lookupDocuments.map((doc) => (
              <li key={doc.documentId}>
                {doc.fileName} · <span className={badgeClassByStatus(doc.status)}>{mapStatusLabel(doc.status)}</span> · {(doc.fileSize / 1024).toFixed(1)}KB
                {doc.ocrConfidence != null ? ` · OCR ${doc.ocrConfidence.toFixed(2)}` : ""}
                {doc.chunkCount != null ? ` · 청크 ${doc.chunkCount}` : ""}
                {doc.vectorCount != null ? ` · 벡터 ${doc.vectorCount}` : ""}
                {doc.lastError ? ` · 오류: ${doc.lastError}` : ""}
              </li>
            ))}
          </ul>
        )}

        {!lookupInquiry && !lookupLoading && inquiryId && (
          <p className="muted" style={{ margin: 0 }}>조회 결과가 없습니다. 문의 ID를 다시 확인해 주세요.</p>
        )}
      </section>

      <section className="card stack">
        <h3 className="section-title">분석 및 답변 생성</h3>
        <label className="label">
          분석 질문
          <textarea
            className="textarea"
            rows={3}
            value={analysisQuestion}
            onChange={(event) => setAnalysisQuestion(event.target.value)}
            placeholder="예: 해당 프로토콜이 타당한가요?"
          />
        </label>

        <div className="row">
          <button className="btn" type="button" onClick={onAnalyze} disabled={lookupLoading}>근거 검색 + 판정</button>
          <label className="row" style={{ fontWeight: 600 }}>
            톤
            <select className="select" value={answerTone} onChange={(e) => setAnswerTone(e.target.value as "professional" | "technical" | "brief")}>
              <option value="professional">정중</option>
              <option value="technical">기술</option>
              <option value="brief">요약</option>
            </select>
          </label>
          <label className="row" style={{ fontWeight: 600 }}>
            채널
            <select className="select" value={answerChannel} onChange={(e) => setAnswerChannel(e.target.value as "email" | "messenger")}>
              <option value="email">이메일</option>
              <option value="messenger">메신저</option>
            </select>
          </label>
          <button className="btn btn-primary" type="button" onClick={onDraftAnswer} disabled={lookupLoading}>답변 초안 생성</button>
        </div>

        {analysisResult && (
          <div className="status-banner">
            <div><b>판정:</b> {mapVerdictLabel(analysisResult.verdict)} / <b>신뢰도:</b> {analysisResult.confidence}</div>
            <div><b>사유:</b> {analysisResult.reason}</div>
            {analysisResult.riskFlags.length > 0 && <div><b>주의:</b> {analysisResult.riskFlags.join(", ")}</div>}
            {analysisResult.evidences.length > 0 && (
              <ul style={{ marginBottom: 0, paddingLeft: "1.1rem" }}>
                {analysisResult.evidences.map((ev) => (
                  <li key={ev.chunkId}>점수 {ev.score.toFixed(3)} · chunk {ev.chunkId.slice(0, 8)} · {ev.excerpt}</li>
                ))}
              </ul>
            )}
          </div>
        )}

        {answerDraft && (
          <div className="status-banner" style={{ display: "grid", gap: ".55rem" }}>
            <div><b>초안:</b> v{answerDraft.version} · <span className={badgeClassByStatus(answerDraft.status)}>{mapStatusLabel(answerDraft.status)}</span> · {answerDraft.channel} · {answerDraft.tone}</div>

            <div className="timeline">
              {[
                { key: "DRAFT", label: "초안 생성" },
                { key: "REVIEWED", label: "리뷰 완료" },
                { key: "APPROVED", label: "승인 완료" },
                { key: "SENT", label: "발송 완료" }
              ].map((step) => {
                const order: Record<string, number> = { DRAFT: 0, REVIEWED: 1, APPROVED: 2, SENT: 3 };
                const current = order[answerDraft.status] ?? 0;
                const idx = order[step.key] ?? 0;
                const done = idx <= current;
                const active = idx === current;
                return (
                  <div key={step.key} className={`timeline-row${done ? " done" : ""}${active ? " active" : ""}`}>
                    <span className="timeline-dot" />
                    <span className="timeline-title">{step.label}</span>
                    <span className="muted">{done ? "완료" : "대기"}</span>
                  </div>
                );
              })}
            </div>

            <div><b>판정:</b> {mapVerdictLabel(answerDraft.verdict)} (신뢰도 {answerDraft.confidence})</div>
            <div><b>답변:</b> {answerDraft.draft}</div>

            {answerDraft.citations.length > 0 && (
              <div>
                <b>출처:</b>
                <ul style={{ marginBottom: 0, paddingLeft: "1.1rem" }}>
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

            {answerDraft.riskFlags.length > 0 && <div><b>리스크:</b> {answerDraft.riskFlags.join(", ")}</div>}
            {answerDraft.formatWarnings.length > 0 && <div style={{ color: "#b45309" }}><b>형식 경고:</b> {answerDraft.formatWarnings.join(", ")}</div>}
            {answerDraft.reviewedBy && <div><b>리뷰:</b> {answerDraft.reviewedBy} / {answerDraft.reviewComment ?? ""}</div>}
            {answerDraft.approvedBy && <div><b>승인:</b> {answerDraft.approvedBy} / {answerDraft.approveComment ?? ""}</div>}
            {answerDraft.sentBy && <div><b>발송:</b> {answerDraft.sentBy} / {answerDraft.sendChannel} / {answerDraft.sendMessageId}</div>}

            <div className="stack">
              <div className="row">
                <input className="input" value={reviewActor} onChange={(e) => setReviewActor(e.target.value)} placeholder="리뷰어" style={{ maxWidth: 180 }} />
                <input className="input" value={reviewComment} onChange={(e) => setReviewComment(e.target.value)} placeholder="리뷰 코멘트" style={{ minWidth: 260 }} />
                <button className="btn" type="button" onClick={onReviewDraft} disabled={lookupLoading || answerDraft.status === "APPROVED"}>리뷰</button>
              </div>

              <div className="row">
                <input className="input" value={approveActor} onChange={(e) => setApproveActor(e.target.value)} placeholder="승인자" style={{ maxWidth: 180 }} />
                <input className="input" value={approveComment} onChange={(e) => setApproveComment(e.target.value)} placeholder="승인 코멘트" style={{ minWidth: 260 }} />
                <button className="btn" type="button" onClick={onApproveDraft} disabled={lookupLoading || answerDraft.status === "APPROVED" || answerDraft.status === "SENT"}>승인</button>
              </div>

              <div className="row">
                <input className="input" value={sendActor} onChange={(e) => setSendActor(e.target.value)} placeholder="발송자" style={{ maxWidth: 180 }} />
                <button className="btn btn-primary" type="button" onClick={onSendDraft} disabled={lookupLoading || answerDraft.status !== "APPROVED"}>발송</button>
              </div>
            </div>

            {answerHistory.length > 0 && (
              <div>
                <b>버전 이력:</b>
                <ul style={{ marginBottom: 0, paddingLeft: "1.1rem" }}>
                  {answerHistory.map((item) => (
                    <li key={item.answerId}>v{item.version} · {mapStatusLabel(item.status)} · {mapVerdictLabel(item.verdict)} · {item.channel} · {item.tone}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </section>

      {status && <p className={`status-banner ${statusToneClass(status)}`} role="status" aria-live="polite">{status}</p>}
    </div>
  );
}
