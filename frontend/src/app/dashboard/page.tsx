const metrics = [
  { label: "Open Inquiries", value: "12" },
  { label: "P95 Response Time", value: "7.4s" },
  { label: "Evidence Coverage", value: "91%" },
  { label: "Approval Queue", value: "4" }
];

export default function DashboardPage() {
  return (
    <section className="grid cols-2">
      {metrics.map((metric) => (
        <article className="panel" key={metric.label}>
          <p style={{ margin: 0, color: "#5b5e56", fontSize: ".85rem" }}>{metric.label}</p>
          <p style={{ margin: ".4rem 0 0", fontSize: "1.8rem", fontWeight: 700 }}>{metric.value}</p>
        </article>
      ))}
      <article className="panel" style={{ gridColumn: "1 / -1" }}>
        <h2 style={{ marginTop: 0 }}>Workflow Snapshot</h2>
        <p style={{ color: "#5b5e56" }}>
          Retriever, Verifier, and Composer pipelines are scaffolded. Integrate backend orchestration and real audit events next.
        </p>
      </article>
    </section>
  );
}
