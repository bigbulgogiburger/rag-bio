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

/* ── Inline SVG Icons (16x16) ── */

function DashboardIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <rect x="1" y="1" width="6" height="6" rx="1.5" stroke="currentColor" strokeWidth="1.5" />
      <rect x="9" y="1" width="6" height="6" rx="1.5" stroke="currentColor" strokeWidth="1.5" />
      <rect x="1" y="9" width="6" height="6" rx="1.5" stroke="currentColor" strokeWidth="1.5" />
      <rect x="9" y="9" width="6" height="6" rx="1.5" stroke="currentColor" strokeWidth="1.5" />
    </svg>
  );
}

function ListIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <path d="M5.5 3.5H14" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      <path d="M5.5 8H14" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      <path d="M5.5 12.5H14" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      <circle cx="2.5" cy="3.5" r="1" fill="currentColor" />
      <circle cx="2.5" cy="8" r="1" fill="currentColor" />
      <circle cx="2.5" cy="12.5" r="1" fill="currentColor" />
    </svg>
  );
}

function PencilPlusIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <path d="M11.5 1.5L14.5 4.5L5.5 13.5H2.5V10.5L11.5 1.5Z" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M9.5 3.5L12.5 6.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}

function BookIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <path d="M2 2.5C2 2.5 3.5 1.5 5.5 1.5C7.5 1.5 8 2.5 8 2.5V13.5C8 13.5 7.5 12.5 5.5 12.5C3.5 12.5 2 13.5 2 13.5V2.5Z" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M14 2.5C14 2.5 12.5 1.5 10.5 1.5C8.5 1.5 8 2.5 8 2.5V13.5C8 13.5 8.5 12.5 10.5 12.5C12.5 12.5 14 13.5 14 13.5V2.5Z" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
    </svg>
  );
}

export default function AppShellNav() {
  const pathname = usePathname();

  return (
    <nav className="menu-nav" aria-label="주 메뉴">
      <Link className={itemClass("/dashboard", pathname)} href="/dashboard">
        <DashboardIcon />
        대시보드
      </Link>
      <Link className={itemClass("/inquiries", pathname)} href="/inquiries">
        <ListIcon />
        문의 목록
      </Link>
      <Link className={itemClass("/inquiries/new", pathname)} href="/inquiries/new">
        <PencilPlusIcon />
        문의 작성
      </Link>
      <Link className={itemClass("/knowledge-base", pathname)} href="/knowledge-base">
        <BookIcon />
        지식 기반
      </Link>
    </nav>
  );
}
