"use client";

import { useEffect, useState } from "react";

export default function OfflineBanner() {
  const [isOffline, setIsOffline] = useState(false);

  useEffect(() => {
    const handleOnline = () => setIsOffline(false);
    const handleOffline = () => setIsOffline(true);

    // Check initial state
    setIsOffline(!navigator.onLine);

    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);

    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, []);

  if (!isOffline) return null;

  return (
    <div
      className="sticky top-0 z-[1200] flex items-center justify-center gap-2 bg-destructive px-4 py-2 text-sm font-medium text-destructive-foreground"
      role="alert"
      aria-live="assertive"
    >
      <svg
        className="h-4 w-4 shrink-0"
        xmlns="http://www.w3.org/2000/svg"
        fill="none"
        viewBox="0 0 24 24"
        strokeWidth={2}
        stroke="currentColor"
        aria-hidden="true"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M3 3l8.735 8.735m0 0a.374.374 0 11.53.53m-.53-.53l.53.53m0 0L21 21M1.5 8.67l1.06 1.06a14.127 14.127 0 012.505-2.012m6.445-.934c1.605.144 3.14.635 4.49 1.43l1.06-1.06a16.132 16.132 0 00-5.55-1.884m-4.9.52A16.09 16.09 0 004.49 8.17M6.343 11.5A10.082 10.082 0 018.5 9.82m3.014-.473a10.112 10.112 0 014.143 1.908M17.657 11.5l-1.06 1.06a7.072 7.072 0 00-2.098-1.276M9.879 14.121A2.985 2.985 0 0112 13.125c.782 0 1.504.3 2.04.796"
        />
      </svg>
      <span>네트워크 연결이 끊겼습니다. 인터넷 연결을 확인해 주세요.</span>
    </div>
  );
}
