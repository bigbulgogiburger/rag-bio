'use client';

import { useEffect } from 'react';

interface ToastProps {
  message: string;
  variant: 'success' | 'error' | 'warn' | 'info';
  onClose: () => void;
  duration?: number;
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
      <span>{message}</span>
      <button
        onClick={onClose}
        style={{
          marginLeft: '1rem',
          background: 'transparent',
          border: 'none',
          cursor: 'pointer',
          fontSize: '1.2rem',
          lineHeight: 1,
          color: 'inherit',
          opacity: 0.7,
        }}
        aria-label="닫기"
      >
        ×
      </button>
    </div>
  );
}
