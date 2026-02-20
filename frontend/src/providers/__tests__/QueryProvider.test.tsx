import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import QueryProvider from "../QueryProvider";

vi.mock("@tanstack/react-query-devtools", () => ({
  ReactQueryDevtools: () => null,
}));

describe("QueryProvider", () => {
  it("renders children within QueryClientProvider", () => {
    render(
      <QueryProvider>
        <div>테스트 자식 컴포넌트</div>
      </QueryProvider>,
    );
    expect(screen.getByText("테스트 자식 컴포넌트")).toBeInTheDocument();
  });
});
