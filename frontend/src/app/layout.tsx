import type { Metadata } from "next";
import Link from "next/link";
import { ThemeProvider } from "@/components/theme-provider";
import { ThemeToggle } from "@/components/theme-toggle";
import AppShellNav from "@/components/app-shell-nav";
import QueryProvider from "@/providers/QueryProvider";
import AuthProvider from "@/components/auth/AuthProvider";
import { ClientErrorBoundary } from "@/components/error";
import "./globals.css";

export const metadata: Metadata = {
  title: "Bio-Rad CS 대응 허브",
  description: "RAG 기반 멀티에이전트 CS 어시스턴트"
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko" suppressHydrationWarning>
      <body>
        <QueryProvider>
        <AuthProvider>
        <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
          <ClientErrorBoundary>
          <a
            href="#main-content"
            className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-[100] focus:rounded-md focus:bg-primary focus:px-4 focus:py-2 focus:text-primary-foreground focus:text-sm focus:font-medium focus:shadow-lg"
          >
            본문으로 건너뛰기
          </a>
          <header className="sticky top-0 z-50 border-b border-border/50 bg-card/80 backdrop-blur-md">
            <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-3">
              <Link href="/dashboard" className="flex items-center gap-3 transition-opacity hover:opacity-80">
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-blue-500 to-blue-600 shadow-sm">
                  <svg width="18" height="18" viewBox="0 0 18 18" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                    <path d="M3 4.5C3 4.5 5 3 9 3C13 3 15 4.5 15 4.5V14.5C15 14.5 13 13 9 13C5 13 3 14.5 3 14.5V4.5Z" stroke="white" strokeWidth="1.5" strokeLinejoin="round"/>
                    <path d="M9 3V13" stroke="white" strokeWidth="1.5"/>
                    <circle cx="6" cy="7.5" r="1" fill="white"/>
                    <circle cx="12" cy="7.5" r="1" fill="white"/>
                  </svg>
                </div>
                <div>
                  <p className="text-sm font-bold tracking-tight text-foreground">
                    Bio-Rad
                    <span className="font-extrabold text-primary"> CS</span>
                    {" "}대응 허브
                  </p>
                  <p className="text-[0.65rem] text-muted-foreground">RAG 기반 고객문의 대응 워크스페이스</p>
                </div>
              </Link>
              <div className="flex items-center gap-2">
                <AppShellNav />
                <div className="mx-1 h-5 w-px bg-border" aria-hidden="true" />
                <ThemeToggle />
              </div>
            </div>
          </header>

          <main id="main-content" className="mx-auto max-w-7xl px-6 py-8 flex-1 w-full">{children}</main>

          <footer className="border-t border-border/50 bg-slate-900 text-slate-400 dark:bg-slate-950 dark:text-slate-500" role="contentinfo">
            <div className="mx-auto flex max-w-7xl gap-8 px-6 py-8 text-xs">
              <div className="flex-1 space-y-1">
                <div className="mb-3 flex items-center gap-2">
                  <div className="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-gradient-to-br from-blue-500 to-blue-600 text-[0.6rem] font-extrabold text-white">
                    BR
                  </div>
                  <strong className="text-sm text-slate-200 dark:text-slate-300">Bio-Rad CS 대응 허브</strong>
                </div>
                <p>고객 문의 데이터는 내부 검토 및 응대 목적으로만 처리됩니다.</p>
                <p>권한 없는 승인/발송 요청은 자동으로 차단됩니다.</p>
              </div>

              <div className="w-px shrink-0 self-stretch bg-slate-700 dark:bg-slate-800" aria-hidden="true" />

              <div className="space-y-1">
                <strong className="mb-2 block text-slate-200 dark:text-slate-300">지원 정보</strong>
                <p>
                  <span className="text-slate-500 dark:text-slate-600">채널</span>
                  {" "}이메일 / 메신저 / 포털
                </p>
                <p>
                  <span className="text-slate-500 dark:text-slate-600">운영</span>
                  {" "}평일 09:00 ~ 18:00 (KST)
                </p>
                <p>
                  <span className="text-slate-500 dark:text-slate-600">버전</span>
                  {" "}Sprint 14 &middot; v3.0
                </p>
              </div>
            </div>
          </footer>
          </ClientErrorBoundary>
        </ThemeProvider>
        </AuthProvider>
        </QueryProvider>
      </body>
    </html>
  );
}
