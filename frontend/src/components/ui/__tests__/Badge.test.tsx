import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import Badge from "../Badge";

describe("Badge", () => {
  it("renders children text", () => {
    render(<Badge variant="info">테스트 배지</Badge>);
    expect(screen.getByText("테스트 배지")).toBeInTheDocument();
  });

  it("has status role", () => {
    render(<Badge variant="success">완료</Badge>);
    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it.each(["info", "success", "warn", "danger", "neutral"] as const)(
    "renders with %s variant without error",
    (variant) => {
      const { container } = render(<Badge variant={variant}>Label</Badge>);
      expect(container.querySelector("span")).toBeInTheDocument();
    },
  );

  it("renders SVG dot indicator", () => {
    const { container } = render(<Badge variant="info">Test</Badge>);
    expect(container.querySelector("svg circle")).toBeInTheDocument();
  });

  it("accepts custom className", () => {
    render(<Badge variant="info" className="custom-class">Test</Badge>);
    expect(screen.getByRole("status")).toHaveClass("custom-class");
  });
});
