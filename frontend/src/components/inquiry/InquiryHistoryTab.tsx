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

  useEffect(() => {
    fetchHistory();
  }, [inquiryId]);

  const fetchHistory = async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await listAnswerDraftHistory(inquiryId);
      setHistory(data);
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
      <div className="card">
        <p className="muted">버전 이력을 불러오는 중...</p>
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
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <h3 className="section-title" style={{ margin: 0 }}>
            답변 버전 이력 ({history.length}건)
          </h3>
          <button className="btn" onClick={fetchHistory} disabled={loading}>
            새로고침
          </button>
        </div>

        <DataTable
          columns={columns}
          data={history}
          emptyMessage="생성된 답변 버전이 없습니다"
        />
      </div>

      {history.length > 0 && (
        <div className="card stack">
          <h3 className="section-title">최신 버전 상세</h3>
          <div className="kv">
            <div>
              <b>버전:</b> v{history[0].version}
            </div>
            <div>
              <b>상태:</b>{" "}
              <Badge variant={getAnswerStatusBadgeVariant(history[0].status)}>
                {labelAnswerStatus(history[0].status)}
              </Badge>
            </div>
            <div>
              <b>판정:</b>{" "}
              <Badge variant={getVerdictBadgeVariant(history[0].verdict)}>
                {labelVerdict(history[0].verdict)}
              </Badge>{" "}
              (신뢰도 {history[0].confidence})
            </div>
            <div>
              <b>채널:</b> {labelChannel(history[0].channel)} | <b>톤:</b> {labelTone(history[0].tone)}
            </div>
            {history[0].reviewedBy && (
              <div>
                <b>리뷰:</b> {history[0].reviewedBy}
                {history[0].reviewComment && ` - ${history[0].reviewComment}`}
              </div>
            )}
            {history[0].approvedBy && (
              <div>
                <b>승인:</b> {history[0].approvedBy}
                {history[0].approveComment && ` - ${history[0].approveComment}`}
              </div>
            )}
            {history[0].sentBy && (
              <div>
                <b>발송:</b> {history[0].sentBy} | {history[0].sendChannel}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
