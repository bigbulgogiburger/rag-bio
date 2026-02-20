import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useReviewDraft } from "../useReviewDraft";

const mockReviewDraft = vi.fn();

vi.mock("@/lib/api/client", () => ({
  reviewAnswerDraft: (...args: unknown[]) => mockReviewDraft(...args),
}));

vi.mock("@/hooks/queries/useAnswerDraft", () => ({
  answerDraftKeys: {
    all: ["answerDraft"],
    latest: (id: string) => ["answerDraft", "latest", id],
  },
}));

vi.mock("@/hooks/queries/useAnswerHistory", () => ({
  answerHistoryKeys: {
    all: ["answerHistory"],
    list: (id: string) => ["answerHistory", id],
  },
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe("useReviewDraft", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls reviewAnswerDraft with params", async () => {
    const mockResult = { answerId: "a-1", status: "REVIEWED", version: 2 };
    mockReviewDraft.mockResolvedValue(mockResult);

    const { result } = renderHook(() => useReviewDraft(), { wrapper: createWrapper() });

    await act(async () => {
      result.current.mutate({ inquiryId: "i-1", answerId: "a-1", actor: "reviewer", comment: "LGTM" });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockReviewDraft).toHaveBeenCalledWith("i-1", "a-1", "reviewer", "LGTM");
  });
});
