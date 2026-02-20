import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    exclude: ["node_modules", ".next", "e2e"],
    coverage: {
      provider: "v8",
      reporter: ["text", "html", "lcov"],
      reportsDirectory: "./coverage",
      include: ["src/**/*.{ts,tsx}"],
      exclude: [
        "src/test/**",
        "src/**/*.test.{ts,tsx}",
        "src/**/*.d.ts",
        "src/app/layout.tsx",
        // Page components are integration-level; covered by E2E tests
        "src/app/**/page.tsx",
        // Complex tab components with SSE/dynamic imports; covered by E2E
        "src/components/inquiry/InquiryAnswerTab.tsx",
        "src/components/inquiry/InquiryInfoTab.tsx",
        "src/components/inquiry/InquiryHistoryTab.tsx",
        // Upload components require browser File API; covered by E2E
        "src/components/upload/**",
        // SSE hook requires EventSource; covered by E2E
        "src/hooks/useInquiryEvents.ts",
      ],
      thresholds: {
        lines: 70,
        branches: 60,
        functions: 70,
        statements: 70,
      },
    },
    css: false,
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
