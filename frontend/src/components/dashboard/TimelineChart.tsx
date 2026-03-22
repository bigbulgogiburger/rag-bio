"use client";

import { useState, useEffect } from "react";
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
} from "recharts";
import type { TimelineDailyMetric } from "@/lib/api/client";

interface TimelineChartProps {
  data: TimelineDailyMetric[];
}

function formatDate(dateStr: string): string {
  const [, m, d] = dateStr.split("-");
  return `${m}/${d}`;
}

function getCssVar(name: string): string {
  if (typeof window === "undefined") return "";
  const raw = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return raw ? `hsl(${raw})` : "";
}

export default function TimelineChart({ data }: TimelineChartProps) {
  const [colors, setColors] = useState({
    primary: "hsl(224, 71%, 48%)",
    warning: "hsl(38, 92%, 50%)",
    success: "hsl(160, 84%, 39%)",
    border: "hsl(220, 13%, 91%)",
    card: "hsl(0, 0%, 100%)",
    foreground: "hsl(224, 71%, 8%)",
    muted: "hsl(220, 9%, 46%)",
  });

  useEffect(() => {
    const update = () => {
      setColors({
        primary: getCssVar("--primary") || colors.primary,
        warning: getCssVar("--warning") || colors.warning,
        success: getCssVar("--success") || colors.success,
        border: getCssVar("--border") || colors.border,
        card: getCssVar("--card") || colors.card,
        foreground: getCssVar("--foreground") || colors.foreground,
        muted: getCssVar("--muted-foreground") || colors.muted,
      });
    };
    update();
    // Re-read on theme change
    const observer = new MutationObserver(update);
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
    return () => observer.disconnect();
  }, []);

  if (data.length === 0) {
    return (
      <div className="flex h-[300px] items-center justify-center text-sm text-muted-foreground">
        표시할 데이터가 없습니다
      </div>
    );
  }

  const formatted = data.map((d) => ({
    ...d,
    dateLabel: formatDate(d.date),
  }));

  return (
    <ResponsiveContainer width="100%" height={300}>
      <LineChart data={formatted} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
        <CartesianGrid strokeDasharray="3 3" stroke={colors.border} />
        <XAxis
          dataKey="dateLabel"
          tick={{ fontSize: 11, fill: colors.muted }}
          interval="preserveStartEnd"
        />
        <YAxis
          allowDecimals={false}
          tick={{ fontSize: 11, fill: colors.muted }}
          width={40}
        />
        <Tooltip
          contentStyle={{
            borderRadius: "var(--radius)",
            border: `1px solid ${colors.border}`,
            backgroundColor: colors.card,
            color: colors.foreground,
            fontSize: "12px",
          }}
          labelFormatter={(label) => `일자: ${label}`}
        />
        <Legend wrapperStyle={{ fontSize: "12px", paddingTop: "8px" }} />
        <Line type="monotone" dataKey="answersSent" name="답변 발송"
          stroke={colors.success} strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
        <Line type="monotone" dataKey="inquiriesCreated" name="문의 접수"
          stroke={colors.primary} strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
        <Line type="monotone" dataKey="draftsCreated" name="초안 생성"
          stroke={colors.warning} strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
      </LineChart>
    </ResponsiveContainer>
  );
}
