"use client";

import { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

const baseClass =
  "flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground";
const activeClass = "bg-primary/10 text-primary font-semibold";

function isActive(href: string, pathname: string): boolean {
  if (href === "/inquiries/new") return pathname === "/inquiries/new";
  if (href === "/inquiries") return pathname === "/inquiries";
  return pathname === href;
}

function itemClass(href: string, pathname: string): string {
  return cn(baseClass, isActive(href, pathname) && activeClass);
}

/* -- Inline SVG Icons (16x16) -- */

function DashboardIcon({ className }: { className?: string }) {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" className={className}>
      <rect x="1" y="1" width="6" height="6" rx="1.5" stroke="currentColor" strokeWidth="1.5" />
      <rect x="9" y="1" width="6" height="6" rx="1.5" stroke="currentColor" strokeWidth="1.5" />
      <rect x="1" y="9" width="6" height="6" rx="1.5" stroke="currentColor" strokeWidth="1.5" />
      <rect x="9" y="9" width="6" height="6" rx="1.5" stroke="currentColor" strokeWidth="1.5" />
    </svg>
  );
}

function ListIcon({ className }: { className?: string }) {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" className={className}>
      <path d="M5.5 3.5H14" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      <path d="M5.5 8H14" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      <path d="M5.5 12.5H14" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      <circle cx="2.5" cy="3.5" r="1" fill="currentColor" />
      <circle cx="2.5" cy="8" r="1" fill="currentColor" />
      <circle cx="2.5" cy="12.5" r="1" fill="currentColor" />
    </svg>
  );
}

function PencilPlusIcon({ className }: { className?: string }) {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" className={className}>
      <path d="M11.5 1.5L14.5 4.5L5.5 13.5H2.5V10.5L11.5 1.5Z" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M9.5 3.5L12.5 6.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}

function BookIcon({ className }: { className?: string }) {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" className={className}>
      <path d="M2 2.5C2 2.5 3.5 1.5 5.5 1.5C7.5 1.5 8 2.5 8 2.5V13.5C8 13.5 7.5 12.5 5.5 12.5C3.5 12.5 2 13.5 2 13.5V2.5Z" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M14 2.5C14 2.5 12.5 1.5 10.5 1.5C8.5 1.5 8 2.5 8 2.5V13.5C8 13.5 8.5 12.5 10.5 12.5C12.5 12.5 14 13.5 14 13.5V2.5Z" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
    </svg>
  );
}

const navItems = [
  { href: "/dashboard", label: "대시보드", shortLabel: "대시보드", Icon: DashboardIcon },
  { href: "/inquiries", label: "문의 목록", shortLabel: "문의목록", Icon: ListIcon },
  { href: "/inquiries/new", label: "문의 작성", shortLabel: "문의작성", Icon: PencilPlusIcon },
  { href: "/knowledge-base", label: "지식 기반", shortLabel: "지식기반", Icon: BookIcon },
] as const;

export default function AppShellNav() {
  const pathname = usePathname();

  return (
    <>
      {/* Desktop nav — rendered inside header */}
      <nav className="hidden md:flex items-center gap-1" aria-label="주 메뉴">
        {navItems.map(({ href, label, Icon }) => (
          <Link key={href} className={itemClass(href, pathname)} href={href}>
            <Icon />
            {label}
          </Link>
        ))}
      </nav>
    </>
  );
}

/** Mobile bottom navigation bar — rendered via layout.tsx */
export function MobileBottomNav() {
  const pathname = usePathname();

  return (
    <nav
      className="fixed bottom-0 left-0 right-0 z-50 flex border-t border-border/50 bg-card/95 backdrop-blur-md md:hidden"
      style={{ paddingBottom: "env(safe-area-inset-bottom)" }}
      aria-label="모바일 하단 메뉴"
    >
      {navItems.map(({ href, shortLabel, Icon }) => (
        <Link
          key={href}
          href={href}
          className={cn(
            "flex flex-1 flex-col items-center gap-1 px-2 py-3 text-[0.65rem] font-medium transition-colors",
            isActive(href, pathname) ? "text-primary" : "text-muted-foreground"
          )}
        >
          <Icon className="h-5 w-5" />
          <span>{shortLabel}</span>
        </Link>
      ))}
    </nav>
  );
}
