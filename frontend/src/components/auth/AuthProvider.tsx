"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useRouter, usePathname } from "next/navigation";
import {
  login as apiLogin,
  fetchMe,
  clearTokens,
  getStoredAccessToken,
  getStoredRefreshToken,
  refreshAccessToken,
  type AuthUser,
  type LoginPayload,
} from "@/lib/api/client";

interface AuthContextValue {
  user: AuthUser | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (payload: LoginPayload) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return ctx;
}

const PUBLIC_PATHS = ["/login"];

export default function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const router = useRouter();
  const pathname = usePathname();

  const logout = useCallback(() => {
    clearTokens();
    document.cookie = "cs_authenticated=; path=/; max-age=0";
    setUser(null);
    router.push("/login");
  }, [router]);

  // Restore session on mount
  useEffect(() => {
    const restore = async () => {
      const accessToken = getStoredAccessToken();
      const refreshToken = getStoredRefreshToken();

      if (!accessToken && !refreshToken) {
        setIsLoading(false);
        return;
      }

      try {
        // If access token exists, try /me
        if (accessToken) {
          const me = await fetchMe();
          document.cookie = "cs_authenticated=1; path=/; max-age=900; SameSite=Lax";
          setUser(me);
          setIsLoading(false);
          return;
        }

        // If only refresh token, try silent refresh first
        if (refreshToken) {
          await refreshAccessToken();
          const me = await fetchMe();
          document.cookie = "cs_authenticated=1; path=/; max-age=900; SameSite=Lax";
          setUser(me);
        }
      } catch {
        clearTokens();
        document.cookie = "cs_authenticated=; path=/; max-age=0";
      } finally {
        setIsLoading(false);
      }
    };

    restore();
  }, []);

  // Silent refresh timer: refresh 1 minute before expiry (14 min interval for 15 min token)
  useEffect(() => {
    if (!user) return;

    const interval = setInterval(async () => {
      try {
        await refreshAccessToken();
      } catch {
        logout();
      }
    }, 14 * 60 * 1000);

    return () => clearInterval(interval);
  }, [user, logout]);

  // Redirect unauthenticated users (client-side guard)
  useEffect(() => {
    if (isLoading) return;

    const isPublic = PUBLIC_PATHS.some((p) => pathname.startsWith(p));

    if (!user && !isPublic) {
      router.replace("/login");
    }

    if (user && pathname === "/login") {
      router.replace("/dashboard");
    }
  }, [user, isLoading, pathname, router]);

  const handleLogin = useCallback(
    async (payload: LoginPayload) => {
      const result = await apiLogin(payload);
      document.cookie = `cs_authenticated=1; path=/; max-age=${result.expiresIn}; SameSite=Lax`;
      setUser({
        id: result.user.id,
        username: result.user.username,
        displayName: result.user.displayName,
        email: result.user.email,
        roles: Array.from(result.user.roles),
      });
      // Full navigation to bypass Next.js Router Cache (middleware redirect cache)
      window.location.href = "/dashboard";
    },
    [],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isLoading,
      isAuthenticated: !!user,
      login: handleLogin,
      logout,
    }),
    [user, isLoading, handleLogin, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
