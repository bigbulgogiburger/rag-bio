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
        <h2>운영 대시보드</h2>
        <p className="muted" style={{ textAlign: 'center', padding: 'var(--space-2xl)' }}>
          데이터를 불러오는 중...
        </p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="stack">
        <h2>운영 대시보드</h2>
        <div className="card">
          <p style={{ color: 'var(--color-danger)' }}>{error}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="stack">
      <h2>운영 대시보드</h2>

      {/* 메트릭 카드 3열 */}
      <section className="metrics-grid" style={{ gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>
        {metricCards.map((metric) => (
          <article className="card" key={metric.label}>
            <p className="muted" style={{ margin: 0, fontSize: 'var(--font-size-sm)' }}>
              {metric.label}
            </p>
            <p style={{ margin: 'var(--space-xs) 0 0', fontSize: '1.9rem', fontWeight: 800 }}>
              {metric.value}
            </p>
            {metric.subValue && (
              <p className="muted" style={{ margin: 'var(--space-xs) 0 0', fontSize: 'var(--font-size-sm)' }}>
                {metric.subValue}
              </p>
            )}
          </article>
        ))}
      </section>

      {/* 최근 문의 5건 */}
      <article className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-md)' }}>
          <h2 className="section-title" style={{ margin: 0 }}>
            최근 문의 (5건)
          </h2>
          <button
            className="btn"
            onClick={() => router.push('/inquiries')}
            style={{ fontSize: 'var(--font-size-sm)' }}
          >
            전체 보기 →
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
        <ul style={{ margin: 0, paddingLeft: 'var(--space-lg)' }}>
          {(metrics?.topFailureReasons ?? []).length === 0 ? (
            <li className="muted">실패 사유 데이터 없음</li>
          ) : (
            metrics?.topFailureReasons.map((item, idx) => (
              <li key={`${item.reason}-${idx}`}>
                {item.reason} <span className="muted">({item.count}건)</span>
              </li>
            ))
          )}
        </ul>
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
