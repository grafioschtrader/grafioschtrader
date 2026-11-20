import { expect, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { holdTenantResponse as holdResponse } from './tenant-notification.helpers';
import { performanceDates, periodResult } from './performance-notification.helpers';

test.use({ viewport: { width: 1800, height: 1100 }, actionTimeout: 15_000 });

test('a tenant without holdings stops loading and disables the calculation', async ({ page }) => {
  await loginAsFixtureUser(page, 'alledit');
  const dates = await holdResponse(page, '**/api/holding/getdatesforform?*', {
    ...performanceDates,
    firstEverTradingDay: null
  });
  try {
    await page.goto('/mainview/tenantTabMenu/performancetabmenu');
    await dates.arrived;
    await expect(page.locator('p-progressbar')).toBeVisible();
    dates.release();
    await expect(page.locator('p-progressbar')).toHaveCount(0);
    await expect(page.locator('button[type=submit]')).toBeDisabled();
    await expect(page.locator('#dateFrom input')).toBeDisabled();
  } finally {
    dates.release();
  }
});

test('period results, metrics and tree columns follow delayed calculations', async ({ page }) => {
  await loginAsFixtureUser(page, 'alledit');
  const dates = await holdResponse(page, '**/api/holding/getdatesforform?*', performanceDates);
  try {
    await page.goto('/mainview/tenantTabMenu/performancetabmenu');
    await dates.arrived;
    await expect(page.locator('#dateFrom input')).toBeVisible();
    dates.release();
    await expect(page.locator('#dateTo input')).not.toHaveValue('');
    await page.locator('select#periodSplit').selectOption('WM_YEAR');
    const calculation = await holdResponse(page, '**/api/holding/*/*/WM_YEAR?*', periodResult());
    try {
      await page.locator('button[type=submit]').click();
      await calculation.arrived;
      await expect(page.locator('p-progressbar')).toBeVisible();
      calculation.release();
      await expect(page.locator('p-progressbar')).toHaveCount(0);
      await expect(page.locator('performance-period-metrics')).toContainText(/12[.,]34/);
      await expect(page.locator('performance-period-from-to-diff')).toContainText(/333[.,]75/);
      const tree = page.locator('performance-period-treetable');
      await expect(tree.locator('th')).toHaveCount(15);
      await expect(tree).toContainText(/333[.,]75/);
      await tree.locator('p-treetabletoggler button').first().click();
      await expect(tree.locator('tbody tr')).toHaveCount(5);
      await expect(tree).toContainText(/TWR|Zeitgewichtet|Time.weighted/);
      await page.locator('select#periodSplit').selectOption('WM_WEEK');
      const weekly = await holdResponse(page, '**/api/holding/*/*/WM_WEEK?*', periodResult('WM_WEEK', 456.25));
      try {
        await page.locator('button[type=submit]').click();
        await weekly.arrived;
        weekly.release();
        await expect(tree.locator('th')).toHaveCount(8);
        await expect(tree).toContainText(/456[.,]25/);
        await expect(page.locator('performance-period-from-to-diff')).toContainText(/456[.,]25/);
      } finally {
        weekly.release();
      }
    } finally {
      calculation.release();
    }
  } finally {
    dates.release();
  }
});

test('PDF dialog loads late metadata, updates section hints and recovers from a failed download', async ({ page }) => {
  await loginAsFixtureUser(page, 'alledit');
  await page.route('**/api/holding/getdatesforform?*', (route) => route.fulfill({ json: performanceDates }));
  await page.route('**/api/holding/*/*/WM_YEAR?*', (route) => route.fulfill({ json: periodResult() }));
  await page.goto('/mainview/tenantTabMenu/performancetabmenu');
  await expect(page.locator('#dateTo input')).not.toHaveValue('');
  await page.locator('select#periodSplit').selectOption('WM_YEAR');
  await page.locator('button[type=submit]').click();
  await expect(page.locator('performance-period-metrics')).toContainText(/12[.,]34/);
  let release!: () => void;
  let arrive!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const arrived = new Promise<void>((resolve) => (arrive = resolve));
  await page.route(
    '**/api/holding/report/options',
    async (route) => {
      const response = await route.fetch();
      expect(response.ok()).toBeTruthy();
      arrive();
      await gate;
      await route.fulfill({ response });
    },
    { times: 1 }
  );
  try {
    await page.getByRole('menuitem', { name: /^(Show|Ansicht)$/ }).click();
    await page.getByRole('menuitem', { name: /PDF.report|PDF-Bericht/i }).click();
    await arrived;
    await expect(page.locator('p-progressbar')).toBeVisible();
    release();
    const dialog = page.locator('.p-dialog:visible');
    await expect(dialog.locator('select#preset')).toBeVisible();
    await dialog.locator('p-multiselect#sections').click();
    await page.getByRole('option', { name: /^(Transactions|Transaktionen)$/ }).click();
    await page.keyboard.press('Escape');
    await expect(dialog.getByRole('status').last()).toBeVisible();
    let fail!: () => void;
    let requested!: () => void;
    const failureGate = new Promise<void>((resolve) => (fail = resolve));
    const requestedGate = new Promise<void>((resolve) => (requested = resolve));
    await page.route(
      '**/api/holding/report/pdf',
      async (route) => {
        requested();
        await failureGate;
        await route.fulfill({ status: 500, json: { error: { message: 'gt.report.invalid.download' } } });
      },
      { times: 1 }
    );
    try {
      await dialog.locator('button[type=submit]').click();
      await requestedGate;
      await expect(dialog.locator('p-progressbar')).toBeVisible();
      fail();
      await expect(dialog.locator('p-progressbar')).toHaveCount(0);
      await expect(dialog.locator('button[type=submit]')).toBeEnabled();
      await expect(page.getByRole('alert')).toBeVisible();
    } finally {
      fail();
    }
    await page.route(
      '**/api/holding/report/pdf',
      (route) =>
        route.fulfill({
          contentType: 'application/pdf',
          headers: { 'Content-Disposition': 'attachment; filename="notification-performance.pdf"' },
          body: '%PDF-1.4\n% notification fixture\n%%EOF'
        }),
      { times: 1 }
    );
    const download = page.waitForEvent('download');
    await dialog.locator('button[type=submit]').click();
    expect((await download).suggestedFilename()).toBe('notification-performance.pdf');
    await expect(dialog).toHaveCount(0);
    await page.getByRole('menuitem', { name: /^(Show|Ansicht)$/ }).click();
    await page.getByRole('menuitem', { name: /PDF.report|PDF-Bericht/i }).click();
    await expect(page.locator('.p-dialog:visible select#preset')).toBeVisible();
    await page.locator('.p-dialog:visible .p-dialog-close-button').click();
  } finally {
    release();
  }
});
