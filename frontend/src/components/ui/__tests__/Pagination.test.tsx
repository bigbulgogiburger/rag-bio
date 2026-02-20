import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Pagination from "../Pagination";

describe("Pagination", () => {
  const defaultProps = {
    page: 0,
    totalPages: 5,
    totalElements: 100,
    size: 20,
    onPageChange: vi.fn(),
    onSizeChange: vi.fn(),
  };

  it("displays total elements count", () => {
    render(<Pagination {...defaultProps} />);
    expect(screen.getByText("100")).toBeInTheDocument();
  });

  it("displays current range", () => {
    render(<Pagination {...defaultProps} />);
    expect(screen.getByText(/1-20/)).toBeInTheDocument();
  });

  it("disables previous button on first page", () => {
    render(<Pagination {...defaultProps} page={0} />);
    expect(screen.getByLabelText("이전 페이지")).toBeDisabled();
  });

  it("disables next button on last page", () => {
    render(<Pagination {...defaultProps} page={4} />);
    expect(screen.getByLabelText("다음 페이지")).toBeDisabled();
  });

  it("calls onPageChange when next is clicked", async () => {
    const onPageChange = vi.fn();
    const user = userEvent.setup();

    render(<Pagination {...defaultProps} onPageChange={onPageChange} />);
    await user.click(screen.getByLabelText("다음 페이지"));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it("calls onPageChange when previous is clicked", async () => {
    const onPageChange = vi.fn();
    const user = userEvent.setup();

    render(<Pagination {...defaultProps} page={2} onPageChange={onPageChange} />);
    await user.click(screen.getByLabelText("이전 페이지"));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it("calls onPageChange when page number is clicked", async () => {
    const onPageChange = vi.fn();
    const user = userEvent.setup();

    render(<Pagination {...defaultProps} onPageChange={onPageChange} />);
    await user.click(screen.getByLabelText("페이지 3"));
    expect(onPageChange).toHaveBeenCalledWith(2);
  });

  it("highlights current page", () => {
    render(<Pagination {...defaultProps} page={2} />);
    const currentPageBtn = screen.getByLabelText("페이지 3");
    expect(currentPageBtn).toHaveAttribute("aria-current", "page");
  });

  it("has navigation role and aria-label", () => {
    render(<Pagination {...defaultProps} />);
    expect(screen.getByRole("navigation")).toHaveAttribute("aria-label", "페이지네이션");
  });

  it("calls onSizeChange when size selector changes", async () => {
    const onSizeChange = vi.fn();
    const user = userEvent.setup();

    render(<Pagination {...defaultProps} onSizeChange={onSizeChange} />);
    await user.selectOptions(screen.getByLabelText("페이지당 항목 수"), "50");
    expect(onSizeChange).toHaveBeenCalledWith(50);
  });

  it("shows correct range for middle page", () => {
    render(<Pagination {...defaultProps} page={2} />);
    expect(screen.getByText(/41-60/)).toBeInTheDocument();
  });

  it("handles zero total elements", () => {
    render(<Pagination {...defaultProps} totalElements={0} totalPages={0} />);
    expect(screen.getByText("0")).toBeInTheDocument();
  });
});
