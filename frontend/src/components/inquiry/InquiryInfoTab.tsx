"use client";

import { useEffect, useState, useCallback } from "react";
import {
  getInquiry,
  updateInquiry,
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
  docStatusBadgeVariant,
} from "@/lib/i18n/labels";
import { Badge, DataTable, Skeleton } from "@/components/ui";
import { Button } from "@/components/ui/button";
import { showToast } from "@/lib/toast";
import { useInquiryEvents, type IndexingProgressData } from "@/hooks/useInquiryEvents";

interface InquiryInfoTabProps {
  inquiryId: string;
}

export default function InquiryInfoTab({ inquiryId }: InquiryInfoTabProps) {
  const [inquiry, setInquiry] = useState<InquiryDetail | null>(null);
  const [documents, setDocuments] = useState<DocumentStatus[]>([]);
  const [indexingStatus, setIndexingStatus] = useState<InquiryIndexingStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showDetails, setShowDetails] = useState(false);
  const [isEditingQuestion, setIsEditingQuestion] = useState(false);
  const [editedQuestion, setEditedQuestion] = useState("");
  const [savingQuestion, setSavingQuestion] = useState(false);

  // SSE-driven indexing progress
  const [indexingProgress, setIndexingProgress] = useState<IndexingProgressData | null>(null);
  const [indexingInProgress, setIndexingInProgress] = useState(false);

  const refreshDocuments = useCallback(async () => {
    try {
      const [documentsData, indexingData] = await Promise.all([
        listInquiryDocuments(inquiryId),
        getInquiryIndexingStatus(inquiryId),
      ]);
      setDocuments(documentsData);
      setIndexingStatus(indexingData);
    } catch {
      // silently fail - data will be refreshed on next event
    }
  }, [inquiryId]);

  const handleIndexingProgress = useCallback((data: IndexingProgressData) => {
    setIndexingProgress(data);
    setIndexingInProgress(true);
  }, []);

  const handleIndexingCompleted = useCallback((data: IndexingProgressData) => {
    setIndexingProgress(data);
    setIndexingInProgress(false);
    showToast(`인덱싱 완료 - ${data.indexed}/${data.total} 처리됨`, "success");
    refreshDocuments();
  }, [refreshDocuments]);

  const handleIndexingFailed = useCallback((data: IndexingProgressData) => {
    setIndexingProgress(data);
    setIndexingInProgress(false);
    showToast(`인덱싱 실패 - ${data.failed}건 실패`, "error");
    refreshDocuments();
  }, [refreshDocuments]);

  useInquiryEvents(inquiryId, {
    onIndexingProgress: (data) => {
      const status = data.status;
      if (status === "COMPLETED" || status === "INDEXED") {
        handleIndexingCompleted(data);
      } else if (status === "FAILED") {
        handleIndexingFailed(data);
      } else {
        handleIndexingProgress(data);
      }
    },
    onEvent: (event) => {
      if (event.type === "INDEXING_COMPLETED") {
        handleIndexingCompleted(event.data as IndexingProgressData);
      } else if (event.type === "INDEXING_FAILED") {
        handleIndexingFailed(event.data as IndexingProgressData);
      } else if (event.type === "INDEXING_STARTED") {
        setIndexingInProgress(true);
        setIndexingProgress(event.data as IndexingProgressData);
      }
    },
  });

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

      // Check if indexing is already in progress from initial data
      const hasActiveIndexing = indexingData.documents.some(
        (d) => d.status === "INDEXING" || d.status === "UPLOADED" || d.status === "PARSING" || d.status === "PARSED" || d.status === "CHUNKED"
      );
      setIndexingInProgress(hasActiveIndexing);
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
      setIndexingInProgress(true);
      await refreshDocuments();
      showToast(
        `인덱싱 시작 - 처리 ${result.processed}건 / 성공 ${result.succeeded}건 / 실패 ${result.failed}건`,
        "success",
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "인덱싱 실행 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };


  const handleSaveQuestion = async () => {
    if (!inquiry || !editedQuestion.trim()) return;
    setSavingQuestion(true);
    try {
      const updated = await updateInquiry(inquiryId, editedQuestion);
      setInquiry(updated);
      setIsEditingQuestion(false);
      showToast("문의 내용이 수정되었습니다", "success");
    } catch (err) {
      showToast(err instanceof Error ? err.message : "문의 수정 중 오류가 발생했습니다.", "error");
    } finally {
      setSavingQuestion(false);
    }
  };

  const getInquiryStatusBadgeVariant = (status: string): "info" | "success" | "warn" | "danger" | "neutral" => {
    if (status === "ANALYSIS_COMPLETED" || status === "DRAFT_GENERATED") return "success";
    if (status === "RECEIVED") return "info";
    return "neutral";
  };

  const hasFailedDocuments = documents.some(
    (doc) => doc.status === "FAILED" || doc.status === "FAILED_PARSING"
  );


  const baseColumns = [
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
        <Badge variant={docStatusBadgeVariant(doc.status)}>
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
  ];

  const detailColumns = [
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
    {
      key: "ocrConfidence",
      header: "OCR 신뢰도",
      render: (doc: DocumentStatus) => doc.ocrConfidence != null ? doc.ocrConfidence.toFixed(2) : "-",
      width: "120px",
    },
  ];

  const documentColumns = showDetails ? [...baseColumns, ...detailColumns] : baseColumns;

  if (loading && !inquiry) {
    return (
      <div className="space-y-6" role="status" aria-label="문의 정보 로딩 중">
        <Skeleton className="h-[120px]" />
        <Skeleton className="h-[200px]" />
      </div>
    );
  }

  const indexingPercent = indexingProgress && indexingProgress.total > 0
    ? Math.round(((indexingProgress.indexed + indexingProgress.failed) / indexingProgress.total) * 100)
    : 0;

  return (
    <div className="space-y-6">
      {error && <p className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive" role="alert">{error}</p>}

      {/* SSE-driven indexing progress bar */}
      {indexingInProgress && indexingProgress && (
        <div
          className="rounded-lg border border-blue-500/30 bg-blue-500/10 px-4 py-3 space-y-2"
          role="status"
          aria-label="인덱싱 진행 중"
        >
          <div className="flex items-center justify-between text-sm">
            <span className="flex items-center gap-2 text-blue-700 dark:text-blue-400">
              <svg className="h-4 w-4 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-hidden="true">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              문서 인덱싱 중...
            </span>
            <span className="text-xs text-muted-foreground">
              {indexingProgress.indexed}/{indexingProgress.total} 완료
              {indexingProgress.failed > 0 && (
                <span className="text-destructive ml-2">{indexingProgress.failed}건 실패</span>
              )}
            </span>
          </div>
          <div className="h-2 w-full rounded-full bg-blue-200 dark:bg-blue-900" aria-hidden="true">
            <div
              className="h-2 rounded-full bg-blue-500 transition-all duration-300"
              style={{ width: `${indexingPercent}%` }}
            />
          </div>
          <p className="text-xs text-muted-foreground">{indexingPercent}% 완료</p>
        </div>
      )}

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

          <div className="space-y-3 text-sm">
            <div>
              <div className="flex items-center justify-between mb-2">
                <b>질문</b>
                {!isEditingQuestion && (
                  <Button
                    variant="outline"
                    size="sm"
                    className="gap-1.5"
                    onClick={() => { setEditedQuestion(inquiry.question); setIsEditingQuestion(true); }}
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/><path d="m15 5 4 4"/></svg>
                    수정
                  </Button>
                )}
              </div>
              {isEditingQuestion ? (
                <div className="space-y-3">
                  <textarea
                    className="w-full min-h-[120px] rounded-lg border border-input bg-transparent p-4 text-sm leading-relaxed shadow-sm focus:outline-none focus:ring-2 focus:ring-ring"
                    value={editedQuestion}
                    onChange={(e) => setEditedQuestion(e.target.value)}
                    aria-label="문의 내용 수정"
                  />
                  <div className="flex items-center gap-2">
                    <Button size="sm" onClick={handleSaveQuestion} disabled={savingQuestion || !editedQuestion.trim()}>
                      {savingQuestion ? "저장 중..." : "저장"}
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => setIsEditingQuestion(false)} disabled={savingQuestion}>
                      취소
                    </Button>
                  </div>
                </div>
              ) : (
                <div className="rounded-lg border bg-muted/20 p-4 text-sm leading-relaxed whitespace-pre-wrap">
                  {inquiry.question}
                </div>
              )}
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
              {hasFailedDocuments && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handleRunIndexing(true)}
                  disabled={loading}
                >
                  실패 건 재처리
                </Button>
              )}
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

          {documents.length > 0 && (
            <>
              <div className="flex justify-end">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setShowDetails((prev) => !prev)}
                  aria-expanded={showDetails}
                  aria-controls="document-table"
                >
                  {showDetails ? "상세 정보 숨기기" : "상세 정보 보기"}
                </Button>
              </div>
              <div id="document-table">
                <DataTable
                  columns={documentColumns}
                  data={documents}
                  emptyMessage="등록된 문서가 없습니다"
                />
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
