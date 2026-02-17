"use client";

import { useEffect, useState } from "react";
import {
  listAnswerDraftHistory,
  type AnswerDraftResult,
} from "@/lib/api/client";
import {
  labelAnswerStatus,
  labelVerdict,
  labelChannel,
  labelTone,
} from "@/lib/i18n/labels";
import { DataTable, Badge } from "@/components/ui";

interface InquiryHistoryTabProps {
  inquiryId: string;
}

export default function InquiryHistoryTab({ inquiryId }: InquiryHistoryTabProps) {
  const [history, setHistory] = useState<AnswerDraftResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<AnswerDraftResult | null>(null);

  useEffect(() => {
    fetchHistory();
  }, [inquiryId]);

  const fetchHistory = async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await listAnswerDraftHistory(inquiryId);
      setHistory(data);
      if (data.length > 0) setSelected(data[0]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "버전 이력 조회 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const getAnswerStatusBadgeVariant = (status: string): "info" | "success" | "warn" | "danger" | "neutral" => {
    if (["SENT", "APPROVED"].includes(status)) return "success";
    if (status === "REVIEWED") return "warn";
    return "info";
  };

  const getVerdictBadgeVariant = (verdict: string): "info" | "success" | "warn" | "danger" | "neutral" => {
    if (verdict === "SUPPORTED") return "success";
    if (verdict === "NOT_SUPPORTED") return "danger";
    if (verdict === "CONDITIONAL") return "warn";
    return "info";
  };

  const columns = [
    {
      key: "version",
      header: "버전",
      render: (item: AnswerDraftResult) => `v${item.version}`,
      width: "80px",
    },
    {
      key: "status",
      header: "상태",
      render: (item: AnswerDraftResult) => (
        <Badge variant={getAnswerStatusBadgeVariant(item.status)}>
          {labelAnswerStatus(item.status)}
        </Badge>
      ),
      width: "120px",
    },
    {
      key: "verdict",
      header: "판정",
      render: (item: AnswerDraftResult) => (
        <Badge variant={getVerdictBadgeVariant(item.verdict)}>
          {labelVerdict(item.verdict)}
        </Badge>
      ),
      width: "120px",
    },
    {
      key: "confidence",
      header: "신뢰도",
      render: (item: AnswerDraftResult) => item.confidence,
      width: "100px",
    },
    {
      key: "channel",
      header: "채널",
      render: (item: AnswerDraftResult) => labelChannel(item.channel),
      width: "100px",
    },
    {
      key: "tone",
      header: "톤",
      render: (item: AnswerDraftResult) => labelTone(item.tone),
      width: "100px",
    },
  ];

  if (loading) {
    return (
      <div className="stack">
        <div className="skeleton" style={{ height: "48px" }} />
        <div className="skeleton" style={{ height: "200px" }} />
      </div>
    );
  }

  if (error) {
    return (
      <div className="card">
        <p className="status-banner status-danger" role="alert">
          {error}
        </p>
      </div>
    );
  }

  return (
    <div className="stack">
      <div className="card stack">
        <div className="page-header">
          <h3 className="section-title">
            답변 버전 이력 ({history.length}건)
          </h3>
          <button className="btn btn-ghost btn-sm" onClick={fetchHistory} disabled={loading}>
            새로고침
          </button>
        </div>

        <hr className="divider" />

        <DataTable
          columns={columns}
          data={history}
          onRowClick={(item) => setSelected(item)}
          emptyMessage="생성된 답변 버전이 없습니다"
        />
      </div>

      {selected && (
        <div className="card stack">
          <h3 className="section-title">v{selected.version} 버전 상세</h3>

          <hr className="divider" />

          {/* Version summary metrics */}
          <div className="metrics-grid cols-3">
            <div className="metric-card">
              <p className="metric-label">버전</p>
              <p className="metric-value">v{selected.version}</p>
            </div>
            <div className="metric-card">
              <p className="metric-label">상태</p>
              <div className="metric-value" style={{ fontSize: "var(--font-size-lg)" }}>
                <Badge variant={getAnswerStatusBadgeVariant(selected.status)}>
                  {labelAnswerStatus(selected.status)}
                </Badge>
              </div>
            </div>
            <div className="metric-card">
              <p className="metric-label">판정 / 신뢰도</p>
              <div className="metric-value" style={{ fontSize: "var(--font-size-lg)" }}>
                <Badge variant={getVerdictBadgeVariant(selected.verdict)}>
                  {labelVerdict(selected.verdict)}
                </Badge>
                <span className="muted" style={{ marginLeft: "var(--space-sm)", fontSize: "var(--font-size-sm)" }}>
                  ({selected.confidence})
                </span>
              </div>
            </div>
          </div>

          <div className="kv">
            <div>
              <b>채널:</b> {labelChannel(selected.channel)} | <b>톤:</b> {labelTone(selected.tone)}
            </div>
          </div>

          {/* Draft content */}
          {selected.draft && (
            <>
              <hr className="divider" />
              <h4 className="section-title">답변 초안</h4>
              <div className="evidence-item" style={{ whiteSpace: "pre-wrap" }}>
                {selected.draft}
              </div>
            </>
          )}

          {/* Citations */}
          {selected.citations && selected.citations.length > 0 && (
            <>
              <hr className="divider" />
              <h4 className="section-title">인용 ({selected.citations.length}건)</h4>
              <div className="stack" style={{ gap: "var(--space-xs)" }}>
                {selected.citations.map((cite, i) => (
                  <div key={i} className="evidence-item" style={{ whiteSpace: "pre-wrap" }}>
                    {cite}
                  </div>
                ))}
              </div>
            </>
          )}

          {/* Workflow trail */}
          {(selected.reviewedBy || selected.approvedBy || selected.sentBy) && (
            <>
              <hr className="divider" />
              <h4 className="section-title">워크플로우 이력</h4>
              <div className="stack">
                {selected.reviewedBy && (
                  <div className="evidence-item">
                    <Badge variant="info" style={{ marginRight: "var(--space-sm)" }}>리뷰</Badge>
                    {selected.reviewedBy}
                    {selected.reviewComment && (
                      <span className="muted"> - {selected.reviewComment}</span>
                    )}
                  </div>
                )}
                {selected.approvedBy && (
                  <div className="evidence-item">
                    <Badge variant="success" style={{ marginRight: "var(--space-sm)" }}>승인</Badge>
                    {selected.approvedBy}
                    {selected.approveComment && (
                      <span className="muted"> - {selected.approveComment}</span>
                    )}
                  </div>
                )}
                {selected.sentBy && (
                  <div className="evidence-item">
                    <Badge variant="success" style={{ marginRight: "var(--space-sm)" }}>발송</Badge>
                    {selected.sentBy} | {selected.sendChannel}
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
