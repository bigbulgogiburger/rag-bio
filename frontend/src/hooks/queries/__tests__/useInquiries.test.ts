import { describe, it, expect, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useInquiries, inquiriesKeys } from "../useInquiries";

const mockListInquiries = vi.fn();

vi.mock("@/lib/api/client", () => ({
  listInquiries: (...args: unknown[]) => mockListInquiries(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe("useInquiries", () => {
  it("fetches inquiry list", async () => {
    const mockData = {
      content: [{ inquiryId: "1", question: "Q1", status: "RECEIVED" }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };
    mockListInquiries.mockResolvedValue(mockData);

    const { result } = renderHook(() => useInquiries({ page: 0, size: 20 }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockData);
    expect(mockListInquiries).toHaveBeenCalledWith({ page: 0, size: 20 });
  });

  it("handles error state", async () => {
    mockListInquiries.mockRejectedValue(new Error("Network error"));

    const { result } = renderHook(() => useInquiries(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe("Network error");
  });
});

describe("inquiriesKeys", () => {
  it("generates correct key structure", () => {
    expect(inquiriesKeys.all).toEqual(["inquiries"]);
    expect(inquiriesKeys.lists()).toEqual(["inquiries", "list"]);
    expect(inquiriesKeys.list({ page: 0 })).toEqual(["inquiries", "list", { page: 0 }]);
  });
});
