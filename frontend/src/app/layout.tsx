import type { Metadata } from "next";
import Link from "next/link";
import AppShellNav from "@/components/app-shell-nav";
import "./globals.css";

export const metadata: Metadata = {
  title: "Bio-Rad CS 코파일럿",
  description: "RAG 기반 멀티에이전트 CS 어시스턴트"
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <header className="topbar">
          <div className="topbar-inner">
            <div className="brand-wrap">
              <div className="brand-badge">BR</div>
              <div>
                <p className="brand-title">Bio-Rad CS 코파일럿</p>
                <p className="brand-subtitle">고객문의 자동화 워크스페이스</p>
              </div>
            </div>
            <AppShellNav />
          </div>
        </header>

        <main>{children}</main>

        <footer className="app-footer">
          <div className="footer-inner">
            <div>
              <strong>Bio-Rad CS 코파일럿</strong>
              <p>문의 접수 · 분석 · 답변 생성/승인/발송을 한 화면에서 관리합니다.</p>
            </div>
            <div className="footer-links">
              <Link href="/dashboard">대시보드</Link>
              <Link href="/inquiry/new">문의 작성</Link>
            </div>
          </div>
        </footer>
      </body>
    </html>
  );
}
