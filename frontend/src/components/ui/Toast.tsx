'use client';

import { useEffect } from 'react';

interface ToastProps {
  message: string;
  variant: 'success' | 'error' | 'warn' | 'info';
  onClose: () => void;
  duration?: number;
}

function StatusIcon({ variant }: { variant: ToastProps['variant'] }) {
  const size = 16;
  const common = { width: size, height: size, viewBox: '0 0 16 16', fill: 'none', 'aria-hidden': true as const, style: { flexShrink: 0 } as React.CSSProperties };

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
      className={`toast toast-${variant}`}
      role="alert"
      aria-live="assertive"
      aria-atomic="true"
    >
      <StatusIcon variant={variant} />
      <span style={{ flex: 1 }}>{message}</span>
      <button
        onClick={onClose}
        style={{
          marginLeft: 'var(--space-sm)',
          background: 'transparent',
          border: 'none',
          cursor: 'pointer',
          padding: '2px',
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          borderRadius: 'var(--radius-xs)',
          color: 'inherit',
          opacity: 0.6,
          transition: 'opacity var(--transition-fast)',
        }}
        onMouseEnter={(e) => { e.currentTarget.style.opacity = '1'; }}
        onMouseLeave={(e) => { e.currentTarget.style.opacity = '0.6'; }}
        aria-label="닫기"
      >
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
          <path d="M3.5 3.5l7 7M10.5 3.5l-7 7" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
        </svg>
      </button>
    </div>
  );
}
