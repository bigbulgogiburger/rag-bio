"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getOpsMetrics, listInquiries, type OpsMetrics, type InquiryListItem } from "@/lib/api/client";
import DataTable from "@/components/ui/DataTable";
import { labelInquiryStatus, labelChannel, labelAnswerStatus } from "@/lib/i18n/labels";

export default function DashboardPage() {
  const router = useRouter();
  const [metrics, setMetrics] = useState<OpsMetrics | null>(null);
  const [inquiries, setInquiries] = useState<InquiryListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([
      getOpsMetrics(),
      listInquiries({ page: 0, size: 5 }),
    ])
      .then(([metricsData, inquiriesData]) => {
        setMetrics(metricsData);
        setInquiries(inquiriesData.content);
      })
      .catch((e) => setError(e instanceof Error ? e.message : "데이터를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, []);

  const metricCards = [
    {
      label: "발송 성공률",
      value: metrics ? `${metrics.sendSuccessRate}%` : "-",
      subValue: metrics ? `${metrics.sentCount}/${metrics.approvedOrSentCount}건` : "",
    },
    {
      label: "중복 차단률",
      value: metrics ? `${metrics.duplicateBlockRate}%` : "-",
      subValue: metrics ? `${metrics.duplicateBlockedCount}/${metrics.totalSendAttemptCount}건` : "",
    },
    {
      label: "Fallback 비율",
      value: metrics ? `${metrics.fallbackDraftRate}%` : "-",
      subValue: metrics ? `${metrics.fallbackDraftCount}/${metrics.totalDraftCount}건` : "",
    },
  ];

  const inquiryColumns = [
    {
      key: 'createdAt',
      header: '접수일',
      render: (item: InquiryListItem) => item.createdAt.slice(0, 10),
      width: '100px',
    },
    {
      key: 'question',
      header: '질문 요약',
      render: (item: InquiryListItem) => (
        <span style={{ display: 'block', maxWidth: '400px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {item.question}
        </span>
      ),
    },
    {
      key: 'customerChannel',
      header: '채널',
      render: (item: InquiryListItem) => labelChannel(item.customerChannel),
      width: '80px',
    },
    {
      key: 'status',
      header: '상태',
      render: (item: InquiryListItem) => (
        <span className={`badge badge-${getStatusVariant(item.status)}`}>
          {labelInquiryStatus(item.status)}
        </span>
      ),
      width: '100px',
    },
    {
      key: 'latestAnswerStatus',
      header: '답변',
      render: (item: InquiryListItem) => {
        if (!item.latestAnswerStatus) return <span className="muted">-</span>;
        return (
          <span className={`badge badge-${getAnswerVariant(item.latestAnswerStatus)}`}>
            {labelAnswerStatus(item.latestAnswerStatus)}
          </span>
        );
      },
      width: '100px',
    },
  ];

  if (loading) {
    return (
      <div className="stack">
        <div className="page-header">
          <h2 className="card-title">운영 대시보드</h2>
        </div>

        {/* Skeleton metric cards */}
        <section className="metrics-grid" style={{ gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>
          {[1, 2, 3].map((i) => (
            <article className="metric-card" key={i}>
              <div className="skeleton" style={{ height: '14px', width: '80px', marginBottom: 'var(--space-sm)' }} />
              <div className="skeleton" style={{ height: '32px', width: '100px', marginBottom: 'var(--space-xs)' }} />
              <div className="skeleton" style={{ height: '12px', width: '60px' }} />
            </article>
          ))}
        </section>

        {/* Skeleton table */}
        <article className="card">
          <div className="skeleton" style={{ height: '18px', width: '140px', marginBottom: 'var(--space-lg)' }} />
          {[1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="skeleton" style={{ height: '40px', width: '100%', marginBottom: 'var(--space-sm)' }} />
          ))}
        </article>
      </div>
    );
  }

  if (error) {
    return (
      <div className="stack">
        <div className="page-header">
          <h2 className="card-title">운영 대시보드</h2>
        </div>
        <div className="status-banner status-danger">{error}</div>
      </div>
    );
  }

  return (
    <div className="stack">
      <div className="page-header">
        <h2 className="card-title">운영 대시보드</h2>
      </div>

      {/* 메트릭 카드 3열 */}
      <section className="metrics-grid" style={{ gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>
        {metricCards.map((metric) => (
          <article className="metric-card" key={metric.label}>
            <p className="metric-label">{metric.label}</p>
            <p className="metric-value">{metric.value}</p>
            {metric.subValue && (
              <p className="metric-sub">{metric.subValue}</p>
            )}
          </article>
        ))}
      </section>

      {/* 최근 문의 5건 */}
      <article className="card">
        <div className="page-header" style={{ marginBottom: 'var(--space-md)' }}>
          <h2 className="section-title">최근 문의 (5건)</h2>
          <button
            className="btn btn-sm"
            onClick={() => router.push('/inquiries')}
          >
            전체 보기
          </button>
        </div>

        <DataTable
          columns={inquiryColumns}
          data={inquiries}
          onRowClick={(item) => router.push(`/inquiries/${item.inquiryId}`)}
          emptyMessage="등록된 문의가 없습니다"
        />
      </article>

      {/* 최근 실패 사유 Top */}
      <article className="card">
        <h2 className="section-title" style={{ marginBottom: 'var(--space-md)' }}>
          최근 실패 사유 Top
        </h2>
        {(metrics?.topFailureReasons ?? []).length === 0 ? (
          <p className="muted" style={{ margin: 0 }}>실패 사유 데이터 없음</p>
        ) : (
          <ul style={{ margin: 0, paddingLeft: 'var(--space-lg)' }}>
            {metrics?.topFailureReasons.map((item, idx) => (
              <li key={`${item.reason}-${idx}`} style={{ marginBottom: 'var(--space-xs)' }}>
                {item.reason} <span className="muted">({item.count}건)</span>
              </li>
            ))}
          </ul>
        )}
      </article>
    </div>
  );
}

function getStatusVariant(status: string): 'info' | 'success' | 'warn' | 'danger' {
  if (status === 'RECEIVED') return 'info';
  if (status === 'ANALYSIS_COMPLETED') return 'success';
  if (status === 'DRAFT_GENERATED') return 'success';
  return 'info';
}

function getAnswerVariant(status: string): 'info' | 'success' | 'warn' | 'danger' {
  if (status === 'DRAFT') return 'info';
  if (status === 'REVIEWED') return 'warn';
  if (status === 'APPROVED') return 'success';
  if (status === 'SENT') return 'success';
  return 'info';
}
