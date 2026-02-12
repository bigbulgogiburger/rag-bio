"use client";

import { FormEvent, useState } from "react";
import {
  createInquiry,
  getInquiry,
  getInquiryIndexingStatus,
  listInquiryDocuments,
  runInquiryIndexing,
  uploadInquiryDocument,
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

  const onRunIndexing = async () => {
    if (!inquiryId) {
      setStatus("인덱싱 실행할 inquiryId를 입력해줘.");
      return;
    }

    setLookupLoading(true);
    setStatus(null);

    try {
      const run = await runInquiryIndexing(inquiryId);
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
          <button type="button" onClick={onRunIndexing} disabled={lookupLoading}>인덱싱 실행</button>
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
            <div>전체 {indexingStatus.total} / 업로드 {indexingStatus.uploaded} / 파싱중 {indexingStatus.parsing} / 파싱완료 {indexingStatus.parsed} / 실패 {indexingStatus.failed}</div>
          </div>
        )}

        {lookupDocuments.length > 0 && (
          <ul style={{ margin: 0, paddingLeft: "1.1rem" }}>
            {lookupDocuments.map((doc) => (
              <li key={doc.documentId}>
                {doc.fileName} / {doc.status} / {(doc.fileSize / 1024).toFixed(1)}KB
                {doc.lastError ? ` / error: ${doc.lastError}` : ""}
              </li>
            ))}
          </ul>
        )}
      </section>

      {status && <p style={{ margin: 0, color: "#5b5e56" }}>{status}</p>}
    </div>
  );
}
