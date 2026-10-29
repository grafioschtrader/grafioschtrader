import { expect, test } from '@playwright/test';

/** Public registration feedback in both hosts, without creating a user or a registration token. */
export function routeInputScenarios(): void {
  test.describe('public route inputs', () => {
    test.use({ actionTimeout: 10_000 });
    for (const separator of ['?', ';']) {
      test(`login shows registration feedback from ${separator === '?' ? 'query' : 'matrix'} parameters`, async ({
        page
      }) => {
        await page.goto(`/login${separator}success=TOKEN_SUCCESS`);
        await expect(page.locator('.alert-success')).toContainText(/verified successfully|erfolgreich/i);
        await page.getByRole('button', { name: /^(Register|Registrieren)$/ }).click();
        await expect(page).toHaveURL(/\/register$/);
        await page.getByRole('button', { name: /^(Sign in|Anmelden)$/i }).click();
        await expect(page).toHaveURL(/\/login$/);
        await expect(page.locator('.alert-success')).toHaveCount(0);
      });
    }

    test('token verification reads its query input and shows the failure after redirecting', async ({ page }) => {
      await page.goto('/tokenverify?token=route-input-invalid-token');
      await expect(page).toHaveURL(/\/register;failure=TOKEN_INVALID$/);
      await expect(page.getByRole('alert')).toContainText(/not valid|ungültig/i);
      await expect(page.locator('#email')).toBeVisible();
    });
  });
}
