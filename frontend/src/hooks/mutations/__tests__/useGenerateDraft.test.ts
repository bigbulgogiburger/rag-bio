import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useGenerateDraft } from "../useGenerateDraft";

const mockDraftInquiryAnswer = vi.fn();

vi.mock("@/lib/api/client", () => ({
  draftInquiryAnswer: (...args: unknown[]) => mockDraftInquiryAnswer(...args),
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
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  return { wrapper: function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  }, queryClient };
}

describe("useGenerateDraft", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls draftInquiryAnswer and returns result", async () => {
    const mockResult = {
      answerId: "a-1",
      inquiryId: "i-1",
      version: 1,
      status: "DRAFT",
      draft: "Generated answer",
    };
    mockDraftInquiryAnswer.mockResolvedValue(mockResult);

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useGenerateDraft(), { wrapper });

    await act(async () => {
      result.current.mutate({
        inquiryId: "i-1",
        question: "Test question",
        tone: "professional",
        channel: "email",
      });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockResult);
    expect(mockDraftInquiryAnswer).toHaveBeenCalledWith("i-1", "Test question", "professional", "email");
  });

  it("handles mutation error", async () => {
    mockDraftInquiryAnswer.mockRejectedValue(new Error("Draft failed"));

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useGenerateDraft(), { wrapper });

    await act(async () => {
      result.current.mutate({ inquiryId: "i-1", question: "Test" });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe("Draft failed");
  });
});
