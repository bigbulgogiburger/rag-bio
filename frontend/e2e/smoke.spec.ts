import { test, expect } from "@playwright/test";

test.describe("Smoke test", () => {
  test("homepage redirects to dashboard", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveURL(/\/dashboard/);
  });
});
