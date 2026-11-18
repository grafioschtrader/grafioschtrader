import { expect, test } from '@playwright/test';

test('password recovery is public and removes the mailbox proof from the address', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('button', { name: /^(Forgot password|Passwort vergessen)$/ }).click();
  await expect(page.locator('#email')).toBeVisible();
  await page.goto('/passwordreset#token=mailbox-proof');
  await expect(page).toHaveURL(/\/passwordreset$/);
  await expect(page.locator('#passwordNew')).toBeVisible();
  await expect(page.locator('#passwordConfirm')).toBeVisible();
  await expect(page.locator('#token')).toHaveCount(0);
});
