"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import {
  createInquiry,
  uploadInquiryDocument,
} from "@/lib/api/client";
import { labelDocStatus } from "@/lib/i18n/labels";
import { Toast } from "@/components/ui";

export default function InquiryCreateForm() {
  const router = useRouter();
  const [question, setQuestion] = useState("");
  const [customerChannel, setCustomerChannel] = useState("email");
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [toast, setToast] = useState<{ message: string; variant: "success" | "error" | "warn" | "info" } | null>(null);

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setToast(null);

    try {
      const inquiry = await createInquiry({ question, customerChannel });

      if (file) {
        const upload = await uploadInquiryDocument(inquiry.inquiryId, file);
        setToast({
          message: `문의 ${inquiry.inquiryId.slice(0, 8)} 생성 완료 / 파일 ${upload.fileName} 업로드 완료 (${labelDocStatus(upload.status)})`,
          variant: "success",
        });
      } else {
        setToast({
          message: `문의 ${inquiry.inquiryId.slice(0, 8)} 생성 완료 (${labelDocStatus(inquiry.status)})`,
          variant: "success",
        });
      }

      // Reset form
      setQuestion("");
      setFile(null);

      // Redirect to inquiry detail page after 2 seconds
      setTimeout(() => {
        router.push(`/inquiries/${inquiry.inquiryId}`);
      }, 2000);
    } catch (error) {
      setToast({
        message: error instanceof Error ? error.message : "알 수 없는 오류가 발생했습니다.",
        variant: "error",
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="stack">
      {toast && (
        <Toast
          message={toast.message}
          variant={toast.variant}
          onClose={() => setToast(null)}
          duration={toast.variant === "success" ? 2000 : 5000}
        />
      )}

      <form className="card stack" onSubmit={onSubmit}>
        <h2 className="card-title">새 고객 문의 등록</h2>
        <p className="muted">고객의 기술 문의를 등록하고 문서를 첨부할 수 있습니다.</p>

        <hr className="divider" />

        <div className="stack">
          <h3 className="section-title">문의 내용</h3>
          <label className="label">
            질문
            <textarea
              className="textarea"
              required
              rows={5}
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="고객 기술 문의 내용을 입력하세요"
            />
          </label>

          <label className="label">
            채널
            <select
              className="select"
              value={customerChannel}
              onChange={(event) => setCustomerChannel(event.target.value)}
            >
              <option value="email">이메일</option>
              <option value="messenger">메신저</option>
              <option value="portal">포털</option>
            </select>
          </label>
        </div>

        <hr className="divider" />

        <div className="stack">
          <h3 className="section-title">문서 첨부</h3>
          <label className="label">
            파일 (PDF/DOC/DOCX)
            <div className="file-input-wrapper">
              <input
                type="file"
                accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                onChange={(event) => setFile(event.target.files?.[0] ?? null)}
              />
              {file ? (
                <p className="muted">{file.name} ({(file.size / 1024).toFixed(1)} KB)</p>
              ) : (
                <p className="muted">클릭하여 파일을 선택하거나 드래그하세요</p>
              )}
            </div>
          </label>
        </div>

        <hr className="divider" />

        <button
          className="btn btn-primary btn-lg btn-pill"
          type="submit"
          disabled={submitting}
        >
          {submitting ? "등록 중..." : "문의 등록"}
        </button>
      </form>
    </div>
  );
}
