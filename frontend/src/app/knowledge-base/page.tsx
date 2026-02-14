"use client";

import { useEffect, useState } from "react";
import {
  listKbDocuments,
  uploadKbDocument,
  deleteKbDocument,
  indexKbDocument,
  indexAllKbDocuments,
  getKbStats,
  type KbDocument,
  type KbDocumentListResponse,
  type KbStats,
} from "@/lib/api/client";
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
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploadData, setUploadData] = useState({
    title: "",
    category: "MANUAL",
    productFamily: "",
    description: "",
    tags: "",
  });
  const [uploading, setUploading] = useState(false);

  // Detail modal state
  const [selectedDoc, setSelectedDoc] = useState<KbDocument | null>(null);
  const [showDetailModal, setShowDetailModal] = useState(false);

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

  const handleUpload = async () => {
    if (!uploadFile || !uploadData.title || !uploadData.category) {
      setError("파일, 제목, 카테고리는 필수 입력 항목입니다.");
      return;
    }

    setUploading(true);
    setError(null);
    try {
      await uploadKbDocument({
        file: uploadFile,
        title: uploadData.title,
        category: uploadData.category,
        productFamily: uploadData.productFamily || undefined,
        description: uploadData.description || undefined,
        tags: uploadData.tags || undefined,
      });
      setShowUploadModal(false);
      setUploadFile(null);
      setUploadData({
        title: "",
        category: "MANUAL",
        productFamily: "",
        description: "",
        tags: "",
      });
      fetchDocuments();
      fetchStats();
    } catch (err) {
      setError(err instanceof Error ? err.message : "업로드 중 오류가 발생했습니다.");
    } finally {
      setUploading(false);
    }
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
    setLoading(true);
    setError(null);
    try {
      const updated = await indexKbDocument(docId);
      setSelectedDoc(updated);
      fetchDocuments();
      fetchStats();
    } catch (err) {
      setError(err instanceof Error ? err.message : "인덱싱 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const handleIndexAll = async () => {
    if (!window.confirm("모든 미인덱싱 문서를 인덱싱하시겠습니까?")) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const result = await indexAllKbDocuments();
      alert(`인덱싱 완료\n처리: ${result.processed}건\n성공: ${result.succeeded}건\n실패: ${result.failed}건`);
      fetchDocuments();
      fetchStats();
    } catch (err) {
      setError(err instanceof Error ? err.message : "일괄 인덱싱 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const getStatusBadgeVariant = (status: string): "info" | "success" | "warn" | "danger" | "neutral" => {
    if (["INDEXED", "PARSED", "CHUNKED"].includes(status)) return "success";
    if (["FAILED", "FAILED_PARSING"].includes(status)) return "danger";
    if (["PARSING"].includes(status)) return "warn";
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
      {/* Stats Cards */}
      {stats && (
        <section className="metrics-grid" style={{ gridTemplateColumns: "repeat(3, minmax(0, 1fr))" }}>
          <article className="card">
            <p className="muted" style={{ margin: 0, fontSize: "var(--font-size-sm)" }}>전체 문서</p>
            <p style={{ margin: "var(--space-xs) 0 0", fontSize: "1.9rem", fontWeight: 800 }}>
              {stats.totalDocuments}건
            </p>
          </article>
          <article className="card">
            <p className="muted" style={{ margin: 0, fontSize: "var(--font-size-sm)" }}>인덱싱 완료</p>
            <p style={{ margin: "var(--space-xs) 0 0", fontSize: "1.9rem", fontWeight: 800 }}>
              {stats.indexedDocuments}건
            </p>
          </article>
          <article className="card">
            <p className="muted" style={{ margin: 0, fontSize: "var(--font-size-sm)" }}>총 청크</p>
            <p style={{ margin: "var(--space-xs) 0 0", fontSize: "1.9rem", fontWeight: 800 }}>
              {stats.totalChunks.toLocaleString()}개
            </p>
          </article>
        </section>
      )}

      {/* Main Content */}
      <div className="card stack">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <h2 className="card-title">지식 기반 관리</h2>
          <div style={{ display: "flex", gap: "var(--space-sm)" }}>
            <button
              className="btn"
              onClick={handleIndexAll}
              disabled={loading}
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

        <FilterBar
          fields={filterFields}
          values={filters}
          onChange={handleFilterChange}
          onSearch={handleSearch}
        />

        {loading && <p className="muted">로딩 중...</p>}
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

      {/* Upload Modal */}
      {showUploadModal && (
        <div
          style={{
            position: "fixed",
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: "rgba(0, 0, 0, 0.5)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            zIndex: 1000,
          }}
          onClick={() => setShowUploadModal(false)}
        >
          <div
            className="card stack"
            style={{ width: "90%", maxWidth: "600px", maxHeight: "90vh", overflow: "auto" }}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="section-title">문서 등록</h3>

            <label className="label">
              파일 선택 *
              <input
                type="file"
                className="input"
                accept=".pdf,.doc,.docx"
                onChange={(e) => setUploadFile(e.target.files?.[0] || null)}
              />
              {uploadFile && (
                <p className="muted" style={{ marginTop: "var(--space-xs)", fontSize: "var(--font-size-sm)" }}>
                  📄 {uploadFile.name} ({(uploadFile.size / 1024 / 1024).toFixed(2)} MB)
                </p>
              )}
            </label>

            <label className="label">
              제목 *
              <input
                type="text"
                className="input"
                value={uploadData.title}
                onChange={(e) => setUploadData({ ...uploadData, title: e.target.value })}
                placeholder="문서 제목"
              />
            </label>

            <label className="label">
              카테고리 *
              <select
                className="select"
                value={uploadData.category}
                onChange={(e) => setUploadData({ ...uploadData, category: e.target.value })}
              >
                {Object.entries(KB_CATEGORY_LABELS).map(([key, label]) => (
                  <option key={key} value={key}>{label}</option>
                ))}
              </select>
            </label>

            <label className="label">
              제품군
              <input
                type="text"
                className="input"
                value={uploadData.productFamily}
                onChange={(e) => setUploadData({ ...uploadData, productFamily: e.target.value })}
                placeholder="예: Reagent, Instrument"
              />
            </label>

            <label className="label">
              설명
              <textarea
                className="textarea"
                rows={3}
                value={uploadData.description}
                onChange={(e) => setUploadData({ ...uploadData, description: e.target.value })}
                placeholder="문서 설명"
              />
            </label>

            <label className="label">
              태그
              <input
                type="text"
                className="input"
                value={uploadData.tags}
                onChange={(e) => setUploadData({ ...uploadData, tags: e.target.value })}
                placeholder="쉼표로 구분 (예: reagent, 4도, 보관)"
              />
            </label>

            <div className="row" style={{ justifyContent: "flex-end" }}>
              <button
                className="btn"
                onClick={() => setShowUploadModal(false)}
                disabled={uploading}
              >
                취소
              </button>
              <button
                className="btn btn-primary"
                onClick={handleUpload}
                disabled={uploading || !uploadFile || !uploadData.title}
              >
                {uploading ? "업로드 중..." : "등록"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Detail Modal */}
      {showDetailModal && selectedDoc && (
        <div
          style={{
            position: "fixed",
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: "rgba(0, 0, 0, 0.5)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            zIndex: 1000,
          }}
          onClick={() => setShowDetailModal(false)}
        >
          <div
            className="card stack"
            style={{ width: "90%", maxWidth: "700px", maxHeight: "90vh", overflow: "auto" }}
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
                  {labelDocStatus(selectedDoc.status)}
                </Badge>
              </div>
              <div><b>청크:</b> {selectedDoc.chunkCount ?? "-"}개 · <b>벡터:</b> {selectedDoc.vectorCount ?? "-"}개</div>
              <div><b>등록자:</b> {selectedDoc.uploadedBy || "-"}</div>
              <div><b>등록일:</b> {new Date(selectedDoc.createdAt).toLocaleString("ko-KR")}</div>
              {selectedDoc.tags && <div><b>태그:</b> {selectedDoc.tags}</div>}
              {selectedDoc.description && <div><b>설명:</b> {selectedDoc.description}</div>}
              {selectedDoc.lastError && (
                <div style={{ color: "var(--color-danger)" }}>
                  <b>오류:</b> {selectedDoc.lastError}
                </div>
              )}
            </div>

            <div className="row" style={{ justifyContent: "flex-end" }}>
              <button
                className="btn"
                onClick={() => handleIndex(selectedDoc.documentId)}
                disabled={loading}
              >
                인덱싱 실행
              </button>
              <button
                className="btn"
                onClick={() => handleDelete(selectedDoc)}
                disabled={loading}
                style={{ color: "var(--color-danger)" }}
              >
                삭제
              </button>
              <button
                className="btn"
                onClick={() => setShowDetailModal(false)}
              >
                닫기
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
