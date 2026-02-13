import type { Metadata } from "next";
import AppShellNav from "@/components/app-shell-nav";
import "./globals.css";

export const metadata: Metadata = {
  title: "Bio-Rad CS 대응 허브",
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
                <p className="brand-title">Bio-Rad CS 대응 허브</p>
                <p className="brand-subtitle">고객문의 대응 워크스페이스</p>
              </div>
            </div>
            <AppShellNav />
          </div>
        </header>

        <main>{children}</main>

        <footer className="app-footer">
          <div className="footer-inner">
            <div>
              <strong>운영 안내</strong>
              <p>고객 문의 데이터는 내부 검토/응대 목적으로 처리됩니다.</p>
              <p>권한 없는 승인·발송 요청은 자동으로 차단됩니다.</p>
            </div>
            <div className="footer-meta">
              <p><b>지원 채널</b> 이메일 · 메신저 · 포털</p>
              <p><b>운영 시간</b> 평일 09:00 ~ 18:00 (KST)</p>
              <p><b>버전</b> Sprint5 UI Rev</p>
            </div>
          </div>
        </footer>
      </body>
    </html>
  );
}
