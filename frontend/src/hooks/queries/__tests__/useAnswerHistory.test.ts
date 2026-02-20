import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useAnswerHistory, answerHistoryKeys } from "../useAnswerHistory";

const mockListHistory = vi.fn();

vi.mock("@/lib/api/client", () => ({
  listAnswerDraftHistory: (...args: unknown[]) => mockListHistory(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe("useAnswerHistory", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches answer draft history", async () => {
    const mockData = [
      { answerId: "a-1", version: 1, status: "DRAFT" },
      { answerId: "a-2", version: 2, status: "REVIEWED" },
    ];
    mockListHistory.mockResolvedValue(mockData);

    const { result } = renderHook(() => useAnswerHistory("i-1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockData);
    expect(mockListHistory).toHaveBeenCalledWith("i-1");
  });

  it("does not fetch when inquiryId is empty", () => {
    const { result } = renderHook(() => useAnswerHistory(""), {
      wrapper: createWrapper(),
    });

    expect(result.current.fetchStatus).toBe("idle");
    expect(mockListHistory).not.toHaveBeenCalled();
  });

  it("does not fetch when disabled", () => {
    const { result } = renderHook(
      () => useAnswerHistory("i-1", { enabled: false }),
      { wrapper: createWrapper() },
    );

    expect(result.current.fetchStatus).toBe("idle");
  });
});

describe("answerHistoryKeys", () => {
  it("generates correct key structure", () => {
    expect(answerHistoryKeys.all).toEqual(["answerHistory"]);
    expect(answerHistoryKeys.list("i-1")).toEqual(["answerHistory", "i-1"]);
  });
});
