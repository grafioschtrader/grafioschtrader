import { expect, Page, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { holdResponse } from './response.helpers';

const ownNote = 'E2E OnPush tax-year correction';
test.use({ viewport: { width: 1600, height: 1300 }, actionTimeout: 15_000 });
test.beforeEach(async ({ page }) => {
  await loginAsFixtureUser(page, 'alledit');
});
test.afterEach(async ({ page }) => {
  await page.unrouteAll({ behavior: 'wait' });
});

async function headers(page: Page) {
  return { 'x-auth-token': (await page.evaluate(() => sessionStorage.getItem('jwt')))!, Accept: 'application/json' };
}

async function openReport(page: Page) {
  // The fixture tenant has no country configured. Exercise the Swiss UI without changing its saved settings.
  await page.route('**/api/portfolio/dividends?*', async (route) => {
    const response = await route.fetch();
    expect(response.ok()).toBeTruthy();
    await route.fulfill({
      response,
      json: { ...(await response.json()), tenantCountry: 'CH', availableTaxYears: [2025] }
    });
  });
  const loaded = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/portfolio/dividends');
  await page
    .getByRole('treeitem', { name: /^Portfolios-/ })
    .locator(':scope > .p-tree-node-content')
    .click();
  await page.getByRole('tab', { name: /^(Dividends.*|Dividende\/Zins)$/ }).click();
  const response = await loaded;
  expect(response.ok()).toBeTruthy();
  return response.json();
}

async function openExport(page: Page) {
  await page.locator('p-table h4').first().click();
  await page
    .locator('p-menubar')
    .getByRole('menuitem', { name: /^(View|Ansicht)$/ })
    .click();
  await page
    .locator('p-menubar')
    .getByRole('menuitem', { name: /tax statement|Steuerauszug/i })
    .click();
  const dialog = page.locator('tax-statement-export-dialog .p-dialog');
  await expect(dialog).toBeVisible();
  return dialog;
}

test('tax-export canton options preserve a draft and cannot update a later dialog session', async ({ page }) => {
  await openReport(page);
  const abandoned = await holdResponse(page, '**/api/taxdata/cantons');
  try {
    const first = await openExport(page);
    await abandoned.arrived;
    await page.keyboard.press('Escape');
    await expect(first).toHaveCount(0);
    const cantons = await holdResponse(page, '**/api/taxdata/cantons');
    try {
      const dialog = await openExport(page);
      await cantons.arrived;
      await dialog.locator('input#institutionName').fill('Unsaved tax export draft');
      cantons.release();
      await expect(dialog.locator('select#canton option[value="ZH"]')).toHaveCount(1);
      await expect(dialog.locator('input#institutionName')).toHaveValue('Unsaved tax export draft');
      abandoned.release();
      await abandoned.done;
      await expect(dialog.locator('input#institutionName')).toHaveValue('Unsaved tax export draft');
      await dialog.locator('select#taxYear').selectOption('2025');
      await dialog.locator('select#canton').selectOption('ZH');
      await dialog.locator('input#clientNumber').fill('Unsaved client');
      await dialog.locator('input#institutionName').blur();
      await dialog.locator('.p-dialog-close-button').click();
      await expect(dialog).toHaveCount(0);
    } finally {
      cantons.release();
    }
  } finally {
    abandoned.release();
  }
});

test('tax-year corrections render delayed rows and refresh after saving an owned comment', async ({ page }) => {
  const report = await openReport(page);
  const group = report.securityDividendsYearGroup.find((row: any) => row.securityDividendsPositions?.length > 0);
  expect(group).toBeTruthy();
  const position = group.securityDividendsPositions[0];
  const id = position.security.idSecuritycurrency;
  const auth = await headers(page);
  const url = `/api/taxyearcorrection/security/${id}`;
  const cleanup = async () => {
    const response = await page.request.get(url, { headers: auth });
    expect(response.ok()).toBeTruthy();
    for (const correction of (await response.json()).corrections)
      if (correction.note?.startsWith(ownNote)) {
        const deleted = await page.request.delete(`/api/taxyearcorrection/${correction.idTaxYearCorrection}`, {
          headers: auth
        });
        expect(deleted.ok(), await deleted.text()).toBeTruthy();
      }
  };
  await cleanup();
  const existing = await page.request.get(url, { headers: auth });
  expect(existing.ok()).toBeTruthy();
  const used = new Set((await existing.json()).corrections.map((row: any) => row.taxYear));
  const year = report.securityDividendsYearGroup.map((row: any) => row.year).find((year: number) => !used.has(year));
  expect(year).toBeDefined();
  try {
    const created = await page.request.post('/api/taxyearcorrection', {
      headers: auth,
      data: {
        idTaxYearCorrection: null,
        idSecuritycurrency: id,
        taxYear: year,
        useTaxableAmount: false,
        taxableIncome: null,
        note: ownNote
      }
    });
    expect(created.ok(), await created.text()).toBeTruthy();
    const pending = await holdResponse(page, `**${url}`);
    try {
      const yearRow = page
        .locator('p-table')
        .first()
        .locator('tbody > tr')
        .filter({ has: page.getByRole('cell', { name: String(group.year), exact: true }) });
      await yearRow.locator('a').first().click();
      const securityRow = page
        .locator('tenant-dividends-security-extended tbody tr')
        .filter({ hasText: position.security.name })
        .first();
      await securityRow.click({ button: 'right' });
      await page
        .locator('[role="menu"]:visible')
        .getByRole('menuitem', { name: /Tax year corrections|Steuerjahr.?Korrekturen/i })
        .click();
      const dialog = page.locator('tax-year-correction-dialog .p-dialog');
      await pending.arrived;
      pending.release();
      const rows = dialog.locator('tax-year-correction-table tbody tr');
      await expect(rows.filter({ hasText: ownNote })).toBeVisible();
      const index = (await rows.allTextContents()).findIndex((text) => text.includes(ownNote));
      const row = rows.nth(index);
      await row.locator('button:has(i.pi-pencil)').click();
      await row.locator('textarea').fill(`${ownNote} updated`);
      await row.locator('button:has(i.pi-check)').click();
      await expect(dialog.locator('tbody tr', { hasText: `${ownNote} updated` })).toBeVisible();
      await expect(dialog.locator('textarea')).toHaveCount(0);
      await dialog.locator('.p-dialog-close-button').click();
      await expect(dialog).toHaveCount(0);
    } finally {
      pending.release();
    }
  } finally {
    await cleanup();
  }
});
