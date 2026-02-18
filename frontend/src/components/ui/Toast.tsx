'use client';

import { useEffect } from 'react';
import { cn } from '@/lib/utils';

interface ToastProps {
  message: string;
  variant: 'success' | 'error' | 'warn' | 'info';
  onClose: () => void;
  duration?: number;
}

const variantClasses: Record<string, string> = {
  success: 'bg-success-light text-success-foreground border-success-border',
  error: 'bg-danger-light text-danger-foreground border-danger-border',
  warn: 'bg-warning-light text-warning-foreground border-warning-border',
  info: 'bg-info-light text-info-foreground border-info-border',
};

function StatusIcon({ variant }: { variant: ToastProps['variant'] }) {
  const size = 16;
  const common = { width: size, height: size, viewBox: '0 0 16 16', fill: 'none', 'aria-hidden': true as const, className: 'shrink-0' };

  switch (variant) {
    case 'success':
      return (
        <svg {...common}>
          <circle cx="8" cy="8" r="7" stroke="currentColor" strokeWidth="1.5" />
          <path d="M5 8l2 2 4-4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    case 'error':
      return (
        <svg {...common}>
          <circle cx="8" cy="8" r="7" stroke="currentColor" strokeWidth="1.5" />
          <path d="M5.5 5.5l5 5M10.5 5.5l-5 5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
        </svg>
      );
    case 'warn':
      return (
        <svg {...common}>
          <path d="M8 2L1.5 14h13L8 2z" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
          <path d="M8 6.5v3" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          <circle cx="8" cy="11.5" r="0.75" fill="currentColor" />
        </svg>
      );
    case 'info':
      return (
        <svg {...common}>
          <circle cx="8" cy="8" r="7" stroke="currentColor" strokeWidth="1.5" />
          <path d="M8 7v4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          <circle cx="8" cy="5" r="0.75" fill="currentColor" />
        </svg>
      );
  }
}

/**
 * Toast 컴포넌트
 *
 * 사용 예:
 * {showToast && (
 *   <Toast
 *     message="문의가 성공적으로 등록되었습니다"
 *     variant="success"
 *     onClose={() => setShowToast(false)}
 *     duration={3000}
 *   />
 * )}
 */
export default function Toast({
  message,
  variant,
  onClose,
  duration = 3000,
}: ToastProps) {
  useEffect(() => {
    if (duration > 0) {
      const timer = setTimeout(onClose, duration);
      return () => clearTimeout(timer);
    }
  }, [duration, onClose]);

  return (
    <div
      className={cn(
        'fixed top-6 right-6 z-[1100] flex max-w-[420px] items-center gap-2 rounded-lg border px-5 py-3 text-sm font-medium shadow-lg animate-toast-in',
        variantClasses[variant]
      )}
      role="alert"
      aria-live="assertive"
      aria-atomic="true"
    >
      <StatusIcon variant={variant} />
      <span className="flex-1">{message}</span>
      <button
        onClick={onClose}
        className="ml-2 inline-flex items-center justify-center rounded p-0.5 opacity-60 transition-opacity hover:opacity-100"
        aria-label="닫기"
      >
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
          <path d="M3.5 3.5l7 7M10.5 3.5l-7 7" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
        </svg>
      </button>
    </div>
  );
}
