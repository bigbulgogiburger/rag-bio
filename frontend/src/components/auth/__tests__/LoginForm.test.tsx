import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import LoginForm from "../LoginForm";
import AuthProvider from "../AuthProvider";

const mockLogin = vi.fn();

vi.mock("@/lib/api/client", () => ({
  login: (...args: unknown[]) => mockLogin(...args),
  fetchMe: vi.fn(),
  clearTokens: vi.fn(),
  getStoredAccessToken: () => null,
  getStoredRefreshToken: () => null,
  refreshAccessToken: vi.fn(),
  storeTokens: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn(), prefetch: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/login",
}));

function renderLoginForm() {
  return render(
    <AuthProvider>
      <LoginForm />
    </AuthProvider>,
  );
}

describe("LoginForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders username and password fields", () => {
    renderLoginForm();
    expect(screen.getByLabelText("아이디")).toBeInTheDocument();
    expect(screen.getByLabelText("비밀번호")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "로그인" })).toBeInTheDocument();
  });

  it("submits login form with credentials", async () => {
    mockLogin.mockResolvedValue({
      accessToken: "token",
      refreshToken: "refresh",
      tokenType: "Bearer",
      expiresIn: 900,
      user: { id: "1", username: "admin", displayName: "Admin", email: "a@b.c", roles: ["ADMIN"] },
    });

    const user = userEvent.setup();
    renderLoginForm();

    await user.type(screen.getByLabelText("아이디"), "admin");
    await user.type(screen.getByLabelText("비밀번호"), "password123");
    await user.click(screen.getByRole("button", { name: "로그인" }));

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith({
        username: "admin",
        password: "password123",
      });
    });
  });

  it("shows error message on login failure", async () => {
    mockLogin.mockRejectedValue(new Error("아이디 또는 비밀번호가 올바르지 않습니다."));

    const user = userEvent.setup();
    renderLoginForm();

    await user.type(screen.getByLabelText("아이디"), "wrong");
    await user.type(screen.getByLabelText("비밀번호"), "wrong");
    await user.click(screen.getByRole("button", { name: "로그인" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("아이디 또는 비밀번호가 올바르지 않습니다.");
    });
  });

  it("disables inputs while submitting", async () => {
    let resolveLogin: (value: unknown) => void;
    mockLogin.mockReturnValue(new Promise((resolve) => { resolveLogin = resolve; }));

    const user = userEvent.setup();
    renderLoginForm();

    await user.type(screen.getByLabelText("아이디"), "admin");
    await user.type(screen.getByLabelText("비밀번호"), "password");
    await user.click(screen.getByRole("button", { name: "로그인" }));

    await waitFor(() => {
      expect(screen.getByLabelText("아이디")).toBeDisabled();
      expect(screen.getByLabelText("비밀번호")).toBeDisabled();
      expect(screen.getByRole("button", { name: "로그인 중..." })).toBeDisabled();
    });

    resolveLogin!({
      accessToken: "t", refreshToken: "r", tokenType: "Bearer", expiresIn: 900,
      user: { id: "1", username: "admin", displayName: null, email: null, roles: [] },
    });
  });
});
