"use client";

import { FormEvent, useState } from "react";
import {
  analyzeInquiry,
  createInquiry,
  draftInquiryAnswer,
  getInquiry,
  getInquiryIndexingStatus,
  listInquiryDocuments,
  runInquiryIndexing,
  uploadInquiryDocument,
  type AnalyzeResult,
  type AnswerDraftResult,
  type DocumentStatus,
  type InquiryDetail,
  type InquiryIndexingStatus
} from "@/lib/api/client";

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

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setStatus(null);

    try {
      const inquiry = await createInquiry({ question, customerChannel });
      setLookupInquiryId(inquiry.inquiryId);

      if (file) {
        const upload = await uploadInquiryDocument(inquiry.inquiryId, file);
        setStatus(
          `문의 ${inquiry.inquiryId} 생성 완료 / 파일 ${upload.fileName} 업로드 완료 (${upload.status})`
        );
      } else {
        setStatus(`문의 ${inquiry.inquiryId} 생성 완료 (${inquiry.status})`);
      }

      setQuestion("");
      setFile(null);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Unknown error");
    } finally {
      setSubmitting(false);
    }
  };

  const inquiryId = lookupInquiryId.trim();

  const onLookup = async () => {
    if (!inquiryId) {
      setStatus("조회할 inquiryId를 입력해줘.");
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
      setStatus(error instanceof Error ? error.message : "Unknown error");
    } finally {
      setLookupLoading(false);
    }
  };

  const onRunIndexing = async (failedOnly = false) => {
    if (!inquiryId) {
      setStatus("인덱싱 실행할 inquiryId를 입력해줘.");
      return;
    }

    setLookupLoading(true);
    setStatus(null);

    try {
      const run = await runInquiryIndexing(inquiryId, failedOnly);
      const idxStatus = await getInquiryIndexingStatus(inquiryId);
      setIndexingStatus(idxStatus);
      setLookupDocuments(idxStatus.documents);
      setStatus(`인덱싱 실행 완료: 처리 ${run.processed}, 성공 ${run.succeeded}, 실패 ${run.failed}`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Unknown error");
    } finally {
      setLookupLoading(false);
    }
  };

  const onAnalyze = async () => {
    if (!inquiryId) {
      setStatus("분석할 inquiryId를 입력해줘.");
      return;
    }
    if (!analysisQuestion.trim()) {
      setStatus("분석 질문을 입력해줘.");
      return;
    }

    setLookupLoading(true);
    setStatus(null);

    try {
      const result = await analyzeInquiry(inquiryId, analysisQuestion.trim(), 5);
      setAnalysisResult(result);
      setStatus(`분석 완료: verdict=${result.verdict}, confidence=${result.confidence}`);
    } catch (error) {
      setAnalysisResult(null);
      setStatus(error instanceof Error ? error.message : "Unknown error");
    } finally {
      setLookupLoading(false);
    }
  };

  const onDraftAnswer = async () => {
    if (!inquiryId) {
      setStatus("답변 초안을 만들 inquiryId를 입력해줘.");
      return;
    }
    if (!analysisQuestion.trim()) {
      setStatus("답변 초안용 질문을 입력해줘.");
      return;
    }

    setLookupLoading(true);
    setStatus(null);

    try {
      const draft = await draftInquiryAnswer(inquiryId, analysisQuestion.trim(), answerTone, answerChannel);
      setAnswerDraft(draft);
      setStatus(`답변 초안 생성 완료: verdict=${draft.verdict}`);
    } catch (error) {
      setAnswerDraft(null);
      setStatus(error instanceof Error ? error.message : "Unknown error");
    } finally {
      setLookupLoading(false);
    }
  };

  return (
    <div style={{ display: "grid", gap: "1rem" }}>
      <form className="panel" onSubmit={onSubmit} style={{ display: "grid", gap: "0.9rem" }}>
        <h2 style={{ margin: 0 }}>New Customer Inquiry</h2>
        <label style={{ display: "grid", gap: "0.35rem" }}>
          Question
          <textarea
            required
            rows={5}
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder="Enter the customer technical question"
            style={{ resize: "vertical", border: "1px solid #dcded6", borderRadius: "8px", padding: "0.7rem" }}
          />
        </label>
        <label style={{ display: "grid", gap: "0.35rem", maxWidth: "260px" }}>
          Channel
          <select
            value={customerChannel}
            onChange={(event) => setCustomerChannel(event.target.value)}
            style={{ border: "1px solid #dcded6", borderRadius: "8px", padding: "0.55rem" }}
          >
            <option value="email">Email</option>
            <option value="messenger">Messenger</option>
            <option value="portal">Portal</option>
          </select>
        </label>
        <label style={{ display: "grid", gap: "0.35rem" }}>
          Attach document (PDF/DOC/DOCX)
          <input
            type="file"
            accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
          />
        </label>
        <button
          type="submit"
          disabled={submitting}
          style={{
            width: "fit-content",
            border: 0,
            borderRadius: "999px",
            background: "#0f766e",
            color: "#fff",
            padding: "0.65rem 1rem",
            cursor: "pointer"
          }}
        >
          {submitting ? "Submitting..." : "Submit Inquiry"}
        </button>
      </form>

      <section className="panel" style={{ display: "grid", gap: "0.75rem" }}>
        <h3 style={{ margin: 0 }}>Inquiry Status Lookup</h3>
        <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
          <input
            value={lookupInquiryId}
            onChange={(event) => setLookupInquiryId(event.target.value)}
            placeholder="inquiry UUID"
            style={{ minWidth: "340px", border: "1px solid #dcded6", borderRadius: "8px", padding: "0.55rem" }}
          />
          <button type="button" onClick={onLookup} disabled={lookupLoading}>조회</button>
          <button type="button" onClick={() => onRunIndexing(false)} disabled={lookupLoading}>인덱싱 실행</button>
          <button type="button" onClick={() => onRunIndexing(true)} disabled={lookupLoading}>실패건만 재처리</button>
        </div>

        {lookupInquiry && (
          <div style={{ fontSize: "0.95rem" }}>
            <div>상태: <b>{lookupInquiry.status}</b></div>
            <div>채널: {lookupInquiry.customerChannel}</div>
            <div>생성시각: {new Date(lookupInquiry.createdAt).toLocaleString()}</div>
          </div>
        )}

        {indexingStatus && (
          <div style={{ fontSize: "0.92rem" }}>
            <b>인덱싱 요약</b>
            <div>전체 {indexingStatus.total} / 업로드 {indexingStatus.uploaded} / 파싱중 {indexingStatus.parsing} / 파싱완료 {indexingStatus.parsed} / 청킹완료 {indexingStatus.chunked} / 벡터저장완료 {indexingStatus.indexed} / 실패 {indexingStatus.failed}</div>
          </div>
        )}

        {lookupDocuments.length > 0 && (
          <ul style={{ margin: 0, paddingLeft: "1.1rem" }}>
            {lookupDocuments.map((doc) => (
              <li key={doc.documentId}>
                {doc.fileName} / {doc.status} / {(doc.fileSize / 1024).toFixed(1)}KB
                {doc.ocrConfidence != null ? ` / ocr:${doc.ocrConfidence.toFixed(2)}` : ""}
                {doc.chunkCount != null ? ` / chunks:${doc.chunkCount}` : ""}
                {doc.vectorCount != null ? ` / vectors:${doc.vectorCount}` : ""}
                {doc.lastError ? ` / error: ${doc.lastError}` : ""}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="panel" style={{ display: "grid", gap: "0.75rem" }}>
        <h3 style={{ margin: 0 }}>Sprint3 Analysis (Retrieve + Verdict)</h3>
        <label style={{ display: "grid", gap: "0.35rem" }}>
          분석 질문
          <textarea
            rows={3}
            value={analysisQuestion}
            onChange={(event) => setAnalysisQuestion(event.target.value)}
            placeholder="예: 해당 프로토콜이 타당한가요?"
            style={{ resize: "vertical", border: "1px solid #dcded6", borderRadius: "8px", padding: "0.6rem" }}
          />
        </label>
        <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap", alignItems: "center" }}>
          <button type="button" onClick={onAnalyze} disabled={lookupLoading}>근거검색+판정 실행</button>
          <label style={{ display: "flex", gap: "0.35rem", alignItems: "center" }}>
            톤
            <select value={answerTone} onChange={(e) => setAnswerTone(e.target.value as "professional" | "technical" | "brief")}>
              <option value="professional">정중</option>
              <option value="technical">기술</option>
              <option value="brief">요약</option>
            </select>
          </label>
          <label style={{ display: "flex", gap: "0.35rem", alignItems: "center" }}>
            채널
            <select value={answerChannel} onChange={(e) => setAnswerChannel(e.target.value as "email" | "messenger")}>
              <option value="email">이메일</option>
              <option value="messenger">메신저</option>
            </select>
          </label>
          <button type="button" onClick={onDraftAnswer} disabled={lookupLoading}>CS 답변 초안 생성</button>
        </div>

        {analysisResult && (
          <div style={{ fontSize: "0.95rem", display: "grid", gap: "0.35rem" }}>
            <div>Verdict: <b>{analysisResult.verdict}</b> / Confidence: {analysisResult.confidence}</div>
            <div>Reason: {analysisResult.reason}</div>
            {analysisResult.riskFlags.length > 0 && (
              <div>Risk: {analysisResult.riskFlags.join(", ")}</div>
            )}
            {analysisResult.evidences.length > 0 && (
              <ul style={{ margin: 0, paddingLeft: "1.1rem" }}>
                {analysisResult.evidences.map((ev) => (
                  <li key={ev.chunkId}>
                    score={ev.score.toFixed(3)} / chunk={ev.chunkId.slice(0, 8)} / {ev.excerpt}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        {answerDraft && (
          <div style={{ fontSize: "0.95rem", display: "grid", gap: "0.35rem", background: "#f7faf9", padding: "0.7rem", borderRadius: "8px" }}>
            <div><b>Draft Verdict:</b> {answerDraft.verdict} (confidence {answerDraft.confidence})</div>
            <div><b>Draft:</b> {answerDraft.draft}</div>
            {answerDraft.citations.length > 0 && <div><b>Citations:</b> {answerDraft.citations.join(" | ")}</div>}
            {answerDraft.riskFlags.length > 0 && <div><b>Risk:</b> {answerDraft.riskFlags.join(", ")}</div>}
          </div>
        )}
      </section>

      {status && <p style={{ margin: 0, color: "#5b5e56" }}>{status}</p>}
    </div>
  );
}
