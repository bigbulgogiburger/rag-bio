"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  listInquiries,
  type InquiryListItem,
  type InquiryListResponse,
} from "@/lib/api/client";
import {
  labelInquiryStatus,
  labelAnswerStatus,
  labelChannel,
  INQUIRY_STATUS_LABELS,
  CHANNEL_LABELS,
} from "@/lib/i18n/labels";
import DataTable from "@/components/ui/DataTable";
import Pagination from "@/components/ui/Pagination";
import FilterBar from "@/components/ui/FilterBar";
import Badge from "@/components/ui/Badge";
import EmptyState from "@/components/ui/EmptyState";

export default function InquiriesPage() {
  const router = useRouter();
  const [response, setResponse] = useState<InquiryListResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Pagination state
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);

  // Filter state
  const [filters, setFilters] = useState({
    status: "",
    channel: "",
    keyword: "",
    from: "",
    to: "",
  });

  const fetchInquiries = async () => {
    setLoading(true);
    setError(null);
    try {
      const params = {
        page,
        size,
        sort: "createdAt,desc",
        status: filters.status ? [filters.status] : undefined,
        channel: filters.channel || undefined,
        keyword: filters.keyword || undefined,
        from: filters.from || undefined,
        to: filters.to || undefined,
      };
      const data = await listInquiries(params);
      setResponse(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "목록 조회 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchInquiries();
  }, [page, size]);

  const handleSearch = () => {
    setPage(0); // Reset to first page on search
    fetchInquiries();
  };

  const handleFilterChange = (key: string, value: string) => {
    setFilters({ ...filters, [key]: value });
  };

  const handlePageChange = (newPage: number) => {
    setPage(newPage);
  };

  const handleSizeChange = (newSize: number) => {
    setSize(newSize);
    setPage(0); // Reset to first page
  };

  const getStatusBadgeVariant = (status: string): "info" | "success" | "warn" | "danger" | "neutral" => {
    if (["ANSWERED", "CLOSED"].includes(status)) return "success";
    if (["ANALYZED"].includes(status)) return "info";
    return "neutral";
  };

  const getAnswerStatusBadgeVariant = (status: string | null): "info" | "success" | "warn" | "danger" | "neutral" => {
    if (!status) return "neutral";
    if (["SENT"].includes(status)) return "success";
    if (["APPROVED"].includes(status)) return "info";
    if (["REVIEWED"].includes(status)) return "warn";
    return "neutral";
  };

  const columns = [
    {
      key: "createdAt",
      header: "접수일",
      width: "120px",
      render: (item: InquiryListItem) => {
        const date = new Date(item.createdAt);
        return date.toLocaleDateString("ko-KR", { month: "2-digit", day: "2-digit" });
      },
    },
    {
      key: "question",
      header: "질문 요약",
      render: (item: InquiryListItem) => (
        <span style={{ display: "block", maxWidth: "400px", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
          {item.question}
        </span>
      ),
    },
    {
      key: "customerChannel",
      header: "채널",
      width: "100px",
      render: (item: InquiryListItem) => labelChannel(item.customerChannel),
    },
    {
      key: "status",
      header: "상태",
      width: "120px",
      render: (item: InquiryListItem) => (
        <Badge variant={getStatusBadgeVariant(item.status)}>
          {labelInquiryStatus(item.status)}
        </Badge>
      ),
    },
    {
      key: "latestAnswerStatus",
      header: "답변",
      width: "120px",
      render: (item: InquiryListItem) =>
        item.latestAnswerStatus ? (
          <Badge variant={getAnswerStatusBadgeVariant(item.latestAnswerStatus)}>
            {labelAnswerStatus(item.latestAnswerStatus)}
          </Badge>
        ) : (
          <span className="muted">-</span>
        ),
    },
    {
      key: "documentCount",
      header: "문서 수",
      width: "100px",
      render: (item: InquiryListItem) => String(item.documentCount),
    },
  ];

  const filterFields = [
    {
      key: "status",
      label: "상태",
      type: "select" as const,
      options: [
        { value: "", label: "전체" },
        ...Object.entries(INQUIRY_STATUS_LABELS).map(([key, label]) => ({
          value: key,
          label,
        })),
      ],
    },
    {
      key: "channel",
      label: "채널",
      type: "select" as const,
      options: [
        { value: "", label: "전체" },
        ...Object.entries(CHANNEL_LABELS).map(([key, label]) => ({
          value: key,
          label,
        })),
      ],
    },
    {
      key: "from",
      label: "시작일",
      type: "date" as const,
    },
    {
      key: "to",
      label: "종료일",
      type: "date" as const,
    },
    {
      key: "keyword",
      label: "검색어",
      type: "text" as const,
      placeholder: "질문 내용 검색",
    },
  ];

  return (
    <div className="stack">
      {/* Page Header */}
      <div className="page-header">
        <h2 className="card-title">문의 대응 내역</h2>
        <button
          className="btn btn-primary"
          onClick={() => router.push("/inquiries/new")}
        >
          문의 작성
        </button>
      </div>

      {/* Main Content Card */}
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
                title="등록된 문의가 없습니다"
                description="새 문의를 작성하여 CS 대응을 시작하세요."
                action={{
                  label: "문의 작성",
                  onClick: () => router.push("/inquiries/new"),
                }}
              />
            ) : (
              <>
                <DataTable
                  columns={columns}
                  data={response.content}
                  onRowClick={(item) => router.push(`/inquiries/${item.inquiryId}`)}
                  emptyMessage="등록된 문의가 없습니다"
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
    </div>
  );
}
