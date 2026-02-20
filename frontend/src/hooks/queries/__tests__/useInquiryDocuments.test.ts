import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useInquiryDocuments, useInquiryIndexingStatus, inquiryDocumentsKeys } from "../useInquiryDocuments";

const mockListDocs = vi.fn();
const mockGetIndexingStatus = vi.fn();

vi.mock("@/lib/api/client", () => ({
  listInquiryDocuments: (...args: unknown[]) => mockListDocs(...args),
  getInquiryIndexingStatus: (...args: unknown[]) => mockGetIndexingStatus(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe("useInquiryDocuments", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches documents for an inquiry", async () => {
    const mockDocs = [
      { documentId: "d-1", fileName: "test.pdf", status: "INDEXED", fileSize: 1024 },
    ];
    mockListDocs.mockResolvedValue(mockDocs);

    const { result } = renderHook(() => useInquiryDocuments("i-1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockDocs);
    expect(mockListDocs).toHaveBeenCalledWith("i-1");
  });

  it("does not fetch when inquiryId is empty", () => {
    const { result } = renderHook(() => useInquiryDocuments(""), {
      wrapper: createWrapper(),
    });

    expect(result.current.fetchStatus).toBe("idle");
    expect(mockListDocs).not.toHaveBeenCalled();
  });
});

describe("useInquiryIndexingStatus", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches indexing status", async () => {
    const mockStatus = { total: 3, indexed: 2, failed: 0, documents: [] };
    mockGetIndexingStatus.mockResolvedValue(mockStatus);

    const { result } = renderHook(() => useInquiryIndexingStatus("i-1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockStatus);
  });

  it("does not fetch when disabled", () => {
    const { result } = renderHook(
      () => useInquiryIndexingStatus("i-1", { enabled: false }),
      { wrapper: createWrapper() },
    );

    expect(result.current.fetchStatus).toBe("idle");
  });
});

describe("inquiryDocumentsKeys", () => {
  it("generates correct key structure", () => {
    expect(inquiryDocumentsKeys.all).toEqual(["inquiryDocuments"]);
    expect(inquiryDocumentsKeys.list("i-1")).toEqual(["inquiryDocuments", "list", "i-1"]);
    expect(inquiryDocumentsKeys.indexingStatus("i-1")).toEqual(["inquiryDocuments", "indexingStatus", "i-1"]);
  });
});
