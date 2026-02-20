import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import DataTable from "../DataTable";

interface TestItem {
  id: string;
  name: string;
  status: string;
}

const columns = [
  { key: "id", header: "ID" },
  { key: "name", header: "이름" },
  {
    key: "status",
    header: "상태",
    render: (item: TestItem) => <span data-testid={`status-${item.id}`}>{item.status}</span>,
  },
];

const sampleData: TestItem[] = [
  { id: "1", name: "항목 1", status: "active" },
  { id: "2", name: "항목 2", status: "inactive" },
  { id: "3", name: "항목 3", status: "active" },
];

describe("DataTable", () => {
  it("renders column headers", () => {
    render(<DataTable columns={columns} data={sampleData} />);
    expect(screen.getByText("ID")).toBeInTheDocument();
    expect(screen.getByText("이름")).toBeInTheDocument();
    expect(screen.getByText("상태")).toBeInTheDocument();
  });

  it("renders data rows", () => {
    render(<DataTable columns={columns} data={sampleData} />);
    expect(screen.getByText("항목 1")).toBeInTheDocument();
    expect(screen.getByText("항목 2")).toBeInTheDocument();
    expect(screen.getByText("항목 3")).toBeInTheDocument();
  });

  it("uses custom render function for columns", () => {
    render(<DataTable columns={columns} data={sampleData} />);
    expect(screen.getByTestId("status-1")).toHaveTextContent("active");
    expect(screen.getByTestId("status-2")).toHaveTextContent("inactive");
  });

  it("shows empty message when data is empty", () => {
    render(<DataTable columns={columns} data={[]} emptyMessage="데이터 없음" />);
    expect(screen.getByText("데이터 없음")).toBeInTheDocument();
  });

  it("shows default empty message", () => {
    render(<DataTable columns={columns} data={[]} />);
    expect(screen.getByText("데이터가 없습니다")).toBeInTheDocument();
  });

  it("calls onRowClick when row is clicked", async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();

    render(<DataTable columns={columns} data={sampleData} onRowClick={onClick} />);

    await user.click(screen.getByText("항목 1"));
    expect(onClick).toHaveBeenCalledWith(sampleData[0]);
  });

  it("calls onRowClick on Enter key", async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();

    render(<DataTable columns={columns} data={sampleData} onRowClick={onClick} />);

    const rows = screen.getAllByRole("button");
    rows[0].focus();
    await user.keyboard("{Enter}");
    expect(onClick).toHaveBeenCalledWith(sampleData[0]);
  });

  it("has table role and aria-label", () => {
    render(<DataTable columns={columns} data={sampleData} />);
    expect(screen.getByRole("table")).toHaveAttribute("aria-label", "데이터 테이블");
  });

  it("renders without onRowClick - rows are not buttons", () => {
    render(<DataTable columns={columns} data={sampleData} />);
    expect(screen.queryAllByRole("button")).toHaveLength(0);
  });
});
