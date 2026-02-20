import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import FilterBar from "../FilterBar";

const fields = [
  {
    key: "status",
    label: "상태",
    type: "select" as const,
    options: [
      { value: "", label: "전체" },
      { value: "RECEIVED", label: "접수됨" },
      { value: "CLOSED", label: "종료" },
    ],
  },
  {
    key: "keyword",
    label: "검색어",
    type: "text" as const,
    placeholder: "검색...",
  },
  {
    key: "from",
    label: "시작일",
    type: "date" as const,
  },
];

describe("FilterBar", () => {
  const defaultProps = {
    fields,
    values: { status: "", keyword: "", from: "" },
    onChange: vi.fn(),
    onSearch: vi.fn(),
  };

  it("renders all filter fields", () => {
    render(<FilterBar {...defaultProps} />);
    expect(screen.getByLabelText("상태")).toBeInTheDocument();
    expect(screen.getByLabelText("검색어")).toBeInTheDocument();
    expect(screen.getByLabelText("시작일")).toBeInTheDocument();
  });

  it("renders select options", () => {
    render(<FilterBar {...defaultProps} />);
    const select = screen.getByLabelText("상태");
    expect(select).toBeInTheDocument();
    expect(select.querySelectorAll("option")).toHaveLength(3);
  });

  it("calls onChange when select value changes", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();

    render(<FilterBar {...defaultProps} onChange={onChange} />);
    await user.selectOptions(screen.getByLabelText("상태"), "RECEIVED");
    expect(onChange).toHaveBeenCalledWith("status", "RECEIVED");
  });

  it("calls onChange when text input changes", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();

    render(<FilterBar {...defaultProps} onChange={onChange} />);
    await user.type(screen.getByLabelText("검색어"), "test");
    expect(onChange).toHaveBeenCalled();
  });

  it("calls onSearch when search button is clicked", async () => {
    const onSearch = vi.fn();
    const user = userEvent.setup();

    render(<FilterBar {...defaultProps} onSearch={onSearch} />);
    await user.click(screen.getByRole("button", { name: /검색/ }));
    expect(onSearch).toHaveBeenCalled();
  });

  it("calls onSearch on Enter key in text input", async () => {
    const onSearch = vi.fn();
    const user = userEvent.setup();

    render(<FilterBar {...defaultProps} onSearch={onSearch} />);
    const input = screen.getByLabelText("검색어");
    await user.click(input);
    await user.keyboard("{Enter}");
    expect(onSearch).toHaveBeenCalled();
  });

  it("has search role and aria-label", () => {
    render(<FilterBar {...defaultProps} />);
    expect(screen.getByRole("search")).toHaveAttribute("aria-label", "필터 검색");
  });

  it("renders text input with placeholder", () => {
    render(<FilterBar {...defaultProps} />);
    expect(screen.getByPlaceholderText("검색...")).toBeInTheDocument();
  });
});
