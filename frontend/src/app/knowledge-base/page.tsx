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

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold tracking-tight">지식 기반 관리</h2>
        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            onClick={handleIndexAll}
          >
            일괄 인덱싱
          </Button>
          <Button
            onClick={() => setShowUploadModal(true)}
          >
            문서 등록
          </Button>
        </div>
      </div>

      {/* Stats Cards */}
      {stats ? (
        <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <article className="rounded-xl border bg-card p-4 shadow-sm sm:p-5">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">전체 문서</p>
            <p className="text-2xl font-bold tracking-tight text-foreground">{stats.totalDocuments}건</p>
          </article>
          <article className="rounded-xl border bg-card p-4 shadow-sm sm:p-5">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">인덱싱 완료</p>
            <p className="text-2xl font-bold tracking-tight text-foreground">{stats.indexedDocuments}건</p>
          </article>
          <article className="rounded-xl border bg-card p-4 shadow-sm sm:p-5">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">총 청크</p>
            <p className="text-2xl font-bold tracking-tight text-foreground">{stats.totalChunks.toLocaleString()}개</p>
          </article>
        </section>
      ) : (
        <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[1, 2, 3].map((i) => (
            <article className="rounded-xl border bg-card p-4 shadow-sm sm:p-5" key={i}>
              <Skeleton className="h-3.5 w-20 mb-2" />
              <Skeleton className="h-8 w-[100px]" />
            </article>
          ))}
        </section>
      )}

      {/* Product Family Distribution */}
      {stats && stats.byProductFamily && Object.keys(stats.byProductFamily).length > 0 && (
        <section>
          <h3 className="text-sm font-medium text-muted-foreground mb-3">제품군별 분포</h3>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-3">
            {Object.entries(stats.byProductFamily).map(([family, count]) => (
              <article key={family} className="rounded-xl border bg-card p-4 shadow-sm">
                <p className="text-xs font-medium text-muted-foreground truncate">{labelProductFamily(family)}</p>
                <p className="text-lg font-bold tracking-tight text-foreground">{count}건</p>
              </article>
            ))}
          </div>
        </section>
      )}

      {/* Main Content */}
      <div className="rounded-xl border bg-card p-6 shadow-sm space-y-4">
        <FilterBar
          fields={filterFields}
          values={filters}
          onChange={handleFilterChange}
          onSearch={handleSearch}
        />

        {/* Loading skeleton */}
        {loading && !response && (
          <div className="space-y-2">
            {[1, 2, 3, 4, 5].map((i) => (
              <Skeleton key={i} className="h-11 w-full" />
            ))}
          </div>
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
                <DataTable
                  columns={columns}
                  data={response.content}
                  onRowClick={(item) => {
                    setSelectedDoc(item);
                    setShowDetailModal(true);
                  }}
                  emptyMessage="등록된 문서가 없습니다"
                />

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

      {/* Detail Modal */}
      {showDetailModal && selectedDoc && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm animate-in fade-in duration-200"
          onClick={() => setShowDetailModal(false)}
        >
          <div
            className="w-full max-w-3xl rounded-xl border bg-card p-6 shadow-2xl space-y-4 animate-in fade-in zoom-in-95 duration-200"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="text-base font-semibold">{selectedDoc.title}</h3>

            <div className="space-y-2 text-sm">
              <div><b>카테고리:</b> {labelKbCategory(selectedDoc.category)}</div>
              <div><b>제품군:</b> {selectedDoc.productFamily ? <Badge variant="info">{labelProductFamily(selectedDoc.productFamily)}</Badge> : "-"}</div>
              <div><b>파일:</b> {selectedDoc.fileName} ({(selectedDoc.fileSize / 1024).toFixed(1)} KB)</div>
              <div>
                <b>상태:</b>{" "}
                <Badge variant={getStatusBadgeVariant(selectedDoc.status)}>
                  {(selectedDoc.status === "INDEXING" || selectedDoc.status === "REINDEXING") && <span className="inline-block h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent" />}
                  {labelDocStatus(selectedDoc.status)}
                </Badge>
              </div>
              <div><b>청크:</b> {selectedDoc.chunkCount ?? "-"}개 &middot; <b>벡터:</b> {selectedDoc.vectorCount ?? "-"}개</div>
              <div><b>등록자:</b> {selectedDoc.uploadedBy || "-"}</div>
              <div><b>등록일:</b> {new Date(selectedDoc.createdAt).toLocaleString("ko-KR")}</div>
              {selectedDoc.tags && <div><b>태그:</b> {selectedDoc.tags}</div>}
              {selectedDoc.description && <div><b>설명:</b> {selectedDoc.description}</div>}
              {selectedDoc.lastError && (
                <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
                  <b>오류:</b> {selectedDoc.lastError}
                </div>
              )}
            </div>

            <hr className="border-t border-border" />

            <div className="flex items-center gap-3 justify-end">
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
      )}

    </div>
  );
}
