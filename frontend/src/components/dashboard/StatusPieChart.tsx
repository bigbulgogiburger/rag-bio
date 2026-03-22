"use client";

import { useState, useEffect } from "react";
import {
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Tooltip,
  Legend,
} from "recharts";
import type { InquiryListResponse } from "@/lib/api/client";
import { labelInquiryStatus } from "@/lib/i18n/labels";

interface StatusPieChartProps {
  inquiries: InquiryListResponse | null;
}

function getCssVar(name: string): string {
  if (typeof window === "undefined") return "";
  const raw = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return raw ? `hsl(${raw})` : "";
}

export default function StatusPieChart({ inquiries }: StatusPieChartProps) {
  const [colors, setColors] = useState({
    primary: "hsl(224, 71%, 48%)",
    warning: "hsl(38, 92%, 50%)",
    success: "hsl(160, 84%, 39%)",
    muted: "hsl(220, 9%, 46%)",
    border: "hsl(220, 13%, 91%)",
    card: "hsl(0, 0%, 100%)",
    foreground: "hsl(224, 71%, 8%)",
  });

  useEffect(() => {
    const update = () => {
      setColors({
        primary: getCssVar("--primary") || colors.primary,
        warning: getCssVar("--warning") || colors.warning,
        success: getCssVar("--success") || colors.success,
        muted: getCssVar("--muted-foreground") || colors.muted,
        border: getCssVar("--border") || colors.border,
        card: getCssVar("--card") || colors.card,
        foreground: getCssVar("--foreground") || colors.foreground,
      });
    };
    update();
    const observer = new MutationObserver(update);
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
    return () => observer.disconnect();
  }, []);

  const statusColorMap: Record<string, string> = {
    RECEIVED: colors.primary,
    ANALYZED: colors.warning,
    ANSWERED: colors.success,
    CLOSED: colors.muted,
  };

  if (!inquiries || inquiries.content.length === 0) {
    return (
      <div className="flex h-[250px] items-center justify-center text-sm text-muted-foreground">
        표시할 데이터가 없습니다
      </div>
    );
  }

  const statusCounts: Record<string, number> = {};
  for (const item of inquiries.content) {
    statusCounts[item.status] = (statusCounts[item.status] || 0) + 1;
  }

  const data = Object.entries(statusCounts).map(([status, count]) => ({
    name: labelInquiryStatus(status),
    value: count,
    status,
  }));

  return (
    <ResponsiveContainer width="100%" height={250}>
      <PieChart>
        <Pie data={data} cx="50%" cy="50%" innerRadius={50} outerRadius={80}
          dataKey="value" nameKey="name" paddingAngle={2}>
          {data.map((entry) => (
            <Cell key={entry.status} fill={statusColorMap[entry.status] || colors.muted} />
          ))}
        </Pie>
        <Tooltip
          contentStyle={{
            borderRadius: "var(--radius)",
            border: `1px solid ${colors.border}`,
            backgroundColor: colors.card,
            color: colors.foreground,
            fontSize: "12px",
          }}
          formatter={(value) => [`${value}건`]}
        />
        <Legend wrapperStyle={{ fontSize: "12px" }} />
      </PieChart>
    </ResponsiveContainer>
  );
}
