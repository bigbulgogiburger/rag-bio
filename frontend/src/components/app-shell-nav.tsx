"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

function itemClass(href: string, pathname: string): string {
  const active = pathname === href;
  return `menu-item${active ? " active" : ""}`;
}

export default function AppShellNav() {
  const pathname = usePathname();

  return (
    <nav className="menu-nav" aria-label="주 메뉴">
      <Link className={itemClass("/dashboard", pathname)} href="/dashboard">대시보드</Link>
      <Link className={itemClass("/inquiry/new", pathname)} href="/inquiry/new">문의 작성</Link>
    </nav>
  );
}
