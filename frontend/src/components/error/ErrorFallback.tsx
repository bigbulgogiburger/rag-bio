"use client";

import { Button } from "@/components/ui/button";

interface ErrorFallbackProps {
  error: Error;
  resetErrorBoundary: () => void;
  level: "app" | "page" | "tab";
}

const levelConfig = {
  app: {
    title: "애플리케이션 오류가 발생했습니다",
    description: "예기치 않은 오류가 발생했습니다. 페이지를 새로고침 해주세요.",
    containerClass: "flex min-h-[60vh] flex-col items-center justify-center p-8",
  },
  page: {
    title: "페이지를 불러올 수 없습니다",
    description: "이 페이지에서 오류가 발생했습니다. 다시 시도해 주세요.",
    containerClass: "flex min-h-[40vh] flex-col items-center justify-center p-8",
  },
  tab: {
    title: "콘텐츠를 표시할 수 없습니다",
    description: "이 탭에서 오류가 발생했습니다. 다시 시도해 주세요.",
    containerClass: "flex min-h-[200px] flex-col items-center justify-center p-6",
  },
};

export default function ErrorFallback({ error, resetErrorBoundary, level }: ErrorFallbackProps) {
  const config = levelConfig[level];

  return (
    <div className={config.containerClass} role="alert">
      <div className="mx-auto max-w-md text-center space-y-4">
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-destructive/10">
          <svg
            className="h-6 w-6 text-destructive"
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={1.5}
            stroke="currentColor"
            aria-hidden="true"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z"
            />
          </svg>
        </div>

        <h2 className="text-lg font-semibold text-foreground">{config.title}</h2>
        <p className="text-sm text-muted-foreground">{config.description}</p>

        {process.env.NODE_ENV === "development" && (
          <details className="mt-4 rounded-lg border border-destructive/20 bg-destructive/5 p-3 text-left">
            <summary className="cursor-pointer text-xs font-medium text-destructive">
              오류 상세 (개발 모드)
            </summary>
            <pre className="mt-2 overflow-auto text-xs text-destructive/80 whitespace-pre-wrap break-all">
              {error.message}
              {error.stack && `\n\n${error.stack}`}
            </pre>
          </details>
        )}

        <div className="flex items-center justify-center gap-3 pt-2">
          <Button onClick={resetErrorBoundary} size="sm">
            다시 시도
          </Button>
          {level === "app" && (
            <Button variant="outline" size="sm" onClick={() => window.location.reload()}>
              페이지 새로고침
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
