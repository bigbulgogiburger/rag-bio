import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import ProtectedRoute from "../ProtectedRoute";
import AuthProvider from "../AuthProvider";

const mockFetchMe = vi.fn();
const mockGetStoredAccessToken = vi.fn();
const mockGetStoredRefreshToken = vi.fn();

vi.mock("@/lib/api/client", () => ({
  login: vi.fn(),
  fetchMe: (...args: unknown[]) => mockFetchMe(...args),
  clearTokens: vi.fn(),
  getStoredAccessToken: () => mockGetStoredAccessToken(),
  getStoredRefreshToken: () => mockGetStoredRefreshToken(),
  refreshAccessToken: vi.fn(),
  storeTokens: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn(), prefetch: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/dashboard",
}));

describe("ProtectedRoute", () => {
  it("shows loading state when session restore is in progress", () => {
    // Return a token so AuthProvider enters the async fetchMe path
    mockGetStoredAccessToken.mockReturnValue("fake-access-token");
    mockGetStoredRefreshToken.mockReturnValue(null);
    // Make fetchMe hang so isLoading stays true
    mockFetchMe.mockReturnValue(new Promise(() => {}));

    render(
      <AuthProvider>
        <ProtectedRoute>
          <div>Protected Content</div>
        </ProtectedRoute>
      </AuthProvider>,
    );

    expect(screen.getByText("인증 확인 중...")).toBeInTheDocument();
  });

  it("renders nothing when not authenticated after loading", async () => {
    mockGetStoredAccessToken.mockReturnValue(null);
    mockGetStoredRefreshToken.mockReturnValue(null);

    render(
      <AuthProvider>
        <ProtectedRoute>
          <div>Protected Content</div>
        </ProtectedRoute>
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.queryByText("인증 확인 중...")).not.toBeInTheDocument();
    });
    expect(screen.queryByText("Protected Content")).not.toBeInTheDocument();
  });
});
