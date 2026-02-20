import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, act } from "@testing-library/react";
import OfflineBanner from "./OfflineBanner";

describe("OfflineBanner", () => {
  let originalOnLine: boolean;

  beforeEach(() => {
    originalOnLine = navigator.onLine;
  });

  afterEach(() => {
    Object.defineProperty(navigator, "onLine", {
      writable: true,
      value: originalOnLine,
    });
  });

  it("does not render when online", () => {
    Object.defineProperty(navigator, "onLine", { writable: true, value: true });

    const { container } = render(<OfflineBanner />);
    expect(container.firstChild).toBeNull();
  });

  it("renders alert when initially offline", () => {
    Object.defineProperty(navigator, "onLine", { writable: true, value: false });

    render(<OfflineBanner />);
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByText("네트워크 연결이 끊겼습니다. 인터넷 연결을 확인해 주세요.")).toBeInTheDocument();
  });

  it("shows banner when offline event fires", () => {
    Object.defineProperty(navigator, "onLine", { writable: true, value: true });

    render(<OfflineBanner />);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();

    act(() => {
      window.dispatchEvent(new Event("offline"));
    });

    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("hides banner when online event fires", () => {
    Object.defineProperty(navigator, "onLine", { writable: true, value: false });

    render(<OfflineBanner />);
    expect(screen.getByRole("alert")).toBeInTheDocument();

    act(() => {
      window.dispatchEvent(new Event("online"));
    });

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("has assertive aria-live attribute", () => {
    Object.defineProperty(navigator, "onLine", { writable: true, value: false });

    render(<OfflineBanner />);
    expect(screen.getByRole("alert")).toHaveAttribute("aria-live", "assertive");
  });
});
