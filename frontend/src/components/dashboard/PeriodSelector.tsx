"use client";

import type { DashboardPeriod } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface PeriodSelectorProps {
  value: DashboardPeriod;
  onChange: (period: DashboardPeriod) => void;
}

const periods: { key: DashboardPeriod; label: string }[] = [
  { key: "today", label: "오늘" },
  { key: "7d", label: "7일" },
  { key: "30d", label: "30일" },
  { key: "90d", label: "90일" },
];

export default function PeriodSelector({ value, onChange }: PeriodSelectorProps) {
  return (
    <div className="flex items-center gap-1" role="group" aria-label="기간 선택">
      {periods.map((p) => (
        <Button
          key={p.key}
          variant={value === p.key ? "default" : "ghost"}
          size="sm"
          className={cn("h-8 text-xs", value === p.key && "pointer-events-none")}
          onClick={() => onChange(p.key)}
          aria-pressed={value === p.key}
        >
          {p.label}
        </Button>
      ))}
    </div>
  );
}
