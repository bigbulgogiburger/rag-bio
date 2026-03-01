"use client";

interface PipelineStep {
  name: string;
  label: string;
  status: "pending" | "started" | "completed" | "failed" | "retry";
}

const statusConfig: Record<
  PipelineStep["status"],
  { bg: string; text: string; border: string }
> = {
  pending: {
    bg: "bg-muted/40",
    text: "text-muted-foreground",
    border: "border-border/50",
  },
  started: {
    bg: "bg-primary/10",
    text: "text-primary",
    border: "border-primary/40",
  },
  completed: {
    bg: "bg-[hsl(var(--success-light))]",
    text: "text-[hsl(var(--success-foreground))]",
    border: "border-[hsl(var(--success-border))]",
  },
  failed: {
    bg: "bg-[hsl(var(--danger-light))]",
    text: "text-[hsl(var(--danger-foreground))]",
    border: "border-[hsl(var(--danger-border))]",
  },
  retry: {
    bg: "bg-[hsl(var(--warning-light))]",
    text: "text-[hsl(var(--warning-foreground))]",
    border: "border-[hsl(var(--warning-border))]",
  },
};

function StatusIcon({ status }: { status: PipelineStep["status"] }) {
  switch (status) {
    case "completed":
      return (
        <svg
          width="14"
          height="14"
          viewBox="0 0 14 14"
          fill="none"
          aria-hidden="true"
        >
          <path
            d="M3 7.5L5.5 10L11 4"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      );
    case "failed":
      return (
        <svg
          width="14"
          height="14"
          viewBox="0 0 14 14"
          fill="none"
          aria-hidden="true"
        >
          <path
            d="M4 4L10 10M10 4L4 10"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
          />
        </svg>
      );
    case "retry":
      return (
        <svg
          width="14"
          height="14"
          viewBox="0 0 14 14"
          fill="none"
          aria-hidden="true"
          className="animate-spin"
        >
          <path
            d="M7 1.5A5.5 5.5 0 1 1 2.05 5"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
          />
          <path d="M2 1.5V5H5.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    case "started":
      return (
        <span className="inline-block h-3 w-3 animate-pulse rounded-full bg-primary" />
      );
    default:
      return (
        <span className="inline-block h-2.5 w-2.5 rounded-full border-2 border-current opacity-40" />
      );
  }
}

export function PipelineProgressBadge({ steps }: { steps: PipelineStep[] }) {
  return (
    <div
      className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none"
      role="list"
      aria-label="파이프라인 진행 상태"
    >
      {steps.map((step, idx) => {
        const config = statusConfig[step.status];
        return (
          <div key={step.name} className="flex items-center gap-1.5 shrink-0">
            <div
              role="listitem"
              className={`flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[0.65rem] font-medium leading-none ${config.bg} ${config.text} ${config.border}`}
              aria-label={`${step.label}: ${step.status}`}
            >
              <StatusIcon status={step.status} />
              <span className="whitespace-nowrap">{step.label}</span>
            </div>
            {idx < steps.length - 1 && (
              <span className="text-muted-foreground/40 text-xs shrink-0" aria-hidden="true">
                &rsaquo;
              </span>
            )}
          </div>
        );
      })}
    </div>
  );
}

export type { PipelineStep };
export default PipelineProgressBadge;
