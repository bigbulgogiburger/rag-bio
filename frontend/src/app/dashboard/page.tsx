"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  getOpsMetrics,
  listInquiries,
  getTimeline,
  getProcessingTime,
  getKbUsage,
  getMetricsCsvUrl,
  type OpsMetrics,
  type RagMetrics,
  type InquiryListItem,
  type InquiryListResponse,
  type TimelineData,
  type ProcessingTimeData,
  type KbUsageData,
  type DashboardPeriod,
} from "@/lib/api/client";
import DataTable from "@/components/ui/DataTable";
import Badge from "@/components/ui/Badge";
import { Skeleton } from "@/components/ui";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { labelInquiryStatus, labelChannel, labelAnswerStatus } from "@/lib/i18n/labels";
import { PeriodSelector, TimelineChart, StatusPieChart } from "@/components/dashboard";

export default function DashboardPage() {
  const router = useRouter();
  const [metrics, setMetrics] = useState<OpsMetrics | null>(null);
  const [inquiries, setInquiries] = useState<InquiryListItem[]>([]);
  const [inquiryListResponse, setInquiryListResponse] = useState<InquiryListResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Analytics state
  const [period, setPeriod] = useState<DashboardPeriod>("30d");
  const [timeline, setTimeline] = useState<TimelineData | null>(null);
  const [processingTime, setProcessingTime] = useState<ProcessingTimeData | null>(null);
  const [kbUsage, setKbUsage] = useState<KbUsageData | null>(null);
  const [analyticsLoading, setAnalyticsLoading] = useState(false);

  // Initial load
  useEffect(() => {
    Promise.all([
      getOpsMetrics(),
      listInquiries({ page: 0, size: 5 }),
    ])
      .then(([metricsData, inquiriesData]) => {
        setMetrics(metricsData);
        setInquiries(inquiriesData.content);
        setInquiryListResponse(inquiriesData);
      })
      .catch((e) => setError(e instanceof Error ? e.message : "데이터를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, []);

  // Analytics load (on period change)
  useEffect(() => {
    setAnalyticsLoading(true);
    Promise.all([
      getTimeline(period),
      getProcessingTime(period),
      getKbUsage(period),
    ])
      .then(([timelineData, processingTimeData, kbUsageData]) => {
        setTimeline(timelineData);
        setProcessingTime(processingTimeData);
        setKbUsage(kbUsageData);
      })
      .catch(() => {
        // Silently handle analytics errors — main metrics still visible
      })
      .finally(() => setAnalyticsLoading(false));
  }, [period]);

  const handleExportCsv = () => {
    window.open(getMetricsCsvUrl(period), "_blank");
  };

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
          <div>
            <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">운영 대시보드</h2>
            <p className="text-sm text-muted-foreground mt-1">실시간 CS 대응 현황을 한눈에 확인하세요</p>
          </div>
        </div>
        <section className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-3">
          {[1, 2, 3].map((i) => (
            <article className="rounded-2xl border bg-card p-4 shadow-sm sm:p-5" key={i}>
              <Skeleton className="mb-3 h-3.5 w-20" />
              <Skeleton className="mb-2 h-8 w-24" />
              <Skeleton className="h-3 w-16" />
            </article>
          ))}
        </section>
        <Card>
          <CardContent className="p-6">
            <Skeleton className="mb-6 h-[18px] w-36" />
            <Skeleton className="h-[300px] w-full" />
          </CardContent>
        </Card>
      </div>
    );
  }

  if (error) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">운영 대시보드</h2>
            <p className="text-sm text-muted-foreground mt-1">실시간 CS 대응 현황을 한눈에 확인하세요</p>
          </div>
        </div>
        <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">{error}</div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header with period selector + CSV export */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">운영 대시보드</h2>
          <p className="text-sm text-muted-foreground mt-1">실시간 CS 대응 현황을 한눈에 확인하세요</p>
        </div>
        <div className="flex items-center gap-3">
          <PeriodSelector value={period} onChange={setPeriod} />
          <Button variant="outline" size="sm" className="h-8 text-xs" onClick={handleExportCsv}>
            CSV 내보내기
          </Button>
        </div>
      </div>

      {/* Metric Cards */}
      <section className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-3">
        {metricCards.map((metric) => (
          <article className="group relative overflow-hidden rounded-2xl border border-border/50 bg-card p-4 sm:p-5 shadow-brand transition-all hover:shadow-brand-lg hover:-translate-y-0.5" key={metric.label}>
            <div className="absolute inset-0 bg-gradient-to-br from-primary/[0.02] to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
            <p className="relative text-xs font-medium uppercase tracking-wide text-muted-foreground">{metric.label}</p>
            <p className="relative text-2xl font-bold tracking-tight text-foreground">{metric.value}</p>
            {metric.subValue && (
              <p className="relative text-xs text-muted-foreground">{metric.subValue}</p>
            )}
          </article>
        ))}

        {/* Processing Time Metrics */}
        {processingTime && (
          <>
            <article className="group relative overflow-hidden rounded-2xl border border-border/50 bg-card p-4 sm:p-5 shadow-brand transition-all hover:shadow-brand-lg hover:-translate-y-0.5">
              <div className="absolute inset-0 bg-gradient-to-br from-primary/[0.02] to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
              <p className="relative text-xs font-medium uppercase tracking-wide text-muted-foreground">평균 처리 시간</p>
              <p className="relative text-2xl font-bold tracking-tight text-foreground">
                {processingTime.avgProcessingTimeHours > 0 ? `${processingTime.avgProcessingTimeHours}h` : "-"}
              </p>
              <p className="relative text-xs text-muted-foreground">
                중앙값 {processingTime.medianProcessingTimeHours}h
              </p>
            </article>
            <article className="group relative overflow-hidden rounded-2xl border border-border/50 bg-card p-4 sm:p-5 shadow-brand transition-all hover:shadow-brand-lg hover:-translate-y-0.5">
              <div className="absolute inset-0 bg-gradient-to-br from-primary/[0.02] to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
              <p className="relative text-xs font-medium uppercase tracking-wide text-muted-foreground">완료 건수</p>
              <p className="relative text-2xl font-bold tracking-tight text-foreground">{processingTime.totalCompleted}건</p>
              <p className="relative text-xs text-muted-foreground">
                {processingTime.minProcessingTimeHours}h ~ {processingTime.maxProcessingTimeHours}h
              </p>
            </article>
            <article className="group relative overflow-hidden rounded-2xl border border-border/50 bg-card p-4 sm:p-5 shadow-brand transition-all hover:shadow-brand-lg hover:-translate-y-0.5">
              <div className="absolute inset-0 bg-gradient-to-br from-primary/[0.02] to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
              <p className="relative text-xs font-medium uppercase tracking-wide text-muted-foreground">KB 활용률</p>
              <p className="relative text-2xl font-bold tracking-tight text-foreground">
                {kbUsage ? `${kbUsage.kbUsageRate}%` : "-"}
              </p>
              {kbUsage && (
                <p className="relative text-xs text-muted-foreground">
                  {kbUsage.kbEvidences}/{kbUsage.totalEvidences}건
                </p>
              )}
            </article>
          </>
        )}
      </section>

      {/* Charts Row */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[2fr_1fr]">
        {/* Timeline Chart */}
        <Card>
          <CardContent className="p-6">
            <h3 className="mb-4 text-lg font-semibold">문의 처리 현황</h3>
            {analyticsLoading ? (
              <Skeleton className="h-[300px] w-full" />
            ) : (
              <TimelineChart data={timeline?.data ?? []} />
            )}
          </CardContent>
        </Card>

        {/* Status Pie Chart */}
        <Card>
          <CardContent className="p-6">
            <h3 className="mb-4 text-lg font-semibold">상태별 분포</h3>
            <StatusPieChart inquiries={inquiryListResponse} />
          </CardContent>
        </Card>
      </div>

      {/* KB Usage - Top Referenced Documents */}
      {kbUsage && kbUsage.topDocuments.length > 0 && (
        <Card>
          <CardContent className="p-6">
            <h3 className="mb-4 text-lg font-semibold">KB 상위 참조 문서</h3>
            <div className="space-y-2">
              {kbUsage.topDocuments.map((doc, idx) => (
                <div
                  key={doc.documentId}
                  className="flex items-center justify-between rounded-xl border border-border/50 bg-card p-4 transition-colors hover:bg-accent/50"
                >
                  <div className="flex items-center gap-3">
                    <span className={`flex h-8 w-8 items-center justify-center rounded-lg text-sm font-bold ${idx < 3 ? 'bg-primary/10 text-primary' : 'bg-muted text-muted-foreground'}`}>
                      {idx + 1}
                    </span>
                    <span className="text-sm font-medium">{doc.fileName}</span>
                  </div>
                  <span className="text-sm text-muted-foreground">{doc.referenceCount}회 참조</span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* RAG Pipeline Metrics */}
      {metrics?.ragMetrics && (
        <RagMetricsSection ragMetrics={metrics.ragMetrics} />
      )}

      {/* Recent Inquiries Table */}
      <Card>
        <CardContent className="p-6">
          <div className="mb-4 flex items-center justify-between">
            <h3 className="text-lg font-semibold">최근 문의 (5건)</h3>
            <Button
              variant="outline"
              size="sm"
              onClick={() => router.push('/inquiries')}
            >
              전체 보기
            </Button>
          </div>

          {/* Desktop table */}
          <div className="hidden md:block">
            <DataTable
              columns={inquiryColumns}
              data={inquiries}
              onRowClick={(item) => { window.location.href = `/inquiries/${item.inquiryId}/`; }}
              emptyMessage="등록된 문의가 없습니다"
            />
          </div>

          {/* Mobile card list */}
          <div className="flex flex-col gap-3 md:hidden">
            {inquiries.length === 0 ? (
              <p className="py-8 text-center text-sm text-muted-foreground">등록된 문의가 없습니다</p>
            ) : (
              inquiries.map((item) => (
                <button
                  key={item.inquiryId}
                  type="button"
                  className="w-full rounded-xl border border-border/50 bg-card p-4 shadow-brand active:scale-[0.98] transition-all text-left"
                  onClick={() => { window.location.href = `/inquiries/${item.inquiryId}/`; }}
                >
                  <div className="mb-2 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Badge variant={getStatusVariant(item.status)}>
                        {labelInquiryStatus(item.status)}
                      </Badge>
                      {item.latestAnswerStatus && (
                        <Badge variant={getAnswerVariant(item.latestAnswerStatus)}>
                          {labelAnswerStatus(item.latestAnswerStatus)}
                        </Badge>
                      )}
                    </div>
                    <span className="text-xs text-muted-foreground">{item.createdAt.slice(0, 10)}</span>
                  </div>
                  <p className="mb-2 text-sm font-medium leading-snug line-clamp-2">{item.question}</p>
                  <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                    <span>{labelChannel(item.customerChannel)}</span>
                  </div>
                </button>
              ))
            )}
          </div>
        </CardContent>
      </Card>

      {/* Failure Reasons */}
      <Card>
        <CardContent className="p-6">
          <h3 className="mb-4 text-lg font-semibold">
            최근 실패 사유 Top
          </h3>
          {(metrics?.topFailureReasons ?? []).length === 0 ? (
            <p className="text-sm text-muted-foreground">실패 사유 데이터 없음</p>
          ) : (
            <div className="space-y-2">
              {metrics?.topFailureReasons.map((item, idx) => (
                <div key={`${item.reason}-${idx}`} className="flex items-center justify-between rounded-lg border border-border/30 bg-destructive/5 px-4 py-3">
                  <span className="text-sm">{item.reason}</span>
                  <Badge variant="danger">{item.count}건</Badge>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function RagMetricsSection({ ragMetrics }: { ragMetrics: RagMetrics }) {
  const ragCards = [
    {
      label: "검색 정확도",
      value: `${(ragMetrics.avgSearchScore * 100).toFixed(1)}%`,
    },
    {
      label: "리랭킹 개선",
      value: `+${(ragMetrics.avgRerankImprovement * 100).toFixed(1)}%`,
    },
    {
      label: "Critic 수정률",
      value: `${(ragMetrics.criticRevisionRate * 100).toFixed(1)}%`,
    },
    {
      label: "HyDE 사용률",
      value: `${(ragMetrics.hydeUsageRate * 100).toFixed(1)}%`,
    },
    {
      label: "Multi-Hop 활성화",
      value: `${(ragMetrics.multiHopActivationRate * 100).toFixed(1)}%`,
    },
    {
      label: "적응형 재시도",
      value: `${(ragMetrics.adaptiveRetryRate * 100).toFixed(1)}%`,
    },
  ];

  return (
    <Card>
      <CardContent className="p-6">
        <h3 className="mb-4 text-lg font-semibold">RAG 파이프라인 성능</h3>
        <div
          className="grid gap-3"
          style={{
            gridTemplateColumns: "repeat(auto-fill, minmax(min(100%, 160px), 1fr))",
          }}
        >
          {ragCards.map((card) => (
            <article
              key={card.label}
              className="rounded-xl border border-border/50 bg-card p-4"
            >
              <p
                className="text-xs font-medium uppercase tracking-wide text-muted-foreground"
              >
                {card.label}
              </p>
              <p className="mt-1 text-xl font-bold tracking-tight">
                {card.value}
              </p>
            </article>
          ))}
        </div>
      </CardContent>
    </Card>
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
