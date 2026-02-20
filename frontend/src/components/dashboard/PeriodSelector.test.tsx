import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import PeriodSelector from "./PeriodSelector";

describe("PeriodSelector", () => {
  it("renders all four period buttons", () => {
    render(<PeriodSelector value="7d" onChange={vi.fn()} />);
    expect(screen.getByText("오늘")).toBeInTheDocument();
    expect(screen.getByText("7일")).toBeInTheDocument();
    expect(screen.getByText("30일")).toBeInTheDocument();
    expect(screen.getByText("90일")).toBeInTheDocument();
  });

  it("marks the selected period with aria-pressed", () => {
    render(<PeriodSelector value="30d" onChange={vi.fn()} />);
    expect(screen.getByText("30일")).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByText("7일")).toHaveAttribute("aria-pressed", "false");
  });

  it("calls onChange when a different period is clicked", () => {
    const onChange = vi.fn();
    render(<PeriodSelector value="7d" onChange={onChange} />);

    fireEvent.click(screen.getByText("30일"));
    expect(onChange).toHaveBeenCalledWith("30d");
  });

  it("calls onChange with 'today' when 오늘 is clicked", () => {
    const onChange = vi.fn();
    render(<PeriodSelector value="7d" onChange={onChange} />);

    fireEvent.click(screen.getByText("오늘"));
    expect(onChange).toHaveBeenCalledWith("today");
  });

  it("has group role with aria-label", () => {
    render(<PeriodSelector value="7d" onChange={vi.fn()} />);
    expect(screen.getByRole("group")).toHaveAttribute("aria-label", "기간 선택");
  });
});
