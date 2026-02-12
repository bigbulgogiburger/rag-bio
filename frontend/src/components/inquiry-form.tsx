"use client";

import { FormEvent, useState } from "react";
import { createInquiry, uploadInquiryDocument } from "@/lib/api/client";

export default function InquiryForm() {
  const [question, setQuestion] = useState("");
  const [customerChannel, setCustomerChannel] = useState("email");
  const [file, setFile] = useState<File | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setStatus(null);

    try {
      const inquiry = await createInquiry({ question, customerChannel });

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

  return (
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
      {status && <p style={{ margin: 0, color: "#5b5e56" }}>{status}</p>}
    </form>
  );
}
