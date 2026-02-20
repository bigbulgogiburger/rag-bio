import React, { type ReactElement, type ReactNode } from "react";
import { render, type RenderOptions } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import userEvent from "@testing-library/user-event";

/**
 * Creates a fresh QueryClient configured for testing:
 * - No retries (fail fast)
 * - No garbage collection delay
 */
function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
        staleTime: 0,
      },
      mutations: {
        retry: false,
      },
    },
  });
}

interface WrapperProps {
  children: ReactNode;
}

/**
 * Renders a component wrapped in all necessary providers for testing.
 * Returns the render result plus a pre-configured userEvent instance.
 */
export function renderWithProviders(
  ui: ReactElement,
  options?: Omit<RenderOptions, "wrapper"> & { queryClient?: QueryClient },
) {
  const queryClient = options?.queryClient ?? createTestQueryClient();

  function Wrapper({ children }: WrapperProps) {
    return (
      <QueryClientProvider client={queryClient}>
        {children}
      </QueryClientProvider>
    );
  }

  const renderResult = render(ui, { wrapper: Wrapper, ...options });
  const user = userEvent.setup();

  return {
    ...renderResult,
    user,
    queryClient,
  };
}

/**
 * Mock API client that returns resolved values.
 * Usage:
 *   const mock = mockApiClient({ createInquiry: { inquiryId: "123", status: "RECEIVED", message: "ok" } });
 *   vi.mock("@/lib/api/client", () => mock);
 */
export function mockApiClient(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  const defaults: Record<string, unknown> = {
    createInquiry: { inquiryId: "test-id", status: "RECEIVED", message: "Created" },
    getInquiry: {
      inquiryId: "test-id",
      question: "테스트 질문",
      customerChannel: "email",
      status: "RECEIVED",
      createdAt: "2025-01-15T10:00:00Z",
    },
    listInquiries: {
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    },
    uploadInquiryDocument: {
      documentId: "doc-1",
      inquiryId: "test-id",
      fileName: "test.pdf",
      status: "UPLOADED",
    },
    listInquiryDocuments: [],
    getInquiryIndexingStatus: {
      inquiryId: "test-id",
      total: 0,
      uploaded: 0,
      parsing: 0,
      parsed: 0,
      chunked: 0,
      indexed: 0,
      failed: 0,
      documents: [],
    },
    analyzeInquiry: {
      inquiryId: "test-id",
      verdict: "SUPPORTED",
      confidence: 0.85,
      reason: "Test reason",
      riskFlags: [],
      evidences: [],
    },
    draftInquiryAnswer: {
      answerId: "answer-1",
      inquiryId: "test-id",
      version: 1,
      status: "DRAFT",
      verdict: "SUPPORTED",
      confidence: 0.85,
      draft: "Test draft answer",
      citations: [],
      riskFlags: [],
      tone: "professional",
      channel: "email",
      reviewedBy: null,
      reviewComment: null,
      approvedBy: null,
      approveComment: null,
      sentBy: null,
      sendChannel: null,
      sendMessageId: null,
      formatWarnings: [],
    },
    getOpsMetrics: {
      approvedOrSentCount: 10,
      sentCount: 8,
      sendSuccessRate: 0.8,
      duplicateBlockedCount: 1,
      totalSendAttemptCount: 10,
      duplicateBlockRate: 0.1,
      fallbackDraftCount: 2,
      totalDraftCount: 15,
      fallbackDraftRate: 0.13,
      topFailureReasons: [],
    },
    listKbDocuments: {
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    },
    getKbStats: {
      totalDocuments: 0,
      indexedDocuments: 0,
      totalChunks: 0,
      byCategory: {},
      byProductFamily: {},
    },
  };

  const merged = { ...defaults, ...overrides };

  const result: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(merged)) {
    if (typeof value === "function") {
      result[key] = value;
    } else {
      result[key] = async () => value;
    }
  }

  return result;
}

// Re-export everything from @testing-library/react
export { screen, waitFor, within, fireEvent, act } from "@testing-library/react";
export { default as userEvent } from "@testing-library/user-event";
