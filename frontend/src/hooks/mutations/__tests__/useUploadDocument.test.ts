import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useUploadDocument } from "../useUploadDocument";

const mockUploadDoc = vi.fn();

vi.mock("@/lib/api/client", () => ({
  uploadInquiryDocument: (...args: unknown[]) => mockUploadDoc(...args),
}));

vi.mock("@/hooks/queries/useInquiryDocuments", () => ({
  inquiryDocumentsKeys: {
    all: ["inquiryDocuments"],
    list: (id: string) => ["inquiryDocuments", "list", id],
    indexingStatus: (id: string) => ["inquiryDocuments", "indexingStatus", id],
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

describe("useUploadDocument", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("uploads a document file", async () => {
    const mockResult = { documentId: "d-1", fileName: "test.pdf", status: "UPLOADED" };
    mockUploadDoc.mockResolvedValue(mockResult);

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useUploadDocument("i-1"), { wrapper });

    const file = new File(["content"], "test.pdf", { type: "application/pdf" });

    await act(async () => {
      result.current.mutate(file);
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockResult);
    expect(mockUploadDoc).toHaveBeenCalledWith("i-1", file);
  });

  it("handles upload error", async () => {
    mockUploadDoc.mockRejectedValue(new Error("Upload failed"));

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useUploadDocument("i-1"), { wrapper });

    const file = new File(["content"], "test.pdf", { type: "application/pdf" });

    await act(async () => {
      result.current.mutate(file);
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe("Upload failed");
  });
});
