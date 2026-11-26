import { expect, Page, test } from '@playwright/test';
import { libHelpers } from './helpers';

// Settings → Theme of the menu bar switches the color scheme of the library host. The mode is a browser preference in
// localStorage ('gt.theme'); a pre-paint script applies it before the application starts, and 'System' follows the
// prefers-color-scheme media query of the operating system live. Every test restores the mode 'system'.

const RX = {
  settings: /^(Einstellungen|Settings)$/,
  theme: /^(Erscheinungsbild|Theme)$/,
  system: /^System$/,
  light: /^(Hell|Light)$/,
  dark: /^(Dunkel|Dark)$/
};

async function openThemeMenu(page: Page): Promise<void> {
  await page.locator('menubar').getByRole('menuitem', { name: RX.settings }).first().click();
  await page.getByRole('menuitem', { name: RX.theme }).first().hover();
  await expect(page.getByRole('menuitem', { name: RX.dark }).first()).toBeVisible({ timeout: 5_000 });
}

async function selectTheme(page: Page, entry: RegExp): Promise<void> {
  await openThemeMenu(page);
  await page.getByRole('menuitem', { name: entry }).first().click();
  await page.keyboard.press('Escape');
}

function currentTheme(page: Page): Promise<string | null> {
  return page.evaluate(() => document.documentElement.getAttribute('data-bs-theme'));
}

test.afterEach(async ({ page }) => {
  await page.evaluate(() => localStorage.removeItem('gt.theme')).catch(() => undefined);
});

test('Dark from the menu applies the dark scheme to Bootstrap and Optimus and survives a reload', async ({ page }) => {
  await libHelpers.loginAsFixtureUser(page, 'admin');
  await page.evaluate(() => localStorage.removeItem('gt.theme'));
  await expect(page.locator('p-tree')).toBeVisible();

  await selectTheme(page, RX.dark);
  await expect.poll(() => currentTheme(page)).toBe('dark');
  expect(await page.evaluate(() => localStorage.getItem('gt.theme'))).toBe('dark');

  // Bootstrap reads data-bs-theme for its body color, Optimus resolves its dark tokens through the same attribute.
  const bootstrapBg = await page.evaluate(() =>
    getComputedStyle(document.documentElement).getPropertyValue('--bs-body-bg').trim()
  );
  expect(bootstrapBg.toLowerCase()).not.toBe('#fff');
  await openThemeMenu(page);
  const submenuBg = await page
    .locator('.p-menubar-submenu:visible')
    .last()
    .evaluate((element) => getComputedStyle(element).backgroundColor);
  expect(submenuBg, 'Optimus menu overlay in the dark scheme').not.toBe('rgb(255, 255, 255)');
  await page.keyboard.press('Escape');

  await page.reload();
  await expect.poll(() => currentTheme(page)).toBe('dark');
});

test('System follows the operating system live and stays the marked entry', async ({ page }) => {
  await libHelpers.loginAsFixtureUser(page, 'admin');
  await expect(page.locator('p-tree')).toBeVisible();
  await selectTheme(page, RX.light);
  await expect.poll(() => currentTheme(page)).toBe('light');

  await page.emulateMedia({ colorScheme: 'dark' });
  await selectTheme(page, RX.system);
  await expect.poll(() => currentTheme(page)).toBe('dark');
  expect(await page.evaluate(() => localStorage.getItem('gt.theme'))).toBe('system');

  await openThemeMenu(page);
  await expect(page.getByRole('menuitem', { name: RX.system }).first().locator('.pi-check')).toHaveCount(1);
  await expect(page.getByRole('menuitem', { name: RX.dark }).first().locator('.pi-check')).toHaveCount(0);
  await page.keyboard.press('Escape');

  // A change of the operating system scheme applies without a reload
  await page.emulateMedia({ colorScheme: 'light' });
  await expect.poll(() => currentTheme(page)).toBe('light');
});
