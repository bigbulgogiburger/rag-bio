import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useSendDraft } from "../useSendDraft";

const mockSendDraft = vi.fn();

vi.mock("@/lib/api/client", () => ({
  sendAnswerDraft: (...args: unknown[]) => mockSendDraft(...args),
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

vi.mock("@/hooks/queries/useInquiries", () => ({
  inquiriesKeys: {
    all: ["inquiries"],
    lists: () => ["inquiries", "list"],
    list: (params: unknown) => ["inquiries", "list", params],
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

describe("useSendDraft", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls sendAnswerDraft with params", async () => {
    const mockResult = { answerId: "a-1", status: "SENT", version: 4 };
    mockSendDraft.mockResolvedValue(mockResult);

    const { result } = renderHook(() => useSendDraft(), { wrapper: createWrapper() });

    await act(async () => {
      result.current.mutate({
        inquiryId: "i-1",
        answerId: "a-1",
        actor: "sender",
        channel: "email",
        sendRequestId: "req-1",
      });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockSendDraft).toHaveBeenCalledWith("i-1", "a-1", "sender", "email", "req-1");
  });
});
