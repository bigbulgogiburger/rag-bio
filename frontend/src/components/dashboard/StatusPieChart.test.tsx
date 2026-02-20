import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import StatusPieChart from "./StatusPieChart";
import type { InquiryListResponse } from "@/lib/api/client";

// Mock Recharts
vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
  PieChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="pie-chart">{children}</div>
  ),
  Pie: ({ data, children }: { data: { name: string; value: number }[]; children: React.ReactNode }) => (
    <div data-testid="pie" data-count={data.length}>
      {data.map((d) => (
        <span key={d.name} data-testid={`pie-entry-${d.name}`}>
          {d.name}: {d.value}
        </span>
      ))}
      {children}
    </div>
  ),
  Cell: ({ fill }: { fill: string }) => <div data-testid="cell" data-fill={fill} />,
  Tooltip: () => <div data-testid="tooltip" />,
  Legend: () => <div data-testid="legend" />,
}));

describe("StatusPieChart", () => {
  it("shows empty state when inquiries is null", () => {
    render(<StatusPieChart inquiries={null} />);
    expect(screen.getByText("표시할 데이터가 없습니다")).toBeInTheDocument();
  });

  it("shows empty state when content is empty", () => {
    const inquiries: InquiryListResponse = {
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    };
    render(<StatusPieChart inquiries={inquiries} />);
    expect(screen.getByText("표시할 데이터가 없습니다")).toBeInTheDocument();
  });

  it("renders pie chart with status distribution", () => {
    const inquiries: InquiryListResponse = {
      content: [
        { inquiryId: "1", question: "q1", customerChannel: "email", status: "RECEIVED", createdAt: "2025-01-01T00:00:00Z" },
        { inquiryId: "2", question: "q2", customerChannel: "email", status: "RECEIVED", createdAt: "2025-01-01T00:00:00Z" },
        { inquiryId: "3", question: "q3", customerChannel: "email", status: "ANSWERED", createdAt: "2025-01-01T00:00:00Z" },
      ],
      page: 0,
      size: 20,
      totalElements: 3,
      totalPages: 1,
    };

    render(<StatusPieChart inquiries={inquiries} />);
    expect(screen.getByTestId("pie-chart")).toBeInTheDocument();
    expect(screen.getByTestId("pie")).toBeInTheDocument();
    // Should have 2 status groups (RECEIVED and ANSWERED)
    expect(screen.getByTestId("pie")).toHaveAttribute("data-count", "2");
  });

  it("counts statuses correctly", () => {
    const inquiries: InquiryListResponse = {
      content: [
        { inquiryId: "1", question: "q1", customerChannel: "email", status: "RECEIVED", createdAt: "2025-01-01T00:00:00Z" },
        { inquiryId: "2", question: "q2", customerChannel: "email", status: "RECEIVED", createdAt: "2025-01-01T00:00:00Z" },
        { inquiryId: "3", question: "q3", customerChannel: "email", status: "RECEIVED", createdAt: "2025-01-01T00:00:00Z" },
        { inquiryId: "4", question: "q4", customerChannel: "email", status: "CLOSED", createdAt: "2025-01-01T00:00:00Z" },
      ],
      page: 0,
      size: 20,
      totalElements: 4,
      totalPages: 1,
    };

    render(<StatusPieChart inquiries={inquiries} />);
    // RECEIVED=3, CLOSED=1 -> labelInquiryStatus maps these to Korean
    const pie = screen.getByTestId("pie");
    expect(pie.textContent).toContain("3");
    expect(pie.textContent).toContain("1");
  });
});
