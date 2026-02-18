"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getOpsMetrics, listInquiries, type OpsMetrics, type InquiryListItem } from "@/lib/api/client";
import DataTable from "@/components/ui/DataTable";
import Badge from "@/components/ui/Badge";
import { Skeleton } from "@/components/ui";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
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
        <span className="block max-w-[400px] truncate">
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
        <Badge variant={getStatusVariant(item.status)}>
          {labelInquiryStatus(item.status)}
        </Badge>
      ),
      width: '100px',
    },
    {
      key: 'latestAnswerStatus',
      header: '답변',
      render: (item: InquiryListItem) => {
        if (!item.latestAnswerStatus) return <span className="text-sm text-muted-foreground">-</span>;
        return (
          <Badge variant={getAnswerVariant(item.latestAnswerStatus)}>
            {labelAnswerStatus(item.latestAnswerStatus)}
          </Badge>
        );
      },
      width: '100px',
    },
  ];

  if (loading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-semibold tracking-tight">운영 대시보드</h2>
        </div>

        {/* Skeleton metric cards */}
        <section className="grid grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <article className="rounded-xl border bg-card p-5 shadow-sm" key={i}>
              <Skeleton className="mb-3 h-3.5 w-20" />
              <Skeleton className="mb-2 h-8 w-24" />
              <Skeleton className="h-3 w-16" />
            </article>
          ))}
        </section>

        {/* Skeleton table */}
        <Card>
          <CardContent className="p-6">
            <Skeleton className="mb-6 h-[18px] w-36" />
            {[1, 2, 3, 4, 5].map((i) => (
              <Skeleton key={i} className="mb-3 h-10 w-full" />
            ))}
          </CardContent>
        </Card>
      </div>
    );
  }

  if (error) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-semibold tracking-tight">운영 대시보드</h2>
        </div>
        <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">{error}</div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold tracking-tight">운영 대시보드</h2>
      </div>

      {/* 메트릭 카드 3열 */}
      <section className="grid grid-cols-3 gap-4">
        {metricCards.map((metric) => (
          <article className="rounded-xl border bg-card p-5 shadow-sm" key={metric.label}>
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{metric.label}</p>
            <p className="text-2xl font-bold tracking-tight text-foreground">{metric.value}</p>
            {metric.subValue && (
              <p className="text-xs text-muted-foreground">{metric.subValue}</p>
            )}
          </article>
        ))}
      </section>

      {/* 최근 문의 5건 */}
      <Card>
        <CardContent className="p-6">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-base font-semibold">최근 문의 (5건)</h2>
            <Button
              variant="outline"
              size="sm"
              onClick={() => router.push('/inquiries')}
            >
              전체 보기
            </Button>
          </div>

          <DataTable
            columns={inquiryColumns}
            data={inquiries}
            onRowClick={(item) => router.push(`/inquiries/${item.inquiryId}`)}
            emptyMessage="등록된 문의가 없습니다"
          />
        </CardContent>
      </Card>

      {/* 최근 실패 사유 Top */}
      <Card>
        <CardContent className="p-6">
          <h2 className="mb-4 text-base font-semibold">
            최근 실패 사유 Top
          </h2>
          {(metrics?.topFailureReasons ?? []).length === 0 ? (
            <p className="text-sm text-muted-foreground">실패 사유 데이터 없음</p>
          ) : (
            <ul className="list-disc pl-5">
              {metrics?.topFailureReasons.map((item, idx) => (
                <li key={`${item.reason}-${idx}`} className="mb-1">
                  {item.reason} <span className="text-sm text-muted-foreground">({item.count}건)</span>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
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
