const metrics = [
  { label: "진행 중 문의", value: "12" },
  { label: "응답 시간 P95", value: "7.4초" },
  { label: "근거 커버리지", value: "91%" },
  { label: "승인 대기", value: "4" }
];

export default function DashboardPage() {
  return (
    <section className="metrics-grid cols-2">
      {metrics.map((metric) => (
        <article className="card" key={metric.label}>
          <p className="muted" style={{ margin: 0, fontSize: ".88rem" }}>{metric.label}</p>
          <p style={{ margin: ".45rem 0 0", fontSize: "1.9rem", fontWeight: 800 }}>{metric.value}</p>
        </article>
      ))}
      <article className="card" style={{ gridColumn: "1 / -1" }}>
        <h2 className="section-title">오늘의 워크플로우 요약</h2>
        <p className="muted" style={{ marginBottom: 0 }}>
          문의 접수 → 상태 확인 → 분석 → 답변 생성/승인/발송 흐름을 이 화면에서 빠르게 점검할 수 있습니다.
        </p>
      </article>
    </section>
  );
}
