import { describe, it, expect, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useLatestAnswerDraft, answerDraftKeys } from "../useAnswerDraft";

const mockGetLatestAnswerDraft = vi.fn();

vi.mock("@/lib/api/client", () => ({
  getLatestAnswerDraft: (...args: unknown[]) => mockGetLatestAnswerDraft(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe("useLatestAnswerDraft", () => {
  it("fetches latest answer draft", async () => {
    const mockDraft = {
      answerId: "a-1",
      inquiryId: "i-1",
      version: 1,
      status: "DRAFT",
      draft: "Test answer",
    };
    mockGetLatestAnswerDraft.mockResolvedValue(mockDraft);

    const { result } = renderHook(() => useLatestAnswerDraft("i-1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockDraft);
  });

  it("does not fetch when disabled", () => {
    const { result } = renderHook(
      () => useLatestAnswerDraft("i-1", { enabled: false }),
      { wrapper: createWrapper() },
    );

    expect(result.current.fetchStatus).toBe("idle");
  });

  it("does not fetch with empty inquiryId", () => {
    const { result } = renderHook(() => useLatestAnswerDraft(""), {
      wrapper: createWrapper(),
    });

    expect(result.current.fetchStatus).toBe("idle");
  });
});

describe("answerDraftKeys", () => {
  it("generates correct key structure", () => {
    expect(answerDraftKeys.all).toEqual(["answerDraft"]);
    expect(answerDraftKeys.latest("i-1")).toEqual(["answerDraft", "latest", "i-1"]);
  });
});
