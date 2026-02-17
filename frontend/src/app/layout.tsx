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
              <div className="brand-badge">
                <svg width="18" height="18" viewBox="0 0 18 18" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <path d="M3 4.5C3 4.5 5 3 9 3C13 3 15 4.5 15 4.5V14.5C15 14.5 13 13 9 13C5 13 3 14.5 3 14.5V4.5Z" stroke="white" strokeWidth="1.5" strokeLinejoin="round"/>
                  <path d="M9 3V13" stroke="white" strokeWidth="1.5"/>
                  <circle cx="6" cy="7.5" r="1" fill="white"/>
                  <circle cx="12" cy="7.5" r="1" fill="white"/>
                </svg>
              </div>
              <div>
                <p className="brand-title">
                  Bio-Rad
                  <span style={{ color: "var(--color-primary)", fontWeight: 800 }}> CS</span>
                  {" "}대응 허브
                </p>
                <p className="brand-subtitle">RAG 기반 고객문의 대응 워크스페이스</p>
              </div>
            </div>
            <AppShellNav />
          </div>
        </header>

        <main>{children}</main>

        <footer className="app-footer">
          <div className="footer-inner">
            <div className="footer-brand">
              <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", marginBottom: "0.75rem" }}>
                <div style={{
                  width: "24px",
                  height: "24px",
                  borderRadius: "6px",
                  background: "linear-gradient(135deg, #3b82f6, #2563eb)",
                  display: "inline-flex",
                  alignItems: "center",
                  justifyContent: "center",
                  fontSize: "0.6rem",
                  fontWeight: 800,
                  color: "#fff",
                  flexShrink: 0,
                }}>
                  BR
                </div>
                <strong style={{ color: "#e2e8f0", fontSize: "0.875rem" }}>Bio-Rad CS 대응 허브</strong>
              </div>
              <p>고객 문의 데이터는 내부 검토 및 응대 목적으로만 처리됩니다.</p>
              <p>권한 없는 승인/발송 요청은 자동으로 차단됩니다.</p>
            </div>

            <div style={{ width: "1px", background: "#334155", alignSelf: "stretch", flexShrink: 0 }} />

            <div className="footer-meta">
              <strong style={{ color: "#e2e8f0", display: "block", marginBottom: "0.5rem" }}>지원 정보</strong>
              <p>
                <span style={{ color: "#64748b" }}>채널</span>
                {" "}이메일 / 메신저 / 포털
              </p>
              <p>
                <span style={{ color: "#64748b" }}>운영</span>
                {" "}평일 09:00 ~ 18:00 (KST)
              </p>
              <p>
                <span style={{ color: "#64748b" }}>버전</span>
                {" "}Sprint 9 &middot; v2.0
              </p>
            </div>
          </div>
        </footer>
      </body>
    </html>
  );
}
