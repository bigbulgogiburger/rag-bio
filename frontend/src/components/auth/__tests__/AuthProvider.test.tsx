import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AuthProvider, { useAuth } from "../AuthProvider";

// Mock the api client
const mockLogin = vi.fn();
const mockFetchMe = vi.fn();
const mockClearTokens = vi.fn();
const mockGetStoredAccessToken = vi.fn();
const mockGetStoredRefreshToken = vi.fn();
const mockRefreshAccessToken = vi.fn();

vi.mock("@/lib/api/client", () => ({
  login: (...args: unknown[]) => mockLogin(...args),
  fetchMe: () => mockFetchMe(),
  clearTokens: () => mockClearTokens(),
  getStoredAccessToken: () => mockGetStoredAccessToken(),
  getStoredRefreshToken: () => mockGetStoredRefreshToken(),
  refreshAccessToken: () => mockRefreshAccessToken(),
  storeTokens: vi.fn(),
}));

const mockPush = vi.fn();
const mockReplace = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace, back: vi.fn(), prefetch: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/dashboard",
}));

function TestConsumer() {
  const { user, isLoading, isAuthenticated, logout } = useAuth();
  return (
    <div>
      <span data-testid="loading">{String(isLoading)}</span>
      <span data-testid="authenticated">{String(isAuthenticated)}</span>
      <span data-testid="username">{user?.username ?? "none"}</span>
      <button onClick={logout}>Logout</button>
    </div>
  );
}

describe("AuthProvider", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetStoredAccessToken.mockReturnValue(null);
    mockGetStoredRefreshToken.mockReturnValue(null);
    // Clear cookie
    document.cookie = "cs_authenticated=; path=/; max-age=0";
  });

  it("starts with loading state and resolves to unauthenticated when no tokens", async () => {
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("loading").textContent).toBe("false");
    });
    expect(screen.getByTestId("authenticated").textContent).toBe("false");
    expect(screen.getByTestId("username").textContent).toBe("none");
  });

  it("restores session from access token", async () => {
    mockGetStoredAccessToken.mockReturnValue("valid-token");
    mockFetchMe.mockResolvedValue({
      id: "1",
      username: "testuser",
      displayName: "Test User",
      email: "test@example.com",
      roles: ["CS_AGENT"],
    });

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("loading").textContent).toBe("false");
    });
    expect(screen.getByTestId("authenticated").textContent).toBe("true");
    expect(screen.getByTestId("username").textContent).toBe("testuser");
  });

  it("clears tokens when session restore fails", async () => {
    mockGetStoredAccessToken.mockReturnValue("expired-token");
    mockFetchMe.mockRejectedValue(new Error("Unauthorized"));

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("loading").textContent).toBe("false");
    });
    expect(mockClearTokens).toHaveBeenCalled();
    expect(screen.getByTestId("authenticated").textContent).toBe("false");
  });

  it("logout clears tokens and redirects to /login", async () => {
    mockGetStoredAccessToken.mockReturnValue("valid-token");
    mockFetchMe.mockResolvedValue({
      id: "1",
      username: "testuser",
      displayName: null,
      email: null,
      roles: [],
    });

    const user = userEvent.setup();

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("authenticated").textContent).toBe("true");
    });

    await user.click(screen.getByText("Logout"));

    expect(mockClearTokens).toHaveBeenCalled();
    expect(mockPush).toHaveBeenCalledWith("/login");
    expect(screen.getByTestId("authenticated").textContent).toBe("false");
  });

  it("tries refresh token when only refresh token exists", async () => {
    mockGetStoredAccessToken.mockReturnValue(null);
    mockGetStoredRefreshToken.mockReturnValue("refresh-token");
    mockRefreshAccessToken.mockResolvedValue({ accessToken: "new-token", tokenType: "Bearer", expiresIn: 900 });
    mockFetchMe.mockResolvedValue({
      id: "2",
      username: "refreshed-user",
      displayName: null,
      email: null,
      roles: [],
    });

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("loading").textContent).toBe("false");
    });
    expect(mockRefreshAccessToken).toHaveBeenCalled();
    expect(screen.getByTestId("username").textContent).toBe("refreshed-user");
  });
});

describe("useAuth", () => {
  it("throws when used outside AuthProvider", () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    expect(() => render(<TestConsumer />)).toThrow("useAuth must be used within AuthProvider");
    consoleError.mockRestore();
  });
});
