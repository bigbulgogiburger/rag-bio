"use client";

import type { ReactNode } from "react";
import AppErrorBoundary from "./AppErrorBoundary";
import OfflineBanner from "./OfflineBanner";
import { Toaster } from "@/components/ui/sonner";

interface ClientErrorBoundaryProps {
  children: ReactNode;
}

/**
 * 클라이언트 사이드 에러 처리 래퍼
 * - AppErrorBoundary: 최상위 에러 바운더리
 * - OfflineBanner: 네트워크 오프라인 감지 배너
 * - Toaster: 전역 토스트 (sonner)
 */
export default function ClientErrorBoundary({ children }: ClientErrorBoundaryProps) {
  return (
    <AppErrorBoundary>
      <OfflineBanner />
      {children}
      <Toaster />
    </AppErrorBoundary>
  );
}
