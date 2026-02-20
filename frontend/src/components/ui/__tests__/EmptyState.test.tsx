import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import EmptyState from "../EmptyState";

describe("EmptyState", () => {
  it("renders title", () => {
    render(<EmptyState title="데이터 없음" />);
    expect(screen.getByText("데이터 없음")).toBeInTheDocument();
  });

  it("renders description when provided", () => {
    render(<EmptyState title="없음" description="설명 텍스트" />);
    expect(screen.getByText("설명 텍스트")).toBeInTheDocument();
  });

  it("does not render description when not provided", () => {
    const { container } = render(<EmptyState title="없음" />);
    expect(container.querySelectorAll("p")).toHaveLength(0);
  });

  it("renders action button when provided", () => {
    const onClick = vi.fn();
    render(
      <EmptyState title="없음" action={{ label: "추가하기", onClick }} />,
    );
    expect(screen.getByRole("button", { name: "추가하기" })).toBeInTheDocument();
  });

  it("calls action onClick when button clicked", async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();

    render(
      <EmptyState title="없음" action={{ label: "추가하기", onClick }} />,
    );

    await user.click(screen.getByRole("button", { name: "추가하기" }));
    expect(onClick).toHaveBeenCalledOnce();
  });

  it("does not render button when no action", () => {
    render(<EmptyState title="없음" />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
