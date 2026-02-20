import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useApproveDraft } from "../useApproveDraft";

const mockApproveDraft = vi.fn();

vi.mock("@/lib/api/client", () => ({
  approveAnswerDraft: (...args: unknown[]) => mockApproveDraft(...args),
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

describe("useApproveDraft", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls approveAnswerDraft with params", async () => {
    const mockResult = { answerId: "a-1", status: "APPROVED", version: 3 };
    mockApproveDraft.mockResolvedValue(mockResult);

    const { result } = renderHook(() => useApproveDraft(), { wrapper: createWrapper() });

    await act(async () => {
      result.current.mutate({ inquiryId: "i-1", answerId: "a-1", actor: "lead", comment: "Approved" });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApproveDraft).toHaveBeenCalledWith("i-1", "a-1", "lead", "Approved");
  });
});
