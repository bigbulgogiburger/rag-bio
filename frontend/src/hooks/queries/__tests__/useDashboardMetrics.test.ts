import { describe, it, expect, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useDashboardMetrics, dashboardKeys } from "../useDashboardMetrics";

const mockGetOpsMetrics = vi.fn();

vi.mock("@/lib/api/client", () => ({
  getOpsMetrics: () => mockGetOpsMetrics(),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe("useDashboardMetrics", () => {
  it("fetches ops metrics", async () => {
    const mockMetrics = {
      approvedOrSentCount: 10,
      sentCount: 8,
      sendSuccessRate: 80,
      duplicateBlockedCount: 1,
      totalSendAttemptCount: 10,
      duplicateBlockRate: 10,
      fallbackDraftCount: 2,
      totalDraftCount: 15,
      fallbackDraftRate: 13,
      topFailureReasons: [],
    };
    mockGetOpsMetrics.mockResolvedValue(mockMetrics);

    const { result } = renderHook(() => useDashboardMetrics(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockMetrics);
  });
});

describe("dashboardKeys", () => {
  it("generates correct key structure", () => {
    expect(dashboardKeys.all).toEqual(["dashboard"]);
    expect(dashboardKeys.metrics()).toEqual(["dashboard", "metrics"]);
  });
});
