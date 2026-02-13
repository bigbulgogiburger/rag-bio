import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: "Bio-Rad CS 코파일럿",
  description: "RAG 기반 멀티에이전트 CS 어시스턴트"
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <main>
          <header className="app-header">
            <h1 className="app-title">Bio-Rad CS 코파일럿</h1>
            <nav className="app-nav">
              <Link className="nav-link" href="/dashboard">대시보드</Link>
              <Link className="nav-link" href="/inquiry/new">문의 작성</Link>
            </nav>
          </header>
          {children}
        </main>
      </body>
    </html>
  );
}
