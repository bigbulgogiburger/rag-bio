import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useInquiryEvents } from "./useInquiryEvents";

// Mock EventSource
class MockEventSource {
  static instances: MockEventSource[] = [];

  url: string;
  readyState: number;
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onerror: (() => void) | null = null;
  close = vi.fn();

  constructor(url: string) {
    this.url = url;
    this.readyState = 0; // CONNECTING
    MockEventSource.instances.push(this);
  }

  // Helper to simulate open
  simulateOpen() {
    this.readyState = 1; // OPEN
    this.onopen?.();
  }

  // Helper to simulate message
  simulateMessage(data: object) {
    this.onmessage?.({ data: JSON.stringify(data) });
  }

  // Helper to simulate error
  simulateError() {
    this.onerror?.();
  }

  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSED = 2;
}

beforeEach(() => {
  MockEventSource.instances = [];
  vi.useFakeTimers();
  (globalThis as unknown as { EventSource: typeof MockEventSource }).EventSource = MockEventSource as unknown as typeof EventSource;
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe("useInquiryEvents", () => {
  it("connects to SSE endpoint on mount", () => {
    renderHook(() => useInquiryEvents("inquiry-1"));

    expect(MockEventSource.instances).toHaveLength(1);
    expect(MockEventSource.instances[0].url).toContain("/api/v1/inquiries/inquiry-1/events");
  });

  it("returns 'connecting' status initially", () => {
    const { result } = renderHook(() => useInquiryEvents("inquiry-1"));
    expect(result.current.connectionStatus).toBe("connecting");
  });

  it("returns 'connected' after EventSource opens", () => {
    const { result } = renderHook(() => useInquiryEvents("inquiry-1"));

    act(() => {
      MockEventSource.instances[0].simulateOpen();
    });

    expect(result.current.connectionStatus).toBe("connected");
  });

  it("does not connect when enabled=false", () => {
    renderHook(() => useInquiryEvents("inquiry-1", { enabled: false }));
    expect(MockEventSource.instances).toHaveLength(0);
  });

  it("does not connect when inquiryId is empty", () => {
    renderHook(() => useInquiryEvents(""));
    expect(MockEventSource.instances).toHaveLength(0);
  });

  it("calls onEvent for any event", () => {
    const onEvent = vi.fn();
    renderHook(() => useInquiryEvents("inquiry-1", { onEvent }));

    act(() => {
      MockEventSource.instances[0].simulateOpen();
    });

    act(() => {
      MockEventSource.instances[0].simulateMessage({
        type: "CONNECTED",
        inquiryId: "inquiry-1",
        timestamp: "2025-01-01T00:00:00Z",
        data: {},
      });
    });

    expect(onEvent).toHaveBeenCalledTimes(1);
    expect(onEvent).toHaveBeenCalledWith(
      expect.objectContaining({ type: "CONNECTED", inquiryId: "inquiry-1" }),
    );
  });

  it("calls onIndexingProgress for INDEXING_PROGRESS events", () => {
    const onIndexingProgress = vi.fn();
    renderHook(() => useInquiryEvents("inquiry-1", { onIndexingProgress }));

    act(() => {
      MockEventSource.instances[0].simulateOpen();
    });

    const progressData = {
      documentId: "doc-1",
      fileName: "test.pdf",
      status: "INDEXING",
      total: 5,
      indexed: 2,
      failed: 0,
    };

    act(() => {
      MockEventSource.instances[0].simulateMessage({
        type: "INDEXING_PROGRESS",
        inquiryId: "inquiry-1",
        timestamp: "2025-01-01T00:00:00Z",
        data: progressData,
      });
    });

    expect(onIndexingProgress).toHaveBeenCalledWith(progressData);
  });

  it("calls onDraftStep for DRAFT_STEP events", () => {
    const onDraftStep = vi.fn();
    renderHook(() => useInquiryEvents("inquiry-1", { onDraftStep }));

    act(() => {
      MockEventSource.instances[0].simulateOpen();
    });

    const stepData = { step: "RETRIEVE", status: "IN_PROGRESS", message: "검색 중..." };

    act(() => {
      MockEventSource.instances[0].simulateMessage({
        type: "DRAFT_STEP",
        inquiryId: "inquiry-1",
        timestamp: "2025-01-01T00:00:00Z",
        data: stepData,
      });
    });

    expect(onDraftStep).toHaveBeenCalledWith(stepData);
  });

  it("calls onDraftCompleted for DRAFT_COMPLETED events", () => {
    const onDraftCompleted = vi.fn();
    renderHook(() => useInquiryEvents("inquiry-1", { onDraftCompleted }));

    act(() => {
      MockEventSource.instances[0].simulateOpen();
    });

    const completedData = { answerId: "answer-1", version: 1, status: "DRAFT" };

    act(() => {
      MockEventSource.instances[0].simulateMessage({
        type: "DRAFT_COMPLETED",
        inquiryId: "inquiry-1",
        timestamp: "2025-01-01T00:00:00Z",
        data: completedData,
      });
    });

    expect(onDraftCompleted).toHaveBeenCalledWith(completedData);
  });

  it("reconnects with exponential backoff on error", () => {
    renderHook(() => useInquiryEvents("inquiry-1"));
    expect(MockEventSource.instances).toHaveLength(1);

    // Simulate error
    act(() => {
      MockEventSource.instances[0].simulateError();
    });

    // Should be disconnected
    expect(MockEventSource.instances[0].close).toHaveBeenCalled();

    // Advance timer by 1000ms (initial retry)
    act(() => {
      vi.advanceTimersByTime(1000);
    });

    // Should have created a new EventSource
    expect(MockEventSource.instances).toHaveLength(2);
  });

  it("closes EventSource on unmount", () => {
    const { unmount } = renderHook(() => useInquiryEvents("inquiry-1"));
    const es = MockEventSource.instances[0];

    act(() => {
      es.simulateOpen();
    });

    unmount();
    expect(es.close).toHaveBeenCalled();
  });

  it("ignores malformed messages without crashing", () => {
    const onEvent = vi.fn();
    renderHook(() => useInquiryEvents("inquiry-1", { onEvent }));

    act(() => {
      MockEventSource.instances[0].simulateOpen();
    });

    // Send malformed data
    act(() => {
      MockEventSource.instances[0].onmessage?.({ data: "not-json" });
    });

    expect(onEvent).not.toHaveBeenCalled();
  });

  it("calls onConnectionChange callback", () => {
    const onConnectionChange = vi.fn();
    renderHook(() => useInquiryEvents("inquiry-1", { onConnectionChange }));

    // Should have been called with "connecting"
    expect(onConnectionChange).toHaveBeenCalledWith("connecting");

    act(() => {
      MockEventSource.instances[0].simulateOpen();
    });

    expect(onConnectionChange).toHaveBeenCalledWith("connected");
  });
});
