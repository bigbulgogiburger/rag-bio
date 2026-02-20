import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import AppShellNav from "../app-shell-nav";

let mockPathname = "/dashboard";

vi.mock("next/navigation", () => ({
  usePathname: () => mockPathname,
}));

describe("AppShellNav", () => {
  it("renders all navigation links", () => {
    render(<AppShellNav />);
    expect(screen.getByRole("navigation", { name: "주 메뉴" })).toBeInTheDocument();
    expect(screen.getByText("대시보드")).toBeInTheDocument();
    expect(screen.getByText("문의 목록")).toBeInTheDocument();
    expect(screen.getByText("문의 작성")).toBeInTheDocument();
    expect(screen.getByText("지식 기반")).toBeInTheDocument();
  });

  it("highlights dashboard link when on dashboard", () => {
    mockPathname = "/dashboard";
    render(<AppShellNav />);
    const dashboardLink = screen.getByText("대시보드").closest("a");
    expect(dashboardLink?.className).toContain("text-primary");
  });

  it("highlights inquiries link when on inquiries page", () => {
    mockPathname = "/inquiries";
    render(<AppShellNav />);
    const inquiriesLink = screen.getByText("문의 목록").closest("a");
    expect(inquiriesLink?.className).toContain("text-primary");
  });

  it("highlights inquiries/new link when on create page", () => {
    mockPathname = "/inquiries/new";
    render(<AppShellNav />);
    const createLink = screen.getByText("문의 작성").closest("a");
    expect(createLink?.className).toContain("text-primary");
    // inquiries list should NOT be active
    const listLink = screen.getByText("문의 목록").closest("a");
    expect(listLink?.className).not.toContain("text-primary");
  });

  it("highlights knowledge-base link when on KB page", () => {
    mockPathname = "/knowledge-base";
    render(<AppShellNav />);
    const kbLink = screen.getByText("지식 기반").closest("a");
    expect(kbLink?.className).toContain("text-primary");
  });

  it("renders correct href attributes", () => {
    mockPathname = "/dashboard";
    render(<AppShellNav />);
    expect(screen.getByText("대시보드").closest("a")).toHaveAttribute("href", "/dashboard");
    expect(screen.getByText("문의 목록").closest("a")).toHaveAttribute("href", "/inquiries");
    expect(screen.getByText("문의 작성").closest("a")).toHaveAttribute("href", "/inquiries/new");
    expect(screen.getByText("지식 기반").closest("a")).toHaveAttribute("href", "/knowledge-base");
  });
});
