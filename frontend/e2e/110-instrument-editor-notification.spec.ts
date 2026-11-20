import { expect, Locator, Page, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { watchlistTable } from './watchlist.helpers';
import { holdResponse } from './response.helpers';
import { selectDynamicFormOptionByText } from './dynamic-form.helpers';
import { toShortDate } from './portfolio.helpers';

let locale: string;

const WATCHLIST = 'Instrument OnPush e2e';
const CREATE = {
  security: /^(Create\s*and\s*add\s*security|Hinzuf.*neues\s*Wertpapier)\b/i,
  currency: /(Create\s*and\s*add\s*currencypair|Hinzuf.*W.*hrungspaar)/i,
  derived: /(Add\s*new\s*derived\s*security|Hinzuf.*abgeleitetes\s*Instrument)/i
};

test.use({ viewport: { width: 1600, height: 1400 }, actionTimeout: 15_000 });

/** Only this empty watchlist is persisted. Every instrument, split and price period remains an unsaved draft. */
async function cleanup(page: Page): Promise<void> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  if (!token) return;
  const headers = { 'x-auth-token': token };
  const lists = await page.request.get('/api/watchlist/tenant', { headers });
  expect(lists.ok()).toBeTruthy();
  for (const list of await lists.json()) {
    if (list.name === WATCHLIST) {
      const deleted = await page.request.delete(`/api/watchlist/${list.idWatchlist}`, { headers });
      expect(deleted.ok(), await deleted.text()).toBeTruthy();
    }
  }
}

test.beforeEach(async ({ page }) => {
  const info = await page.request.get('/api/gtinfo');
  expect(await info.json()).toMatchObject({ databaseName: 'grafioschtrader_t', activeProfile: 'e2e' });
  locale = (await loginAsFixtureUser(page, 'alledit')).locale!;
  await cleanup(page);
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  const created = await page.request.post('/api/watchlist', {
    headers: { 'x-auth-token': token! },
    data: { name: WATCHLIST }
  });
  expect(created.ok(), await created.text()).toBeTruthy();
  await page.reload();
  await page.getByRole('treeitem', { name: WATCHLIST, exact: true }).click();
});

test.afterEach(async ({ page }) => cleanup(page));

async function openEditor(page: Page, kind: keyof typeof CREATE): Promise<Locator> {
  const table = watchlistTable(page);
  await table.click();
  await table.click({ button: 'right' });
  await page.locator('[role="menu"]:visible').getByText(CREATE[kind]).first().click();
  const selector = { security: 'security-edit', currency: 'currencypair-edit', derived: 'security-derived-edit' }[kind];
  const dialog = page.locator(`${selector} .p-dialog`).first();
  await expect(dialog).toBeVisible();
  return dialog;
}

async function closeEditor(dialog: Locator): Promise<void> {
  await dialog.locator('.p-dialog-close-button').click();
  await expect(dialog).toHaveCount(0);
}

async function enterNumber(scope: Locator, field: string, value: string): Promise<void> {
  const input = scope.locator(`#${field} input, input#${field}`).first();
  await input.fill(value);
  await input.press('Tab');
}

async function enterDate(scope: Locator, field: string, isoDate: string): Promise<void> {
  const input = scope.locator(`#${field} input, input#${field}`).first();
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(toShortDate(isoDate, locale));
  await input.press('Tab');
}

test('currency options arrive without another click and a closed opening cannot overwrite its successor', async ({
  page
}) => {
  const held = await holdResponse(page, '**/api/currencypair/feedConnectors*');
  try {
    const first = await openEditor(page, 'currency');
    await held.arrived;
    await expect(first.locator('select#fromCurrency option')).toHaveCount(0);
    const aborted = page.waitForEvent('requestfailed', {
      predicate: (request) => request.url().includes('/currencypair/feedConnectors')
    });
    await closeEditor(first);
    expect((await aborted).failure()?.errorText).toBe('net::ERR_ABORTED');

    const second = await openEditor(page, 'currency');
    await expect(second.locator('select#fromCurrency option[value="USD"]')).toHaveCount(1);
    await second.locator('select#fromCurrency').selectOption('USD');
    await expect(second.locator('select#toCurrency option')).not.toHaveCount(0);
    await second.locator('#note').fill('This opening owns the draft');
    held.release();
    await held.done;
    await expect(second.locator('#note')).toHaveValue('This opening owns the draft');
    await expect(second.locator('select#fromCurrency')).toHaveValue('USD');
    await closeEditor(second);
  } finally {
    held.release();
    await held.done;
  }
});

test('security initialization can fail, reopen and render split edits after helper data arrives', async ({ page }) => {
  await page.route(
    '**/api/stockexchange?*',
    (route) =>
      route.fulfill({
        status: 500,
        json: { message: 'Instrument helper unavailable' }
      }),
    { times: 1 }
  );
  const failed = await openEditor(page, 'security');
  await expect(page.locator('p-toast')).toContainText('Instrument helper unavailable');
  await closeEditor(failed);

  const held = await holdResponse(page, '**/api/stockexchange?*');
  try {
    const dialog = await openEditor(page, 'security');
    await held.arrived;
    held.release();
    await held.done;
    await expect(dialog.locator('select#currency option[value="EUR"]')).toHaveCount(1);
    await selectDynamicFormOptionByText(
      page,
      dialog,
      'assetClass',
      /Aktien Spanien.*(Direct investment|Direktanlage)/i
    );
    await selectDynamicFormOptionByText(page, dialog, 'stockexchange', /Madrid/);
    await dialog.locator('p-tab[value="splits"]').click();
    const panel = dialog.locator('p-tabpanel[value="splits"]');
    await enterDate(panel, 'splitDate', '2020-02-01');
    await enterNumber(panel, 'fromFactor', '1');
    await enterNumber(panel, 'toFactor', '5');
    await panel.locator('button[type="submit"]').click();
    const table = panel.locator('securitysplit-edit-table');
    await expect(table.locator('th').first()).toContainText(/Split/);
    const row = table.locator('tbody tr');
    await expect(row).toHaveCount(1);
    await expect(row).toContainText('5');
    await row.click();
    await table.getByRole('button', { name: /^(Edit|Bearbeiten)$/ }).click();
    await enterNumber(panel, 'toFactor', '10');
    await panel.locator('button[type="submit"]').click();
    await expect(row).toHaveCount(1);
    await expect(row).toContainText('10');
    await table.getByRole('button', { name: /^(Delete|L.schen)$/ }).click();
    await expect(row).toHaveCount(0);
    await closeEditor(dialog);
  } finally {
    held.release();
    await held.done;
  }
});

test('period rows render after parent method calls and receive a late instrument limit', async ({ page }) => {
  const held = await holdResponse(page, '**/api/gt_globalparameters/maxinstrumentlimits', 'GET', {
    maxInstrumentSplits: 2,
    maxInstrumentHistoryquotePeriods: 2
  });
  try {
    const dialog = await openEditor(page, 'security');
    await held.arrived;
    await expect(dialog.locator('select#currency option[value="CHF"]')).toHaveCount(1);
    await selectDynamicFormOptionByText(page, dialog, 'assetClass', /Festgeld/i);
    await selectDynamicFormOptionByText(page, dialog, 'stockexchange', /Private Papers/);
    await dialog.locator('p-tab[value="periods"]').click();
    const panel = dialog.locator('p-tabpanel[value="periods"]');
    const table = panel.locator('security-historyquote-period-edit-table');
    await expect(table).toContainText(/max\w*\s+20\b/i);
    held.release();
    await held.done;
    await expect(table).toContainText(/max\w*\s+2\b/i);
    await enterDate(panel, 'fromDate', '2020-01-01');
    await enterNumber(panel, 'price', '100');
    await panel.locator('button[type="submit"]').click();
    const row = table.locator('tbody tr');
    await expect(row).toHaveCount(1);
    await expect(row).toContainText('100');
    await row.click();
    await table.getByRole('button', { name: /^(Edit|Bearbeiten)$/ }).click();
    await enterNumber(panel, 'price', '105');
    await panel.locator('button[type="submit"]').click();
    await expect(row).toHaveCount(1);
    await expect(row).toContainText('105');
    await table.getByRole('button', { name: /^(Delete|L.schen)$/ }).click();
    await expect(row).toHaveCount(0);
    await closeEditor(dialog);
  } finally {
    held.release();
    await held.done;
  }
});

test('bond terms appear after the schema arrives and retain a manual coupon across input changes', async ({ page }) => {
  const held = await holdResponse(page, '**/api/globalparameters/formdefinition/SecurityBondTerms*');
  try {
    const dialog = await openEditor(page, 'security');
    await expect(dialog.locator('select#currency option[value="EUR"]')).toHaveCount(1);
    await dialog.locator('#name').fill('1.25% Notification bond');
    await dialog.locator('select#currency').selectOption('EUR');
    await selectDynamicFormOptionByText(
      page,
      dialog,
      'assetClass',
      /Anleihen Österreich.*(Direct investment|Direktanlage)/i
    );
    await held.arrived;
    await dialog.locator('p-tab[value="bondTerms"]').click();
    await expect(dialog.locator('security-bond-terms dynamic-form')).toHaveCount(0);
    held.release();
    await held.done;
    const terms = dialog.locator('security-bond-terms');
    const coupon = terms.locator('#couponRate input, input#couponRate').first();
    await expect(coupon).toHaveValue(/1[,.]25/);
    await enterNumber(terms, 'couponRate', '2');
    await dialog.locator('p-tab[value="security"]').click();
    await dialog.locator('#name').fill('3% Another name');
    await dialog.locator('select#currency').selectOption('USD');
    await dialog.locator('p-tab[value="bondTerms"]').click();
    await expect(coupon).toHaveValue('2');
    await closeEditor(dialog);
  } finally {
    held.release();
    await held.done;
  }
});

test('derived formula controls rebuild repeatedly and helper loading ends with the opening', async ({ page }) => {
  const held = await holdResponse(page, '**/api/stockexchange?*');
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  try {
    const first = await openEditor(page, 'derived');
    await held.arrived;
    const aborted = page.waitForEvent('requestfailed', {
      predicate: (request) => request.url().includes('/stockexchange?')
    });
    await closeEditor(first);
    expect((await aborted).failure()?.errorText).toBe('net::ERR_ABORTED');
    const dialog = await openEditor(page, 'derived');
    await expect(dialog.locator('select#currency option[value="EUR"]')).toHaveCount(1);
    await dialog.locator('#name').fill('Keep this draft');
    await expect(dialog.locator('#name')).toHaveValue('Keep this draft');
    const formula = dialog.locator('textarea#formulaPrices');
    await formula.fill('o+p+p');
    await expect(dialog.locator('#additionalInstrumentName_p')).toHaveCount(1);
    await expect(dialog.locator('#name')).toHaveValue('Keep this draft');
    await formula.fill('o+q');
    await expect(dialog.locator('#additionalInstrumentName_p')).toHaveCount(0);
    await expect(dialog.locator('#additionalInstrumentName_q')).toHaveCount(1);
    await expect(dialog.locator('#name')).toHaveValue('Keep this draft');
    await formula.fill('');
    await expect(dialog.locator('#additionalInstrumentName_q')).toHaveCount(0);
    await expect(dialog.locator('#name')).toHaveValue('Keep this draft');
    await formula.fill('o+p');
    await expect(dialog.locator('#additionalInstrumentName_p')).toHaveCount(1);
    await expect(dialog.locator('#name')).toHaveValue('Keep this draft');
    held.release();
    await held.done;
    await expect(formula).toHaveValue('o+p');
    await closeEditor(dialog);
    expect(errors).toEqual([]);
  } finally {
    held.release();
    await held.done;
  }
});
