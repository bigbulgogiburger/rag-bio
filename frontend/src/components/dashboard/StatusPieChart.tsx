"use client";

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

const STATUS_COLORS: Record<string, string> = {
  RECEIVED: "hsl(221, 83%, 53%)",
  ANALYZED: "hsl(38, 92%, 50%)",
  ANSWERED: "hsl(160, 84%, 39%)",
  CLOSED: "hsl(215, 16%, 57%)",
};

export default function StatusPieChart({ inquiries }: StatusPieChartProps) {
  if (!inquiries || inquiries.content.length === 0) {
    return (
      <div className="flex h-[250px] items-center justify-center text-sm text-muted-foreground">
        표시할 데이터가 없습니다
      </div>
    );
  }

  // Count by status
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
        <Pie
          data={data}
          cx="50%"
          cy="50%"
          innerRadius={50}
          outerRadius={80}
          dataKey="value"
          nameKey="name"
          paddingAngle={2}
        >
          {data.map((entry) => (
            <Cell
              key={entry.status}
              fill={STATUS_COLORS[entry.status] || "hsl(215, 16%, 57%)"}
            />
          ))}
        </Pie>
        <Tooltip
          contentStyle={{
            borderRadius: "var(--radius)",
            border: "1px solid hsl(var(--border))",
            backgroundColor: "hsl(var(--card))",
            color: "hsl(var(--foreground))",
            fontSize: "12px",
          }}
          formatter={(value) => [`${value}건`]}
        />
        <Legend
          wrapperStyle={{ fontSize: "12px" }}
        />
      </PieChart>
    </ResponsiveContainer>
  );
}
