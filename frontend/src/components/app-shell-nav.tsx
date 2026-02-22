"use client";

import { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

const baseClass =
  "flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground";
const activeClass = "bg-primary/10 text-primary font-semibold";

const mobileBaseClass =
  "flex items-center gap-3 rounded-lg px-4 py-3 text-base font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground";
const mobileActiveClass = "bg-primary/10 text-primary font-semibold";

function isActive(href: string, pathname: string): boolean {
  if (href === "/inquiries/new") return pathname === "/inquiries/new";
  if (href === "/inquiries") return pathname === "/inquiries";
  return pathname === href;
}

function itemClass(href: string, pathname: string): string {
  return cn(baseClass, isActive(href, pathname) && activeClass);
}

function mobileItemClass(href: string, pathname: string): string {
  return cn(mobileBaseClass, isActive(href, pathname) && mobileActiveClass);
}

/* -- Inline SVG Icons (16x16) -- */

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

const navItems = [
  { href: "/dashboard", label: "대시보드", Icon: DashboardIcon },
  { href: "/inquiries", label: "문의 목록", Icon: ListIcon },
  { href: "/inquiries/new", label: "문의 작성", Icon: PencilPlusIcon },
  { href: "/knowledge-base", label: "지식 기반", Icon: BookIcon },
] as const;

function HamburgerIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <path d="M3 5h14M3 10h14M3 15h14" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}

function CloseIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <path d="M5 5l10 10M15 5L5 15" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}

export default function AppShellNav() {
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);

  // Close menu on route change
  useEffect(() => {
    setMenuOpen(false);
  }, [pathname]);

  // Prevent body scroll when menu is open
  useEffect(() => {
    if (menuOpen) {
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = "";
    }
    return () => { document.body.style.overflow = ""; };
  }, [menuOpen]);

  const handleKeyDown = useCallback((e: KeyboardEvent) => {
    if (e.key === "Escape") setMenuOpen(false);
  }, []);

  useEffect(() => {
    if (menuOpen) {
      document.addEventListener("keydown", handleKeyDown);
      return () => document.removeEventListener("keydown", handleKeyDown);
    }
  }, [menuOpen, handleKeyDown]);

  return (
    <>
      {/* Desktop nav */}
      <nav className="hidden md:flex items-center gap-1" aria-label="주 메뉴">
        {navItems.map(({ href, label, Icon }) => (
          <Link key={href} className={itemClass(href, pathname)} href={href}>
            <Icon />
            {label}
          </Link>
        ))}
      </nav>

      {/* Mobile hamburger button */}
      <button
        type="button"
        className="md:hidden flex items-center justify-center rounded-lg p-2 text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
        onClick={() => setMenuOpen(true)}
        aria-label="메뉴 열기"
        aria-expanded={menuOpen}
      >
        <HamburgerIcon />
      </button>

      {/* Mobile overlay menu */}
      {menuOpen && (
        <div
          className="md:hidden fixed inset-0 z-[100] flex"
          role="dialog"
          aria-modal="true"
          aria-label="모바일 메뉴"
        >
          {/* Backdrop */}
          <div
            className="absolute inset-0 bg-black/50 backdrop-blur-sm animate-in fade-in duration-200"
            onClick={() => setMenuOpen(false)}
            aria-hidden="true"
          />

          {/* Slide-in panel */}
          <div className="relative ml-auto w-72 max-w-[85vw] bg-card shadow-2xl animate-in slide-in-from-right duration-200 flex flex-col">
            {/* Close button */}
            <div className="flex items-center justify-between px-4 py-3 border-b border-border/50">
              <span className="text-sm font-semibold text-foreground">메뉴</span>
              <button
                type="button"
                className="flex items-center justify-center rounded-lg p-2 text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
                onClick={() => setMenuOpen(false)}
                aria-label="메뉴 닫기"
              >
                <CloseIcon />
              </button>
            </div>

            {/* Nav items */}
            <nav className="flex-1 flex flex-col gap-1 p-4" aria-label="모바일 주 메뉴">
              {navItems.map(({ href, label, Icon }) => (
                <Link
                  key={href}
                  className={mobileItemClass(href, pathname)}
                  href={href}
                  onClick={() => setMenuOpen(false)}
                >
                  <Icon />
                  {label}
                </Link>
              ))}
            </nav>
          </div>
        </div>
      )}
    </>
  );
}
