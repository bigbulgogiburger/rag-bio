import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useInquiry, inquiryKeys } from "../useInquiry";

const mockGetInquiry = vi.fn();

vi.mock("@/lib/api/client", () => ({
  getInquiry: (...args: unknown[]) => mockGetInquiry(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe("useInquiry", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches inquiry detail by id", async () => {
    const mockData = {
      inquiryId: "test-id",
      question: "Test question",
      customerChannel: "email",
      status: "RECEIVED",
      createdAt: "2025-01-01T00:00:00Z",
    };
    mockGetInquiry.mockResolvedValue(mockData);

    const { result } = renderHook(() => useInquiry("test-id"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockData);
    expect(mockGetInquiry).toHaveBeenCalledWith("test-id");
  });

  it("does not fetch when inquiryId is empty", () => {
    const { result } = renderHook(() => useInquiry(""), {
      wrapper: createWrapper(),
    });

    expect(result.current.fetchStatus).toBe("idle");
    expect(mockGetInquiry).not.toHaveBeenCalled();
  });
});

describe("inquiryKeys", () => {
  it("generates correct key structure", () => {
    expect(inquiryKeys.all).toEqual(["inquiry"]);
    expect(inquiryKeys.detail("abc")).toEqual(["inquiry", "abc"]);
  });
});
