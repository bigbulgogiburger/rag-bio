import { describe, it, expect, vi } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useCreateInquiry } from "../useCreateInquiry";

const mockCreateInquiry = vi.fn();

vi.mock("@/lib/api/client", () => ({
  createInquiry: (...args: unknown[]) => mockCreateInquiry(...args),
}));

// Need to also mock the keys import
vi.mock("@/hooks/queries/useInquiries", () => ({
  inquiriesKeys: {
    all: ["inquiries"],
    lists: () => ["inquiries", "list"],
    list: (params: unknown) => ["inquiries", "list", params],
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

describe("useCreateInquiry", () => {
  it("calls createInquiry and returns result", async () => {
    const mockResult = { inquiryId: "new-id", status: "RECEIVED", message: "Created" };
    mockCreateInquiry.mockResolvedValue(mockResult);

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useCreateInquiry(), { wrapper });

    await act(async () => {
      result.current.mutate({ question: "Test Q", customerChannel: "email" });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockResult);
    expect(mockCreateInquiry).toHaveBeenCalledWith({ question: "Test Q", customerChannel: "email" });
  });

  it("handles mutation error", async () => {
    mockCreateInquiry.mockRejectedValue(new Error("Failed"));

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useCreateInquiry(), { wrapper });

    await act(async () => {
      result.current.mutate({ question: "Test" });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe("Failed");
  });
});
