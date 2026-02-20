import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Toast from "../Toast";

describe("Toast", () => {
  it("renders message with alert role", () => {
    render(<Toast message="성공 메시지" variant="success" onClose={vi.fn()} />);
    expect(screen.getByRole("alert")).toHaveTextContent("성공 메시지");
  });

  it("calls onClose when close button clicked", async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<Toast message="테스트" variant="info" onClose={onClose} />);

    await user.click(screen.getByRole("button", { name: "닫기" }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("auto-dismisses after duration", async () => {
    vi.useFakeTimers();
    const onClose = vi.fn();
    render(<Toast message="테스트" variant="warn" onClose={onClose} duration={2000} />);

    expect(onClose).not.toHaveBeenCalled();
    vi.advanceTimersByTime(2000);
    expect(onClose).toHaveBeenCalledOnce();
    vi.useRealTimers();
  });

  it("renders success variant icon", () => {
    render(<Toast message="성공" variant="success" onClose={vi.fn()} />);
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("renders error variant icon", () => {
    render(<Toast message="에러" variant="error" onClose={vi.fn()} />);
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("renders warn variant icon", () => {
    render(<Toast message="경고" variant="warn" onClose={vi.fn()} />);
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("renders info variant icon", () => {
    render(<Toast message="정보" variant="info" onClose={vi.fn()} />);
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("does not auto-dismiss when duration is 0", () => {
    vi.useFakeTimers();
    const onClose = vi.fn();
    render(<Toast message="테스트" variant="info" onClose={onClose} duration={0} />);

    vi.advanceTimersByTime(10000);
    expect(onClose).not.toHaveBeenCalled();
    vi.useRealTimers();
  });
});
