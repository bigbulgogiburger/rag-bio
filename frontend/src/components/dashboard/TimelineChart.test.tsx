import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import TimelineChart from "./TimelineChart";
import type { TimelineDailyMetric } from "@/lib/api/client";

// Mock Recharts to avoid DOM measurement issues in jsdom
vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
  LineChart: ({ children, data }: { children: React.ReactNode; data: unknown[] }) => (
    <div data-testid="line-chart" data-length={data.length}>{children}</div>
  ),
  Line: ({ dataKey, name }: { dataKey: string; name: string }) => (
    <div data-testid={`line-${dataKey}`}>{name}</div>
  ),
  XAxis: () => <div data-testid="x-axis" />,
  YAxis: () => <div data-testid="y-axis" />,
  CartesianGrid: () => <div data-testid="grid" />,
  Tooltip: () => <div data-testid="tooltip" />,
  Legend: () => <div data-testid="legend" />,
}));

const sampleData: TimelineDailyMetric[] = [
  { date: "2025-01-01", inquiriesCreated: 5, draftsCreated: 3, answersSent: 2 },
  { date: "2025-01-02", inquiriesCreated: 8, draftsCreated: 6, answersSent: 4 },
  { date: "2025-01-03", inquiriesCreated: 3, draftsCreated: 2, answersSent: 1 },
];

describe("TimelineChart", () => {
  it("shows empty state when data is empty", () => {
    render(<TimelineChart data={[]} />);
    expect(screen.getByText("표시할 데이터가 없습니다")).toBeInTheDocument();
    expect(screen.queryByTestId("line-chart")).not.toBeInTheDocument();
  });

  it("renders chart with data", () => {
    render(<TimelineChart data={sampleData} />);
    expect(screen.queryByText("표시할 데이터가 없습니다")).not.toBeInTheDocument();
    expect(screen.getByTestId("responsive-container")).toBeInTheDocument();
    expect(screen.getByTestId("line-chart")).toBeInTheDocument();
  });

  it("renders three Line components for each metric", () => {
    render(<TimelineChart data={sampleData} />);
    expect(screen.getByTestId("line-inquiriesCreated")).toBeInTheDocument();
    expect(screen.getByTestId("line-draftsCreated")).toBeInTheDocument();
    expect(screen.getByTestId("line-answersSent")).toBeInTheDocument();
  });

  it("shows Korean labels for each line", () => {
    render(<TimelineChart data={sampleData} />);
    expect(screen.getByText("문의 접수")).toBeInTheDocument();
    expect(screen.getByText("초안 생성")).toBeInTheDocument();
    expect(screen.getByText("답변 발송")).toBeInTheDocument();
  });

  it("passes correct data length to chart", () => {
    render(<TimelineChart data={sampleData} />);
    expect(screen.getByTestId("line-chart")).toHaveAttribute("data-length", "3");
  });
});
