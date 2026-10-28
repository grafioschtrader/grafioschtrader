import { expect, test } from '@playwright/test';
import { libHelpers } from './helpers';
import { assertUnhandledErrorsReachAngular } from './bootstrap.assertions';

test('unhandled browser errors reach Angular ErrorHandler', async ({ page }) => {
  await assertUnhandledErrorsReachAngular(page);
});

test('mail composer renders its fields before recipient options arrive', async ({ page }) => {
  await libHelpers.loginAsFixtureUser(page, 'admin');
  let releaseRecipients!: () => void;
  const recipientsReady = new Promise<void>((resolve) => (releaseRecipients = resolve));
  await page.route('**/api/useradmin/idnicknameexcludeme', async (route) => {
    await recipientsReady;
    await route.continue();
  });
  try {
    await page.getByRole('treeitem', { name: /^(Messages for\/from|Nachrichten für\/von)$/ }).click();
    await page.locator('.data-container').first().click({ button: 'right' });
    await page
      .locator('[role="menu"]:visible')
      .getByRole('menuitem', { name: /^(Message to user|Nachricht an Benutzer)$/ })
      .click();
    const dialog = page.locator('.p-dialog:visible');
    await expect(dialog.locator('#subject')).toBeVisible();
    await expect(dialog.locator('button[type="submit"]')).toBeDisabled();
    releaseRecipients();
    await expect(dialog.locator('select#idUserTo')).toContainText(libHelpers.getUser('user').nickname);
    await dialog.locator('.p-dialog-close-button').click();
    await expect(dialog).toBeHidden();
  } finally {
    releaseRecipients();
  }
});

test('mainview reload preserves translated navigation and authenticated routing', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => {
    if (message.type() === 'error' && /NG\d{4}/.test(message.text())) errors.push(message.text());
  });
  await libHelpers.loginAsFixtureUser(page, 'admin');
  await expect(page.getByRole('menuitem', { name: /^(Settings|Einstellungen)$/ })).toBeVisible();
  // A non-default session language loads before the root asks for the English fallback. Keep that second request
  // outstanding while the shell renders, exposing consumers that copy labels before translation has completed.
  await page.evaluate(() => sessionStorage.setItem('language', 'de'));
  await page.route('**/api/globalparameters/properties/en', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 500));
    await route.continue();
  });
  await page.reload();
  expect(await page.evaluate(() => typeof (window as any).Zone), 'grafiosch-host must not load zone.js').toBe(
    'undefined'
  );
  await expect(page.getByRole('menuitem', { name: /^(Settings|Einstellungen)$/ })).toBeVisible();
  await expect(
    page.locator('.p-tree-node-label', { hasText: /Administrative data|Administrative Daten/ })
  ).toBeVisible();
  await expect(page.locator('dashboard article').first()).toBeVisible();
  await expect(page.locator('dashboard article a').first()).toBeVisible();
  await page
    .locator('.p-tree-node-label', { hasText: /User settings|Benutzer Einstellungen|Benutzereinstellungen/ })
    .click();
  await expect(page.locator('tbody tr', { hasText: 'admin' }).first()).toBeVisible();
  expect(errors).toEqual([]);
});

test('unreachable translations block bootstrap and retry recovers', async ({ page }) => {
  await page.route('**/api/**', (route) => route.abort('connectionrefused'));
  await page.goto('/login');
  await expect(page.getByRole('alertdialog')).toContainText('Server unavailable — Server nicht erreichbar');
  await expect(page.locator('#email')).toHaveCount(0);
  await page.unroute('**/api/**');
  await page.getByRole('button', { name: 'Retry — Erneut versuchen' }).click();
  await expect(page.locator('#email')).toBeVisible();
  await expect(page.getByRole('alertdialog')).toHaveCount(0);
});

test('forced password change renders after login and returns to sign-in after saving', async ({ page }) => {
  const credentials = libHelpers.getUser('admin');
  // Exercise the server's forced-change response without changing the shared password policy. The update itself
  // goes to the real backend and retains the fixture password, so another run needs no credential repair.
  await page.route(
    '**/api/login',
    async (route) => {
      const response = await route.fetch();
      expect(response.ok()).toBe(true);
      await route.fulfill({ response, json: { ...(await response.json()), passwordRegexOk: false } });
    },
    { times: 1 }
  );
  await page.goto('/login');
  await page.locator('#email').fill(credentials.email);
  await page.locator('#password').fill(credentials.password);
  await page.locator('button[type="submit"]').click();
  const dialog = page.locator('.p-dialog:visible', { has: page.locator('#passwordOld') });
  await expect(dialog).toBeVisible();
  await expect(dialog.locator('.p-dialog-close-button')).toHaveCount(0);
  await page.keyboard.press('Escape');
  await expect(dialog).toBeVisible();
  await dialog.locator('#passwordOld').fill(credentials.password);
  await dialog.locator('#password').fill(credentials.password);
  await dialog.locator('#passwordConfirm').fill(credentials.password);
  await expect(dialog.locator('button[type="submit"]')).toBeEnabled();
  const updated = page.waitForResponse((response) => response.url().endsWith('/api/user/password'));
  await dialog.locator('button[type="submit"]').click();
  expect((await updated).ok()).toBe(true);
  await expect(dialog).toBeHidden();
  await expect(page.locator('#email')).toBeVisible();
  await libHelpers.loginAsFixtureUser(page, 'admin');
});
