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
  KB_CATEGORY_LABELS,
  DOC_STATUS_LABELS,
} from "@/lib/i18n/labels";
import DataTable from "@/components/ui/DataTable";
import Pagination from "@/components/ui/Pagination";
import FilterBar from "@/components/ui/FilterBar";
import Badge from "@/components/ui/Badge";
import EmptyState from "@/components/ui/EmptyState";
import Toast from "@/components/ui/Toast";

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

  // Toast state
  const [toast, setToast] = useState<{ variant: "success" | "error" | "warn" | "info"; message: string } | null>(null);

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

  useEffect(() => {
    fetchDocuments();
    fetchStats();
  }, [page, size]);

  // Polling: if any document has status INDEXING, poll every 5s
  useEffect(() => {
    if (!response) return;

    const hasIndexing = response.content.some((doc) => doc.status === "INDEXING");
    if (!hasIndexing) return;

    const interval = setInterval(() => {
      fetchDocuments();
      fetchStats();
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
      setToast({ variant: "success", message: `"${docTitle}" 인덱싱을 시작합니다.` });
      fetchDocuments();
    } catch (err) {
      if (err instanceof Error && err.message.includes("409")) {
        setToast({ variant: "warn", message: "이미 인덱싱이 진행 중입니다" });
      } else {
        setToast({ variant: "error", message: err instanceof Error ? err.message : "인덱싱 중 오류가 발생했습니다." });
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
      setToast({ variant: "success", message: result.message });
      fetchDocuments();
    } catch (err) {
      if (err instanceof Error && err.message.includes("409")) {
        setToast({ variant: "warn", message: "이미 인덱싱이 진행 중입니다" });
      } else {
        setToast({ variant: "error", message: err instanceof Error ? err.message : "일괄 인덱싱 중 오류가 발생했습니다." });
      }
    }
  };

  const getStatusBadgeVariant = (status: string): "info" | "success" | "warn" | "danger" | "neutral" => {
    if (status === "INDEXING") return "warn";
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
        <span style={{ display: "block", maxWidth: "300px", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
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
      width: "120px",
      render: (item: KbDocument) => item.productFamily || <span className="muted">-</span>,
    },
    {
      key: "status",
      header: "상태",
      width: "120px",
      render: (item: KbDocument) => (
        <Badge variant={getStatusBadgeVariant(item.status)}>
          {item.status === "INDEXING" && <span className="badge-spinner" />}
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
      type: "text" as const,
      placeholder: "제품군 검색",
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
    <div className="stack">
      {/* Page Header */}
      <div className="page-header">
        <h2 className="card-title">지식 기반 관리</h2>
        <div className="row">
          <button
            className="btn"
            onClick={handleIndexAll}
          >
            일괄 인덱싱
          </button>
          <button
            className="btn btn-primary"
            onClick={() => setShowUploadModal(true)}
          >
            문서 등록
          </button>
        </div>
      </div>

      {/* Stats Cards */}
      {stats ? (
        <section className="metrics-grid" style={{ gridTemplateColumns: "repeat(3, minmax(0, 1fr))" }}>
          <article className="metric-card">
            <p className="metric-label">전체 문서</p>
            <p className="metric-value">{stats.totalDocuments}건</p>
          </article>
          <article className="metric-card">
            <p className="metric-label">인덱싱 완료</p>
            <p className="metric-value">{stats.indexedDocuments}건</p>
          </article>
          <article className="metric-card">
            <p className="metric-label">총 청크</p>
            <p className="metric-value">{stats.totalChunks.toLocaleString()}개</p>
          </article>
        </section>
      ) : (
        <section className="metrics-grid" style={{ gridTemplateColumns: "repeat(3, minmax(0, 1fr))" }}>
          {[1, 2, 3].map((i) => (
            <article className="metric-card" key={i}>
              <div className="skeleton" style={{ height: '14px', width: '80px', marginBottom: 'var(--space-sm)' }} />
              <div className="skeleton" style={{ height: '32px', width: '100px' }} />
            </article>
          ))}
        </section>
      )}

      {/* Main Content */}
      <div className="card stack">
        <FilterBar
          fields={filterFields}
          values={filters}
          onChange={handleFilterChange}
          onSearch={handleSearch}
        />

        {/* Loading skeleton */}
        {loading && (
          <div className="stack" style={{ gap: 'var(--space-sm)' }}>
            {[1, 2, 3, 4, 5].map((i) => (
              <div key={i} className="skeleton" style={{ height: '44px', width: '100%' }} />
            ))}
          </div>
        )}

        {error && <p className="status-banner status-danger">{error}</p>}

        {!loading && !error && response && (
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
          className="modal-backdrop"
          onClick={() => setShowDetailModal(false)}
        >
          <div
            className="modal-content modal-lg stack"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="section-title">{selectedDoc.title}</h3>

            <div className="kv">
              <div><b>카테고리:</b> {labelKbCategory(selectedDoc.category)}</div>
              <div><b>제품군:</b> {selectedDoc.productFamily || "-"}</div>
              <div><b>파일:</b> {selectedDoc.fileName} ({(selectedDoc.fileSize / 1024).toFixed(1)} KB)</div>
              <div>
                <b>상태:</b>{" "}
                <Badge variant={getStatusBadgeVariant(selectedDoc.status)}>
                  {selectedDoc.status === "INDEXING" && <span className="badge-spinner" />}
                  {labelDocStatus(selectedDoc.status)}
                </Badge>
              </div>
              <div><b>청크:</b> {selectedDoc.chunkCount ?? "-"}개 &middot; <b>벡터:</b> {selectedDoc.vectorCount ?? "-"}개</div>
              <div><b>등록자:</b> {selectedDoc.uploadedBy || "-"}</div>
              <div><b>등록일:</b> {new Date(selectedDoc.createdAt).toLocaleString("ko-KR")}</div>
              {selectedDoc.tags && <div><b>태그:</b> {selectedDoc.tags}</div>}
              {selectedDoc.description && <div><b>설명:</b> {selectedDoc.description}</div>}
              {selectedDoc.lastError && (
                <div className="status-banner status-danger">
                  <b>오류:</b> {selectedDoc.lastError}
                </div>
              )}
            </div>

            <hr className="divider" />

            <div className="row" style={{ justifyContent: "flex-end" }}>
              <button
                className="btn"
                onClick={() => handleIndex(selectedDoc.documentId)}
              >
                인덱싱 실행
              </button>
              <button
                className="btn btn-danger"
                onClick={() => handleDelete(selectedDoc)}
                disabled={loading}
              >
                삭제
              </button>
              <button
                className="btn btn-ghost"
                onClick={() => setShowDetailModal(false)}
              >
                닫기
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Toast */}
      {toast && (
        <Toast
          variant={toast.variant}
          message={toast.message}
          onClose={() => setToast(null)}
        />
      )}
    </div>
  );
}
