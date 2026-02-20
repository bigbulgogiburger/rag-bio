import { describe, it, expect, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useKbDocuments, useKbStats, kbKeys } from "../useKnowledgeBase";

const mockListKbDocuments = vi.fn();
const mockGetKbStats = vi.fn();

vi.mock("@/lib/api/client", () => ({
  listKbDocuments: (...args: unknown[]) => mockListKbDocuments(...args),
  getKbDocument: vi.fn(),
  getKbStats: () => mockGetKbStats(),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe("useKbDocuments", () => {
  it("fetches KB document list", async () => {
    const mockData = {
      content: [{ documentId: "kb-1", title: "Manual" }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };
    mockListKbDocuments.mockResolvedValue(mockData);

    const { result } = renderHook(() => useKbDocuments({ page: 0 }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockData);
  });
});

describe("useKbStats", () => {
  it("fetches KB statistics", async () => {
    const mockStats = {
      totalDocuments: 10,
      indexedDocuments: 8,
      totalChunks: 200,
      byCategory: { MANUAL: 5 },
      byProductFamily: {},
    };
    mockGetKbStats.mockResolvedValue(mockStats);

    const { result } = renderHook(() => useKbStats(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockStats);
  });
});

describe("kbKeys", () => {
  it("generates correct key structure", () => {
    expect(kbKeys.all).toEqual(["knowledgeBase"]);
    expect(kbKeys.stats()).toEqual(["knowledgeBase", "stats"]);
    expect(kbKeys.detail("doc-1")).toEqual(["knowledgeBase", "detail", "doc-1"]);
  });
});
