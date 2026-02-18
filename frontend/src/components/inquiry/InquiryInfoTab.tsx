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
import { Badge, DataTable, Toast, Skeleton } from "@/components/ui";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

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
            <div className="mt-1 rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-xs text-destructive" role="alert">
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

  if (loading && !inquiry) {
    return (
      <div className="space-y-6" role="status" aria-label="문의 정보 로딩 중">
        <Skeleton className="h-[120px]" />
        <Skeleton className="h-[200px]" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {toast && (
        <Toast
          message={toast.message}
          variant={toast.variant}
          onClose={() => setToast(null)}
        />
      )}

      {error && <p className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive" role="alert">{error}</p>}

      {inquiry && (
        <div className="rounded-xl border bg-card p-6 shadow-sm space-y-4">
          <h3 className="text-base font-semibold">문의 정보</h3>
          <hr className="border-t border-border" />

          <div className="grid grid-cols-2 gap-4">
            <div className="rounded-xl border bg-card p-5 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">상태</p>
              <div className="text-lg font-bold tracking-tight text-foreground">
                <Badge variant={getInquiryStatusBadgeVariant(inquiry.status)}>
                  {labelInquiryStatus(inquiry.status)}
                </Badge>
              </div>
            </div>
            <div className="rounded-xl border bg-card p-5 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">채널</p>
              <p className="text-lg font-bold tracking-tight text-foreground">
                {labelChannel(inquiry.customerChannel)}
              </p>
            </div>
          </div>

          <div className="space-y-2 text-sm">
            <div>
              <b>질문:</b> {inquiry.question}
            </div>
            <div>
              <b>접수일:</b> {new Date(inquiry.createdAt).toLocaleString("ko-KR")}
            </div>
          </div>
        </div>
      )}

      {indexingStatus && (
        <div className="rounded-xl border bg-card p-6 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-base font-semibold">첨부 문서 ({documents.length}건)</h3>
            <div className="flex items-center gap-3">
              <Button
                size="sm"
                onClick={() => handleRunIndexing(false)}
                disabled={loading}
              >
                인덱싱 실행
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => handleRunIndexing(true)}
                disabled={loading}
              >
                실패 건 재처리
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={fetchInquiryData}
                disabled={loading}
              >
                새로고침
              </Button>
            </div>
          </div>

          <hr className="border-t border-border" />

          {/* Indexing Summary Metrics */}
          <div className="grid grid-cols-3 gap-4">
            <div className="rounded-xl border bg-card p-5 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">전체</p>
              <p className="text-2xl font-bold tracking-tight text-foreground">{indexingStatus.total}</p>
              <p className="text-xs text-muted-foreground">등록된 문서</p>
            </div>
            <div className="rounded-xl border bg-card p-5 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">인덱싱 완료</p>
              <p className="text-2xl font-bold tracking-tight text-success">{indexingStatus.indexed}</p>
              <p className="text-xs text-muted-foreground">벡터 저장 완료</p>
            </div>
            <div className="rounded-xl border bg-card p-5 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">실패</p>
              <p className={cn(
                "text-2xl font-bold tracking-tight",
                indexingStatus.failed > 0 ? "text-destructive" : "text-foreground"
              )}>
                {indexingStatus.failed}
              </p>
              <p className="text-xs text-muted-foreground">재처리 필요</p>
            </div>
          </div>

          {/* Progress breakdown */}
          <div className="rounded-lg border border-info/30 bg-info-light px-4 py-3 text-sm text-info-foreground" aria-live="polite">
            업로드 {indexingStatus.uploaded} / 파싱 중 {indexingStatus.parsing} / 파싱 완료 {indexingStatus.parsed} / 청크 완료 {indexingStatus.chunked}
          </div>

          {documents.length > 0 && (
            <DataTable
              columns={documentColumns}
              data={documents}
              emptyMessage="등록된 문서가 없습니다"
            />
          )}
        </div>
      )}
    </div>
  );
}
