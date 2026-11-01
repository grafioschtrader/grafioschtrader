import { expect, test } from '@playwright/test';
import { getUser, loginAsFixtureUser } from './helpers';
import { assertUnhandledErrorsReachAngular } from './lib/bootstrap.assertions';

/**
 * Start of Grafioschtrader without zone.js: the shell, the translations that block the bootstrap and the forced
 * password change must all render through signals and template listeners alone. Nothing is written to
 * grafioschtrader_t; the forced password change saves the unchanged fixture password.
 */

test('unhandled browser errors reach Angular ErrorHandler', async ({ page }) => {
  await assertUnhandledErrorsReachAngular(page);
});

test('the application runs without zone.js and a reload of the main view renders the shell', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => {
    if (message.type() === 'error' && /NG\d{4}/.test(message.text())) errors.push(message.text());
  });
  await loginAsFixtureUser(page, 'alledit');
  await page.reload();
  expect(await page.evaluate(() => typeof (window as any).Zone), 'Grafioschtrader must not load zone.js').toBe(
    'undefined'
  );
  await expect(page.getByRole('menuitem', { name: /^(Settings|Einstellungen)$/ })).toBeVisible();
  await expect(page.getByRole('treeitem', { name: /^Portfolios-/ }).first()).toBeVisible({ timeout: 15_000 });
  await expect(page.locator('dashboard article').first()).toBeVisible();
  expect(errors).toEqual([]);
});

test('tab defaults preserve direct child routes and the last tree selection wins', async ({ page }) => {
  await loginAsFixtureUser(page, 'limit2');
  const root = page.getByRole('treeitem', { name: /^(Base Data|Basisdaten).*$/ }).first();
  const exchange = page.getByRole('treeitem', { name: /^(Stock\s*exchange|Handelsplatz)$/ }).first();
  // Selecting the root starts its proposal tab host; immediately choose a different tab host from its children.
  for (let visit = 0; visit < 4; visit++) {
    await root.locator(':scope > .p-tree-node-content').click();
    if ((await root.getAttribute('aria-expanded')) !== 'true')
      await root.locator(':scope > .p-tree-node-content > button').click();
    await exchange.locator(':scope > .p-tree-node-content').click();
    await expect(page).toHaveURL(/\/mainview\/stockexchange\/exchanges$/);
    await expect(page.locator('configurable-table tbody tr').first()).toBeVisible();
  }
  await page.goto('/mainview/stockexchange/rulesets');
  const ruleTab = page.getByRole('tab', { name: /Trading calendar rules|Handelskalender-Regeln/i });
  await expect(ruleTab).toHaveAttribute('aria-selected', 'true');
  await expect(page.locator('configurable-table tbody tr').first()).toBeVisible();
  await expect(page).toHaveURL(/\/mainview\/stockexchange\/rulesets$/);
  await page.reload();
  await expect(ruleTab).toHaveAttribute('aria-selected', 'true');
  await expect(page.locator('configurable-table tbody tr').first()).toBeVisible();
  await expect(page).toHaveURL(/\/mainview\/stockexchange\/rulesets$/);
});

test('unreachable translations block the bootstrap and a retry recovers', async ({ page }) => {
  await page.route('**/api/**', (route) => route.abort('connectionrefused'));
  await page.goto('/login');
  await expect(page.getByRole('alertdialog')).toContainText('Server unavailable — Server nicht erreichbar');
  await expect(page.locator('#email')).toHaveCount(0);
  await page.unroute('**/api/**');
  await page.getByRole('button', { name: 'Retry — Erneut versuchen' }).click();
  await expect(page.locator('#email')).toBeVisible();
  await expect(page.getByRole('alertdialog')).toHaveCount(0);
});

test('a forced password change renders after login and returns to sign-in after saving', async ({ page }) => {
  const credentials = getUser('alledit');
  // The server's forced-change answer is simulated, so the shared password policy stays untouched.
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
  await dialog.locator('#passwordOld').fill(credentials.password);
  await dialog.locator('#password').fill(credentials.password);
  await dialog.locator('#passwordConfirm').fill(credentials.password);
  await expect(dialog.locator('button[type="submit"]')).toBeEnabled();
  const updated = page.waitForResponse((response) => response.url().endsWith('/api/user/password'));
  await dialog.locator('button[type="submit"]').click();
  expect((await updated).ok()).toBe(true);
  await expect(dialog).toBeHidden();
  await expect(page.locator('#email')).toBeVisible();
  await loginAsFixtureUser(page, 'alledit');
});
