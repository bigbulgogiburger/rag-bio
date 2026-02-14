"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

function itemClass(href: string, pathname: string): string {
  // Exact match for /dashboard and /knowledge-base
  // For /inquiries routes, match prefix but not for /inquiries/new when on /inquiries
  let active = false;
  if (href === "/inquiries/new") {
    active = pathname === "/inquiries/new";
  } else if (href === "/inquiries") {
    active = pathname === "/inquiries";
  } else {
    active = pathname === href;
  }
  return `menu-item${active ? " active" : ""}`;
}

export default function AppShellNav() {
  const pathname = usePathname();

  return (
    <nav className="menu-nav" aria-label="주 메뉴">
      <Link className={itemClass("/dashboard", pathname)} href="/dashboard">대시보드</Link>
      <Link className={itemClass("/inquiries", pathname)} href="/inquiries">문의 목록</Link>
      <Link className={itemClass("/inquiries/new", pathname)} href="/inquiries/new">문의 작성</Link>
      <Link className={itemClass("/knowledge-base", pathname)} href="/knowledge-base">지식 기반</Link>
    </nav>
  );
}
