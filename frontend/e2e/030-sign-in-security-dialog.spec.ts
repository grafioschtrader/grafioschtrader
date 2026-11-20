import { expect, test } from '@playwright/test';
import { login } from './helpers';

/**
 * The sign-in security dialog of the reusable library is reachable from the settings menu of Grafioschtrader. Passkeys
 * and the authenticator app are exercised by `e2e/lib/026-totp-sign-in.spec.ts` and `e2e/lib/027-passkey-sign-in.spec.ts`
 * in the standalone host; this spec opens the dialog, checks that both sections are there - the passkeys as table or,
 * without a usable public address, as hint - walks into the proof step and cancels. It changes no data.
 */
test('the sign-in security dialog opens from the settings menu', async ({ page }) => {
  await login(page);
  await page.getByRole('menuitem', { name: /^(Settings|Einstellungen)$/ }).click();
  await page.getByRole('menuitem', { name: /^(Sign-in security|Anmeldesicherheit)\.\.\.$/ }).click();
  const dialog = page.locator('sign-in-security-dialog .p-dialog');
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('heading', { name: /^Passkeys$/ })).toBeVisible();
  await expect(dialog.locator('user-passkey-table, .p-message')).toBeVisible();
  await expect(dialog).toContainText(/(Authenticator app \(TOTP\)|Authenticator-App \(TOTP\))/);
  const setup = dialog.getByRole('button', { name: /^(Set up authenticator app|Authenticator-App einrichten)$/ });
  await expect(setup).toBeVisible();
  await setup.click();
  await expect(dialog.locator('#password')).toBeVisible();
  await dialog.getByRole('button', { name: /^(Cancel|Abbrechen)$/ }).click();
  await expect(dialog.locator('#password')).toHaveCount(0);
  // Closing removes the dialog, so the settings menu can open it again
  await dialog.locator('.p-dialog-close-button').click();
  await expect(dialog).toHaveCount(0);
  await page.getByRole('menuitem', { name: /^(Settings|Einstellungen)$/ }).click();
  await page.getByRole('menuitem', { name: /^(Sign-in security|Anmeldesicherheit)\.\.\.$/ }).click();
  await expect(dialog).toBeVisible();
});
