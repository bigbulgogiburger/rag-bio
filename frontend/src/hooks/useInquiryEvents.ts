"use client";

import { useEffect, useRef, useCallback, useState } from "react";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";

// ===== SSE 이벤트 타입 =====

export type InquiryEventType =
  | "INDEXING_STARTED"
  | "INDEXING_PROGRESS"
  | "INDEXING_COMPLETED"
  | "INDEXING_FAILED"
  | "DRAFT_STEP"
  | "DRAFT_COMPLETED"
  | "DRAFT_FAILED"
  | "STATUS_CHANGED"
  | "CONNECTED";

export interface IndexingProgressData {
  documentId: string;
  fileName: string;
  status: string;
  total: number;
  indexed: number;
  failed: number;
}

export interface DraftStepData {
  step: "RETRIEVE" | "VERIFY" | "COMPOSE" | "SELF_REVIEW";
  status: "IN_PROGRESS" | "COMPLETED" | "FAILED" | "RETRY";
  message?: string;
}

export interface DraftCompletedData {
  answerId: string;
  version: number;
  status: string;
}

export interface StatusChangedData {
  previousStatus: string;
  newStatus: string;
}

export interface InquiryEvent {
  type: InquiryEventType;
  inquiryId: string;
  timestamp: string;
  data: IndexingProgressData | DraftStepData | DraftCompletedData | StatusChangedData | Record<string, unknown>;
}

// ===== 연결 상태 =====

export type ConnectionStatus = "connecting" | "connected" | "disconnected" | "error";

// ===== 훅 옵션 =====

interface UseInquiryEventsOptions {
  /** SSE 이벤트 핸들러 */
  onEvent?: (event: InquiryEvent) => void;
  /** 인덱싱 진행 이벤트 전용 핸들러 */
  onIndexingProgress?: (data: IndexingProgressData) => void;
  /** 답변 생성 단계 이벤트 전용 핸들러 */
  onDraftStep?: (data: DraftStepData) => void;
  /** 답변 생성 완료 이벤트 전용 핸들러 */
  onDraftCompleted?: (data: DraftCompletedData) => void;
  /** 연결 상태 변경 핸들러 */
  onConnectionChange?: (status: ConnectionStatus) => void;
  /** SSE 활성화 여부 (기본: true) */
  enabled?: boolean;
}

// ===== 재연결 설정 =====

const INITIAL_RETRY_MS = 1000;
const MAX_RETRY_MS = 30000;
const RETRY_BACKOFF_FACTOR = 2;

/**
 * 문의별 SSE 이벤트 구독 훅
 *
 * SSE 엔드포인트: GET /api/v1/inquiries/{inquiryId}/events
 *
 * 사용 예:
 * ```tsx
 * const { connectionStatus } = useInquiryEvents(inquiryId, {
 *   onIndexingProgress: (data) => setProgress(data),
 *   onDraftStep: (data) => setCurrentStep(data.step),
 * });
 * ```
 */
export function useInquiryEvents(
  inquiryId: string,
  options: UseInquiryEventsOptions = {},
) {
  const {
    onEvent,
    onIndexingProgress,
    onDraftStep,
    onDraftCompleted,
    onConnectionChange,
    enabled = true,
  } = options;

  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>("disconnected");
  const eventSourceRef = useRef<EventSource | null>(null);
  const retryTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const retryDelayRef = useRef(INITIAL_RETRY_MS);

  // Stable callback refs
  const onEventRef = useRef(onEvent);
  const onIndexingProgressRef = useRef(onIndexingProgress);
  const onDraftStepRef = useRef(onDraftStep);
  const onDraftCompletedRef = useRef(onDraftCompleted);
  const onConnectionChangeRef = useRef(onConnectionChange);

  useEffect(() => { onEventRef.current = onEvent; }, [onEvent]);
  useEffect(() => { onIndexingProgressRef.current = onIndexingProgress; }, [onIndexingProgress]);
  useEffect(() => { onDraftStepRef.current = onDraftStep; }, [onDraftStep]);
  useEffect(() => { onDraftCompletedRef.current = onDraftCompleted; }, [onDraftCompleted]);
  useEffect(() => { onConnectionChangeRef.current = onConnectionChange; }, [onConnectionChange]);

  const updateStatus = useCallback((status: ConnectionStatus) => {
    setConnectionStatus(status);
    onConnectionChangeRef.current?.(status);
  }, []);

  const handleMessage = useCallback((messageEvent: MessageEvent) => {
    try {
      const event: InquiryEvent = JSON.parse(messageEvent.data);
      onEventRef.current?.(event);

      switch (event.type) {
        case "INDEXING_PROGRESS":
        case "INDEXING_STARTED":
        case "INDEXING_COMPLETED":
        case "INDEXING_FAILED":
          onIndexingProgressRef.current?.(event.data as IndexingProgressData);
          break;
        case "DRAFT_STEP":
          onDraftStepRef.current?.(event.data as DraftStepData);
          break;
        case "DRAFT_COMPLETED":
        case "DRAFT_FAILED":
          onDraftCompletedRef.current?.(event.data as DraftCompletedData);
          break;
        case "CONNECTED":
          // Initial connection confirmation
          break;
      }
    } catch {
      // Ignore malformed events
    }
  }, []);

  const connect = useCallback(() => {
    if (!enabled || !inquiryId) return;

    // Clean up existing connection
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }

    updateStatus("connecting");

    const url = `${API_BASE_URL}/api/v1/inquiries/${inquiryId}/events`;
    const es = new EventSource(url);
    eventSourceRef.current = es;

    es.onopen = () => {
      updateStatus("connected");
      retryDelayRef.current = INITIAL_RETRY_MS; // Reset retry delay on success
    };

    es.onmessage = handleMessage;

    es.onerror = () => {
      es.close();
      eventSourceRef.current = null;
      updateStatus("disconnected");

      // Schedule reconnection with exponential backoff
      const delay = retryDelayRef.current;
      retryDelayRef.current = Math.min(delay * RETRY_BACKOFF_FACTOR, MAX_RETRY_MS);

      retryTimeoutRef.current = setTimeout(() => {
        connect();
      }, delay);
    };
  }, [inquiryId, enabled, updateStatus, handleMessage]);

  // Connect/disconnect lifecycle
  useEffect(() => {
    if (enabled) {
      connect();
    }

    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
      if (retryTimeoutRef.current) {
        clearTimeout(retryTimeoutRef.current);
        retryTimeoutRef.current = null;
      }
    };
  }, [connect, enabled]);

  // Reconnect on tab visibility change
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible" && enabled) {
        // Re-establish connection when tab becomes visible
        if (!eventSourceRef.current || eventSourceRef.current.readyState === EventSource.CLOSED) {
          retryDelayRef.current = INITIAL_RETRY_MS;
          connect();
        }
      }
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => document.removeEventListener("visibilitychange", handleVisibilityChange);
  }, [connect, enabled]);

  return { connectionStatus };
}
