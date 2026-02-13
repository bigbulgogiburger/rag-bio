"use client";

import { useEffect, useState } from "react";
import { getOpsMetrics, type OpsMetrics } from "@/lib/api/client";

export default function DashboardPage() {
  const [metrics, setMetrics] = useState<OpsMetrics | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getOpsMetrics()
      .then(setMetrics)
      .catch((e) => setError(e instanceof Error ? e.message : "지표를 불러오지 못했습니다."));
  }, []);

  const cards = [
    { label: "발송 성공률", value: metrics ? `${metrics.sendSuccessRate}%` : "-" },
    { label: "발송 완료 건", value: metrics ? `${metrics.sentCount}/${metrics.approvedOrSentCount}` : "-" },
    { label: "Fallback 비율", value: metrics ? `${metrics.fallbackDraftRate}%` : "-" },
    { label: "Fallback 건", value: metrics ? `${metrics.fallbackDraftCount}/${metrics.totalDraftCount}` : "-" }
  ];

  return (
    <section className="metrics-grid cols-2">
      {cards.map((metric) => (
        <article className="card" key={metric.label}>
          <p className="muted" style={{ margin: 0, fontSize: ".88rem" }}>{metric.label}</p>
          <p style={{ margin: ".45rem 0 0", fontSize: "1.9rem", fontWeight: 800 }}>{metric.value}</p>
        </article>
      ))}

      <article className="card" style={{ gridColumn: "1 / -1" }}>
        <h2 className="section-title">최근 실패 사유 Top</h2>
        {error && <p className="muted">{error}</p>}
        {!error && (
          <ul style={{ margin: 0, paddingLeft: "1.1rem" }}>
            {(metrics?.topFailureReasons ?? []).length === 0 ? (
              <li>실패 사유 데이터 없음</li>
            ) : (
              metrics?.topFailureReasons.map((item, idx) => (
                <li key={`${item.reason}-${idx}`}>{item.reason} · {item.count}건</li>
              ))
            )}
          </ul>
        )}
      </article>
    </section>
  );
}
