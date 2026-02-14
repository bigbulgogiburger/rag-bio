"use client";

import { useEffect, useState } from "react";
import {
  getInquiry,
  listInquiryDocuments,
  getInquiryIndexingStatus,
  runInquiryIndexing,
  type InquiryDetail,
  type DocumentStatus,
  type InquiryIndexingStatus,
} from "@/lib/api/client";
import {
  labelDocStatus,
  labelChannel,
  labelInquiryStatus,
} from "@/lib/i18n/labels";
import { Badge, DataTable, Toast } from "@/components/ui";

interface InquiryInfoTabProps {
  inquiryId: string;
}

export default function InquiryInfoTab({ inquiryId }: InquiryInfoTabProps) {
  const [inquiry, setInquiry] = useState<InquiryDetail | null>(null);
  const [documents, setDocuments] = useState<DocumentStatus[]>([]);
  const [indexingStatus, setIndexingStatus] = useState<InquiryIndexingStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; variant: "success" | "error" | "warn" | "info" } | null>(null);

  useEffect(() => {
    fetchInquiryData();
  }, [inquiryId]);

  const fetchInquiryData = async () => {
    setLoading(true);
    setError(null);

    try {
      const [inquiryData, documentsData, indexingData] = await Promise.all([
        getInquiry(inquiryId),
        listInquiryDocuments(inquiryId),
        getInquiryIndexingStatus(inquiryId),
      ]);
      setInquiry(inquiryData);
      setDocuments(documentsData);
      setIndexingStatus(indexingData);
    } catch (err) {
      setError(err instanceof Error ? err.message : "문의 정보 조회 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const handleRunIndexing = async (failedOnly: boolean) => {
    setLoading(true);
    setError(null);

    try {
      const result = await runInquiryIndexing(inquiryId, failedOnly);
      const [documentsData, indexingData] = await Promise.all([
        listInquiryDocuments(inquiryId),
        getInquiryIndexingStatus(inquiryId),
      ]);
      setDocuments(documentsData);
      setIndexingStatus(indexingData);
      setToast({
        message: `인덱싱 완료 - 처리 ${result.processed}건 / 성공 ${result.succeeded}건 / 실패 ${result.failed}건`,
        variant: "success",
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "인덱싱 실행 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const getDocStatusBadgeVariant = (status: string): "info" | "success" | "warn" | "danger" | "neutral" => {
    if (["INDEXED", "PARSED", "CHUNKED"].includes(status)) return "success";
    if (["FAILED", "FAILED_PARSING"].includes(status)) return "danger";
    if (["PARSING"].includes(status)) return "warn";
    return "neutral";
  };

  const getInquiryStatusBadgeVariant = (status: string): "info" | "success" | "warn" | "danger" | "neutral" => {
    if (status === "ANALYSIS_COMPLETED" || status === "DRAFT_GENERATED") return "success";
    if (status === "RECEIVED") return "info";
    return "neutral";
  };

  const documentColumns = [
    {
      key: "fileName",
      header: "파일명",
      render: (doc: DocumentStatus) => (
        <div>
          <div>{doc.fileName}</div>
          {doc.lastError && (
            <div style={{ fontSize: "0.85rem", color: "var(--color-danger)", marginTop: "0.25rem" }}>
              오류: {doc.lastError}
            </div>
          )}
        </div>
      ),
    },
    {
      key: "status",
      header: "상태",
      render: (doc: DocumentStatus) => (
        <Badge variant={getDocStatusBadgeVariant(doc.status)}>
          {labelDocStatus(doc.status)}
        </Badge>
      ),
      width: "120px",
    },
    {
      key: "fileSize",
      header: "크기",
      render: (doc: DocumentStatus) => `${(doc.fileSize / 1024).toFixed(1)} KB`,
      width: "100px",
    },
    {
      key: "ocrConfidence",
      header: "OCR 신뢰도",
      render: (doc: DocumentStatus) => doc.ocrConfidence != null ? doc.ocrConfidence.toFixed(2) : "-",
      width: "120px",
    },
    {
      key: "chunkCount",
      header: "청크",
      render: (doc: DocumentStatus) => doc.chunkCount ?? "-",
      width: "80px",
    },
    {
      key: "vectorCount",
      header: "벡터",
      render: (doc: DocumentStatus) => doc.vectorCount ?? "-",
      width: "80px",
    },
  ];

  return (
    <div className="stack">
      {toast && (
        <Toast
          message={toast.message}
          variant={toast.variant}
          onClose={() => setToast(null)}
        />
      )}

      {loading && <p className="muted">로딩 중...</p>}
      {error && <p className="status-banner status-danger" role="alert">{error}</p>}

      {inquiry && (
        <div className="card stack">
          <h3 className="section-title">문의 정보</h3>
          <div className="kv">
            <div>
              <b>질문:</b> {inquiry.question}
            </div>
            <div>
              <b>채널:</b> {labelChannel(inquiry.customerChannel)}
            </div>
            <div>
              <b>상태:</b>{" "}
              <Badge variant={getInquiryStatusBadgeVariant(inquiry.status)}>
                {labelInquiryStatus(inquiry.status)}
              </Badge>
            </div>
            <div>
              <b>접수일:</b> {new Date(inquiry.createdAt).toLocaleString("ko-KR")}
            </div>
          </div>
        </div>
      )}

      {indexingStatus && (
        <div className="card stack">
          <h3 className="section-title">첨부 문서 ({documents.length}건)</h3>

          <div className="kv">
            <b>인덱싱 요약</b>
            <div>
              전체 {indexingStatus.total} / 업로드 {indexingStatus.uploaded} / 파싱 중 {indexingStatus.parsing} /
              파싱 완료 {indexingStatus.parsed} / 청크 완료 {indexingStatus.chunked} / 벡터 저장 완료 {indexingStatus.indexed} /
              실패 {indexingStatus.failed}
            </div>
          </div>

          {documents.length > 0 && (
            <DataTable
              columns={documentColumns}
              data={documents}
              emptyMessage="등록된 문서가 없습니다"
            />
          )}

          <div className="row" style={{ marginTop: "var(--space-md)" }}>
            <button
              className="btn btn-primary"
              onClick={() => handleRunIndexing(false)}
              disabled={loading}
            >
              인덱싱 실행
            </button>
            <button
              className="btn"
              onClick={() => handleRunIndexing(true)}
              disabled={loading}
            >
              실패 건 재처리
            </button>
            <button
              className="btn"
              onClick={fetchInquiryData}
              disabled={loading}
            >
              새로고침
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
