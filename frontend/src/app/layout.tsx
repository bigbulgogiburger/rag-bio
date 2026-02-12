import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: "Bio-Rad CS Copilot",
  description: "RAG-based multi-agent CS assistant"
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <main>
          <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1.5rem" }}>
            <h1 style={{ margin: 0, fontSize: "1.4rem" }}>Bio-Rad CS Copilot</h1>
            <nav style={{ display: "flex", gap: "1rem", color: "#0f766e" }}>
              <Link href="/dashboard">Dashboard</Link>
              <Link href="/inquiry/new">New Inquiry</Link>
            </nav>
          </header>
          {children}
        </main>
      </body>
    </html>
  );
}
