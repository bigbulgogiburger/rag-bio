"use client";

import { useEffect, useState } from "react";
import {
  listKbDocuments,
  deleteKbDocument,
  indexKbDocument,
  indexAllKbDocuments,
  getKbStats,
  type KbDocument,
  type KbDocumentListResponse,
  type KbStats,
} from "@/lib/api/client";
import { SmartUploadModal } from "@/components/upload";
import {
  labelKbCategory,
  labelDocStatus,
  labelProductFamily,
  KB_CATEGORY_LABELS,
  DOC_STATUS_LABELS,
  PRODUCT_FAMILY_LABELS,
} from "@/lib/i18n/labels";
import { showToast } from "@/lib/toast";
import DataTable from "@/components/ui/DataTable";
import Pagination from "@/components/ui/Pagination";
import FilterBar from "@/components/ui/FilterBar";
import Badge from "@/components/ui/Badge";
import EmptyState from "@/components/ui/EmptyState";
import { Skeleton } from "@/components/ui";
import { Button } from "@/components/ui/button";

export default function KnowledgeBasePage() {
  const [response, setResponse] = useState<KbDocumentListResponse | null>(null);
  const [stats, setStats] = useState<KbStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Pagination state
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);

  // Filter state
  const [filters, setFilters] = useState({
    category: "",
    productFamily: "",
    status: "",
    keyword: "",
  });

  // Upload modal state
  const [showUploadModal, setShowUploadModal] = useState(false);

  // Detail modal state
  const [selectedDoc, setSelectedDoc] = useState<KbDocument | null>(null);
  const [showDetailModal, setShowDetailModal] = useState(false);

  // Toast is now handled globally via sonner (showToast)

  const fetchDocuments = async () => {
    setLoading(true);
    setError(null);
    try {
      const params = {
        page,
        size,
        sort: "createdAt,desc",
        category: filters.category || undefined,
        productFamily: filters.productFamily || undefined,
        status: filters.status || undefined,
        keyword: filters.keyword || undefined,
      };
      const data = await listKbDocuments(params);
      setResponse(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "문서 목록 조회 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const fetchStats = async () => {
    try {
      const data = await getKbStats();
      setStats(data);
    } catch (err) {
      console.error("통계 조회 실패:", err);
    }
  };

  const silentRefreshDocuments = async () => {
    try {
      const params = {
        page,
        size,
        sort: "createdAt,desc",
        category: filters.category || undefined,
        productFamily: filters.productFamily || undefined,
        status: filters.status || undefined,
        keyword: filters.keyword || undefined,
      };
      const data = await listKbDocuments(params);
      setResponse(data);

      // 모달이 열려있으면 selectedDoc도 갱신
      if (selectedDoc) {
        const updated = data.content.find((d) => d.documentId === selectedDoc.documentId);
        if (updated) setSelectedDoc(updated);
      }
    } catch {
      // 백그라운드 실패 시 무시 (기존 데이터 유지)
    }
  };

  const silentRefreshStats = async () => {
    try {
      const data = await getKbStats();
      setStats(data);
    } catch {
      // 백그라운드 실패 시 무시
    }
  };

  useEffect(() => {
    fetchDocuments();
    fetchStats();
  }, [page, size]);

  // Polling: if any document has status INDEXING, poll every 5s
  useEffect(() => {
    if (!response) return;

    const hasIndexing = response.content.some((doc) => doc.status === "INDEXING" || doc.status === "REINDEXING");
    if (!hasIndexing) return;

    const interval = setInterval(() => {
      silentRefreshDocuments();
      silentRefreshStats();
    }, 5000);

    return () => clearInterval(interval);
  }, [response]);

  const handleSearch = () => {
    setPage(0);
    fetchDocuments();
  };

  const handleFilterChange = (key: string, value: string) => {
    setFilters({ ...filters, [key]: value });
  };

  const handlePageChange = (newPage: number) => {
    setPage(newPage);
  };

  const handleSizeChange = (newSize: number) => {
    setSize(newSize);
    setPage(0);
  };

  const handleUploadComplete = () => {
    fetchDocuments();
    fetchStats();
  };

  const handleDelete = async (doc: KbDocument) => {
    if (!window.confirm(`"${doc.title}" 문서를 삭제하시겠습니까?\n관련 벡터 데이터도 함께 삭제됩니다.`)) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      await deleteKbDocument(doc.documentId);
      setShowDetailModal(false);
      setSelectedDoc(null);
      fetchDocuments();
      fetchStats();
    } catch (err) {
      setError(err instanceof Error ? err.message : "삭제 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const handleIndex = async (docId: string) => {
    setError(null);
    const doc = response?.content?.find((d) => d.documentId === docId);
    const docTitle = doc?.title ?? docId;
    try {
      await indexKbDocument(docId);
      showToast(`"${docTitle}" 인덱싱을 시작합니다.`, "success");
      fetchDocuments();
    } catch (err) {
      if (err instanceof Error && err.message.includes("409")) {
        showToast("이미 인덱싱이 진행 중입니다", "warn");
      } else {
        showToast(err instanceof Error ? err.message : "인덱싱 중 오류가 발생했습니다.", "error");
      }
    }
  };

  const handleIndexAll = async () => {
    if (!window.confirm("모든 미인덱싱 문서를 인덱싱하시겠습니까?")) {
      return;
    }

    setError(null);
    try {
      const result = await indexAllKbDocuments();
      showToast(result.message, "success");
      fetchDocuments();
    } catch (err) {
      if (err instanceof Error && err.message.includes("409")) {
        showToast("이미 인덱싱이 진행 중입니다", "warn");
      } else {
        showToast(err instanceof Error ? err.message : "일괄 인덱싱 중 오류가 발생했습니다.", "error");
      }
    }
  };


  const getStatusBadgeVariant = (status: string): "info" | "success" | "warn" | "danger" | "neutral" => {
    if (status === "INDEXING" || status === "REINDEXING") return "warn";
    if (status === "INDEXED") return "success";
    if (status === "FAILED") return "danger";
    if (status === "UPLOADED") return "neutral";
    return "neutral";
  };

  const columns = [
    {
      key: "title",
      header: "제목",
      render: (item: KbDocument) => (
        <span className="block max-w-[300px] truncate">
          {item.title}
        </span>
      ),
    },
    {
      key: "category",
      header: "카테고리",
      width: "100px",
      render: (item: KbDocument) => labelKbCategory(item.category),
    },
    {
      key: "productFamily",
      header: "제품군",
      width: "140px",
      render: (item: KbDocument) =>
        item.productFamily ? (
          <Badge variant="info">{labelProductFamily(item.productFamily)}</Badge>
        ) : (
          <span className="text-sm text-muted-foreground">-</span>
        ),
    },
    {
      key: "status",
      header: "상태",
      width: "120px",
      render: (item: KbDocument) => (
        <Badge variant={getStatusBadgeVariant(item.status)}>
          {(item.status === "INDEXING" || item.status === "REINDEXING") && <span className="inline-block h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent" />}
          {labelDocStatus(item.status)}
        </Badge>
      ),
    },
    {
      key: "chunkCount",
      header: "청크수",
      width: "80px",
      render: (item: KbDocument) => (item.chunkCount !== null ? String(item.chunkCount) : "-"),
    },
    {
      key: "createdAt",
      header: "등록일",
      width: "100px",
      render: (item: KbDocument) => {
        const date = new Date(item.createdAt);
        return date.toLocaleDateString("ko-KR", { month: "2-digit", day: "2-digit" });
      },
    },
  ];

  const filterFields = [
    {
      key: "category",
      label: "카테고리",
      type: "select" as const,
      options: [
        { value: "", label: "전체" },
        ...Object.entries(KB_CATEGORY_LABELS).map(([key, label]) => ({
          value: key,
          label,
        })),
      ],
    },
    {
      key: "productFamily",
      label: "제품군",
      type: "select" as const,
      options: [
        { value: "", label: "전체" },
        ...Object.entries(PRODUCT_FAMILY_LABELS).map(([key, label]) => ({
          value: key,
          label,
        })),
      ],
    },
    {
      key: "status",
      label: "상태",
      type: "select" as const,
      options: [
        { value: "", label: "전체" },
        ...Object.entries(DOC_STATUS_LABELS).map(([key, label]) => ({
          value: key,
          label,
        })),
      ],
    },
    {
      key: "keyword",
      label: "검색어",
      type: "text" as const,
      placeholder: "제목 검색",
    },
  ];

  // Compute indexing percentage
  const indexPercentage = stats && stats.totalDocuments > 0
    ? Math.round((stats.indexedDocuments / stats.totalDocuments) * 100)
    : 0;

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">지식 기반 관리</h2>
          {stats && (
            <p className="text-sm text-muted-foreground mt-1">
              총 {stats.totalDocuments}건의 문서, {indexPercentage}% 인덱싱 완료
            </p>
          )}
        </div>
        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            onClick={handleIndexAll}
            className="flex-1 sm:flex-none"
          >
            일괄 인덱싱
          </Button>
          <Button
            onClick={() => setShowUploadModal(true)}
            className="flex-1 sm:flex-none"
          >
            문서 등록
          </Button>
        </div>
      </div>

      {/* Metric Strip */}
      {stats ? (
        <div className="space-y-3">
          <div className="flex items-center gap-6 rounded-2xl border border-border/50 bg-card px-6 py-4 shadow-brand overflow-x-auto scrollbar-none">
            <div className="shrink-0">
              <p className="text-[0.65rem] font-medium uppercase tracking-wide text-muted-foreground">전체 문서</p>
              <p className="text-lg font-bold tracking-tight text-foreground">{stats.totalDocuments}건</p>
            </div>
            <div className="h-8 w-px bg-border shrink-0" />
            <div className="shrink-0">
              <p className="text-[0.65rem] font-medium uppercase tracking-wide text-muted-foreground">인덱싱 완료</p>
              <p className="text-lg font-bold tracking-tight text-[hsl(var(--success))]">{stats.indexedDocuments}건</p>
            </div>
            <div className="h-8 w-px bg-border shrink-0" />
            <div className="shrink-0">
              <p className="text-[0.65rem] font-medium uppercase tracking-wide text-muted-foreground">총 청크</p>
              <p className="text-lg font-bold tracking-tight text-foreground">{stats.totalChunks.toLocaleString()}개</p>
            </div>
          </div>

          {/* Indexing Progress Bar */}
          <div className="h-2 rounded-full bg-muted overflow-hidden">
            <div
              className="h-full rounded-full bg-[hsl(var(--success))] transition-all duration-500"
              style={{ width: `${indexPercentage}%` }}
            />
          </div>
        </div>
      ) : (
        <div className="space-y-3">
          <div className="flex items-center gap-6 rounded-2xl border border-border/50 bg-card px-6 py-4 shadow-brand">
            {[1, 2, 3].map((i) => (
              <div key={i} className="flex items-center gap-6">
                {i > 1 && <div className="h-8 w-px bg-border shrink-0" />}
                <div>
                  <Skeleton className="h-3 w-16 mb-2" />
                  <Skeleton className="h-6 w-[60px]" />
                </div>
              </div>
            ))}
          </div>
          <Skeleton className="h-2 w-full rounded-full" />
        </div>
      )}

      {/* Product Family Distribution - Bar Chart */}
      {stats && stats.byProductFamily && Object.keys(stats.byProductFamily).length > 0 && (
        <section>
          <h3 className="text-sm font-medium text-muted-foreground mb-3">제품군별 분포</h3>
          <div className="space-y-2">
            {Object.entries(stats.byProductFamily)
              .sort(([, a], [, b]) => (b as number) - (a as number))
              .map(([family, count]) => {
                const maxCount = Math.max(...Object.values(stats.byProductFamily).map(Number));
                const barPercentage = maxCount > 0 ? ((count as number) / maxCount) * 100 : 0;
                return (
                  <div key={family} className="flex items-center gap-3">
                    <span className="text-xs font-medium w-24 truncate text-right">{labelProductFamily(family)}</span>
                    <div className="flex-1 h-2.5 rounded-full bg-muted overflow-hidden">
                      <div
                        className="h-full rounded-full bg-primary/60 transition-all duration-500"
                        style={{ width: `${barPercentage}%` }}
                      />
                    </div>
                    <span className="text-xs tabular-nums text-muted-foreground w-12 text-right">{count as number}건</span>
                  </div>
                );
              })}
          </div>
        </section>
      )}

      {/* Main Content */}
      <div className="rounded-2xl border border-border/50 bg-card shadow-brand p-4 sm:p-6 space-y-4">
        <FilterBar
          fields={filterFields}
          values={filters}
          onChange={handleFilterChange}
          onSearch={handleSearch}
        />

        {/* Loading skeleton */}
        {loading && !response && (
          <>
            {/* Desktop skeleton */}
            <div className="hidden md:block space-y-2">
              {[1, 2, 3, 4, 5].map((i) => (
                <Skeleton key={i} className="h-11 w-full" />
              ))}
            </div>
            {/* Mobile skeleton cards */}
            <div className="flex flex-col gap-3 md:hidden">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="rounded-2xl border border-border/50 bg-card p-4 shadow-brand space-y-3">
                  <Skeleton className="h-4 w-3/4" />
                  <div className="flex items-center gap-2">
                    <Skeleton className="h-3 w-14" />
                    <Skeleton className="h-5 w-16 rounded-full" />
                    <Skeleton className="h-5 w-14 rounded-full" />
                  </div>
                </div>
              ))}
            </div>
          </>
        )}

        {error && <p className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">{error}</p>}

        {!error && response && (
          <>
            {response.content.length === 0 ? (
              <EmptyState
                title="등록된 지식 기반 문서가 없습니다"
                description="문서 등록 버튼을 눌러 기술 문서를 추가하세요."
                action={{
                  label: "문서 등록",
                  onClick: () => setShowUploadModal(true),
                }}
              />
            ) : (
              <>
                {/* Desktop table */}
                <div className="hidden md:block">
                  <DataTable
                    columns={columns}
                    data={response.content}
                    onRowClick={(item) => {
                      setSelectedDoc(item);
                      setShowDetailModal(true);
                    }}
                    emptyMessage="등록된 문서가 없습니다"
                  />
                </div>

                {/* Mobile card list */}
                <div className="flex flex-col gap-3 md:hidden">
                  {response.content.map((item) => (
                    <button
                      key={item.documentId}
                      type="button"
                      className="w-full rounded-xl border border-border/50 bg-card p-4 shadow-brand text-left active:scale-[0.98] transition-all"
                      onClick={() => {
                        setSelectedDoc(item);
                        setShowDetailModal(true);
                      }}
                    >
                      <p className="mb-2 text-sm font-medium leading-snug line-clamp-2">{item.title}</p>
                      <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                        <span>{labelKbCategory(item.category)}</span>
                        {item.productFamily && (
                          <Badge variant="info">{labelProductFamily(item.productFamily)}</Badge>
                        )}
                        <Badge variant={getStatusBadgeVariant(item.status)}>
                          {(item.status === "INDEXING" || item.status === "REINDEXING") && <span className="inline-block h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent" />}
                          {labelDocStatus(item.status)}
                        </Badge>
                        {item.chunkCount !== null && <span>{item.chunkCount}청크</span>}
                      </div>
                    </button>
                  ))}
                </div>

                <Pagination
                  page={response.page}
                  totalPages={response.totalPages}
                  totalElements={response.totalElements}
                  size={response.size}
                  onPageChange={handlePageChange}
                  onSizeChange={handleSizeChange}
                />
              </>
            )}
          </>
        )}
      </div>

      {/* Smart Upload Modal */}
      <SmartUploadModal
        open={showUploadModal}
        onClose={() => setShowUploadModal(false)}
        onComplete={handleUploadComplete}
      />

      {/* Detail Modal — Bottom Sheet on mobile */}
      {showDetailModal && selectedDoc && (
        <div
          className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-[hsl(var(--overlay))/50] backdrop-blur-sm animate-in fade-in duration-200"
          onClick={() => setShowDetailModal(false)}
        >
          <div
            className="mx-0 sm:mx-4 w-full max-w-3xl rounded-t-2xl sm:rounded-2xl border bg-card shadow-2xl max-h-[85vh] overflow-y-auto animate-in fade-in slide-in-from-bottom-4 sm:zoom-in-95 duration-200"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Mobile drag handle */}
            <div className="flex justify-center py-2 sm:hidden">
              <div className="h-1 w-8 rounded-full bg-muted-foreground/30" />
            </div>

            {/* Sticky Header */}
            <div className="sticky top-0 z-10 bg-card border-b border-border px-4 sm:px-6 py-4">
              <h3 className="text-base font-semibold line-clamp-2">{selectedDoc.title}</h3>
            </div>

            {/* Content */}
            <div className="px-4 sm:px-6 py-4 space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
                <div>
                  <p className="text-xs font-medium text-muted-foreground mb-1">카테고리</p>
                  <p>{labelKbCategory(selectedDoc.category)}</p>
                </div>
                <div>
                  <p className="text-xs font-medium text-muted-foreground mb-1">제품군</p>
                  <p>{selectedDoc.productFamily ? <Badge variant="info">{labelProductFamily(selectedDoc.productFamily)}</Badge> : "-"}</p>
                </div>
                <div>
                  <p className="text-xs font-medium text-muted-foreground mb-1">파일</p>
                  <p>{selectedDoc.fileName} ({(selectedDoc.fileSize / 1024).toFixed(1)} KB)</p>
                </div>
                <div>
                  <p className="text-xs font-medium text-muted-foreground mb-1">상태</p>
                  <p>
                    <Badge variant={getStatusBadgeVariant(selectedDoc.status)}>
                      {(selectedDoc.status === "INDEXING" || selectedDoc.status === "REINDEXING") && <span className="inline-block h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent" />}
                      {labelDocStatus(selectedDoc.status)}
                    </Badge>
                  </p>
                </div>
                <div>
                  <p className="text-xs font-medium text-muted-foreground mb-1">청크 / 벡터</p>
                  <p>{selectedDoc.chunkCount ?? "-"}개 / {selectedDoc.vectorCount ?? "-"}개</p>
                </div>
                <div>
                  <p className="text-xs font-medium text-muted-foreground mb-1">등록자</p>
                  <p>{selectedDoc.uploadedBy || "-"}</p>
                </div>
                <div>
                  <p className="text-xs font-medium text-muted-foreground mb-1">등록일</p>
                  <p>{new Date(selectedDoc.createdAt).toLocaleString("ko-KR")}</p>
                </div>
                {selectedDoc.tags && (
                  <div>
                    <p className="text-xs font-medium text-muted-foreground mb-1">태그</p>
                    <p>{selectedDoc.tags}</p>
                  </div>
                )}
              </div>
              {selectedDoc.description && (
                <div className="text-sm">
                  <p className="text-xs font-medium text-muted-foreground mb-1">설명</p>
                  <p>{selectedDoc.description}</p>
                </div>
              )}
              {selectedDoc.lastError && (
                <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
                  <b>오류:</b> {selectedDoc.lastError}
                </div>
              )}
            </div>

            {/* Sticky Footer */}
            <div className="sticky bottom-0 z-10 bg-card border-t border-border px-4 sm:px-6 py-4">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:gap-3 sm:justify-end">
                <Button
                  variant="outline"
                  onClick={() => handleIndex(selectedDoc.documentId)}
                  disabled={selectedDoc.status === "INDEXING" || selectedDoc.status === "REINDEXING"}
                >
                  {selectedDoc.status === "INDEXED" ? "재인덱싱" : "인덱싱 실행"}
                </Button>
                <Button
                  variant="destructive"
                  onClick={() => handleDelete(selectedDoc)}
                  disabled={loading}
                >
                  삭제
                </Button>
                <Button
                  variant="ghost"
                  onClick={() => setShowDetailModal(false)}
                >
                  닫기
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Mobile FAB - Upload button */}
      <button
        type="button"
        onClick={() => setShowUploadModal(true)}
        className="fixed bottom-[calc(4rem+env(safe-area-inset-bottom)+0.5rem)] right-4 z-40 flex h-14 w-14 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg shadow-primary/25 transition-transform hover:scale-105 active:scale-95 md:hidden"
        aria-label="문서 등록"
      >
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M12 5V19M5 12H19" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
        </svg>
      </button>
    </div>
  );
}
