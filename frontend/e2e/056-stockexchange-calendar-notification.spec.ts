import { expect, Locator, Page, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { holdCalendarResponse } from './calendar.helpers';
import { loginAsFixtureUser } from './helpers';

// Runs after 055 has populated the global trading days; no calendar data is saved here.
const views = { exchange: /^\s*(Stock\s*exchange|Handelsplatz)\s*$/i };
test.beforeEach(async ({ page }) => {
  page.setDefaultTimeout(15_000);
  await loginAsFixtureUser(page, 'alledit');
});

async function openView(page: Page, label: RegExp): Promise<Locator> {
  const node = page.getByRole('treeitem', { name: label }).locator(':scope > .p-tree-node-content').first();
  if (!(await node.isVisible())) {
    await page
      .locator('.p-tree-node-content')
      .filter({ hasText: /Base Data|Basisdaten/ })
      .first()
      .dblclick();
  }
  await node.click();
  const table = page.locator('configurable-table').first();
  await expect(table.locator('tbody tr').first()).toBeVisible();
  return table;
}

test('expanded exchange calendar publishes delayed data and keeps its own active menu', async ({ page }) => {
  const table = await openView(page, views.exchange);
  const row = table.locator('tbody tr').filter({ has: page.getByRole('cell', { name: 'XSWX', exact: true }) });
  const data = await holdCalendarResponse(page, '**/api/tradingdaysminus/*/*');
  try {
    await row.locator('a[href="#"]').click();
    const calendar = page.locator('trading-calendar-stockexchange');
    await data.arrived;
    data.release();
    await expect(calendar.locator('p-select#idYearSelect')).toContainText(String(new Date().getFullYear()));
    await expect(calendar.locator('month-calendar')).toHaveCount(12);
    await calendar.locator('label[for="idYearSelect"]').click();
    await page
      .locator('p-menubar')
      .getByRole('menuitem', { name: /^(View|Ansicht)$/ })
      .click();
    await page
      .locator('p-menubar')
      .getByRole('menuitem', { name: /underline|unterstr/i })
      .click();
    await expect(calendar.locator('.day[style*="border-bottom"]').first()).toBeVisible();
    const mainEdit = page
      .locator('p-menubar')
      .getByRole('menuitem', { name: /^(Edit|Bearbeiten)$/ })
      .first();
    await mainEdit.click();
    await expect(
      page.locator('p-menubar').getByRole('menuitem', { name: /calendar.*exchange|Kalender.*Handelsplatz/i })
    ).not.toHaveCount(0);
    await expect(page.locator('p-menubar').getByRole('menuitem', { name: /^(Create|Erstellen)\s/ })).toHaveCount(0);
    await page.keyboard.press('Escape');
    await table.locator('h4').click();
    await expect(calendar.locator('.data-container').first()).toHaveClass(/passiv-border/);
    await row.locator('a[href="#"]').click();
    await expect(calendar).toHaveCount(0);
  } finally {
    data.release();
  }
});

async function selectCalendarYear(page: Page, calendar: Locator, year: number): Promise<void> {
  await calendar.locator('p-select#idYearSelect').click();
  await page.getByRole('option', { name: String(year), exact: true }).click();
}

async function dayStyles(calendar: Locator): Promise<string[]> {
  return calendar.locator('.day').evaluateAll((days) => days.map((day) => (day as HTMLElement).style.cssText));
}

test('exchange calendar ignores an obsolete year response and cancels loading when collapsed', async ({ page }) => {
  const table = await openView(page, views.exchange);
  const row = table.locator('tbody tr').filter({ has: page.getByRole('cell', { name: 'XSWX', exact: true }) });
  await row.locator('a[href="#"]').click();
  const calendar = page.locator('trading-calendar-stockexchange');
  await expect(calendar.locator('p-select#idYearSelect')).toContainText(String(new Date().getFullYear()));
  const oldYear = await holdCalendarResponse(page, '**/api/tradingdaysplus/2024');
  try {
    await selectCalendarYear(page, calendar, 2024);
    await oldYear.arrived;
    const currentResponse = page.waitForResponse(
      (response) => response.url().includes('/api/tradingdaysminus/') && response.url().endsWith('/2023')
    );
    await calendar
      .locator('p-footer button')
      .filter({ hasText: /^\s*2023\s*$/ })
      .click();
    expect((await currentResponse).ok()).toBeTruthy();
    await expect(calendar.locator('.day')).toHaveCount(365);
    await expect(calendar.locator('.day[style*="lime"]').first()).toBeVisible();
    const current = await dayStyles(calendar);
    oldYear.release();
    await oldYear.done;
    await expect.poll(() => dayStyles(calendar)).toEqual(current);
    await expect(calendar.locator('p-select#idYearSelect')).toContainText('2023');
  } finally {
    oldYear.release();
  }
  const closing = await holdCalendarResponse(page, '**/api/tradingdaysminus/*/2024');
  try {
    await selectCalendarYear(page, calendar, 2024);
    await closing.arrived;
    await row.locator('a[href="#"]').click();
    await expect(calendar).toHaveCount(0);
    closing.release();
    await closing.done;
    await row.locator('a[href="#"]').click();
    await expect(calendar.locator('p-select#idYearSelect')).toContainText(String(new Date().getFullYear()));
    await expect(calendar.locator('.day[style*="lime"]').first()).toBeVisible();
  } finally {
    closing.release();
  }
});

test('calendar copy dialog refreshes its OnPush parent for year and full-calendar copies', async ({ page }) => {
  const names = ['E2E calendar copy source', 'E2E calendar copy target'];
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  const headers = { 'x-auth-token': token!, Accept: 'application/json' };
  const rows = await page.request.get('/api/stockexchange?includeNameOfCalendarIndex=false', { headers });
  expect(rows.ok()).toBeTruthy();
  for (const exchange of await rows.json()) {
    if (names.includes(exchange.name)) {
      const deleted = await page.request.delete(`/api/stockexchange/${exchange.idStockexchange}`, { headers });
      expect(deleted.ok()).toBeTruthy();
    }
  }
  const ids: number[] = [];
  try {
    for (const name of names) {
      const created = await page.request.post('/api/stockexchange', {
        headers,
        data: {
          name,
          countryCode: 'CH',
          timeOpen: '09:00:00',
          timeClose: '17:00:00',
          timeZone: 'Europe/Zurich',
          secondaryMarket: false,
          noMarketValue: false
        }
      });
      expect(created.ok(), await created.text()).toBeTruthy();
      ids.push((await created.json()).idStockexchange);
    }
    for (const year of [2025, 2026]) {
      const seeded = await page.request.put(`/api/tradingdaysminus/${ids[0]}`, {
        headers,
        data: {
          year,
          addRemoveDays: [{ date: `${year}-01-02`, add: true }]
        }
      });
      expect(seeded.ok(), await seeded.text()).toBeTruthy();
    }
    const table = await openView(page, views.exchange);
    const row = table.locator('tbody tr').filter({ has: page.getByRole('cell', { name: names[1], exact: true }) });
    await row.locator('a[href="#"]').click();
    const calendar = page.locator('trading-calendar-stockexchange');
    await expect(calendar.locator('p-select#idYearSelect')).toContainText(String(new Date().getFullYear()));
    if (new Date().getFullYear() !== 2026) {
      await selectCalendarYear(page, calendar, 2026);
    }
    for (const fullCopy of [false, true]) {
      await calendar.locator('label[for="idYearSelect"]').click({ button: 'right' });
      const menu = page.locator('[role="menu"]:visible');
      await menu
        .getByRole('menuitem')
        .nth(fullCopy ? 0 : 1)
        .click();
      const dialog = page.locator('p-dynamicdialog [role="dialog"]');
      await expect(dialog).toBeVisible();
      await expect(dialog.locator('input#copyYearOrFull')).toHaveValue(fullCopy ? '2000 - 2030' : '2026');
      await dialog.locator('select#name').selectOption(String(ids[0]));
      const reload = page.waitForResponse(
        (response) =>
          response.url().endsWith(`/api/tradingdaysminus/${ids[1]}/2026`) && response.request().method() === 'GET'
      );
      await dialog.locator('button[type="submit"]').click();
      expect((await reload).ok()).toBeTruthy();
      await expect(dialog).toHaveCount(0);
      await expect(calendar.locator('.day[style*="blue"]')).toHaveCount(1);
      const previousYear = await page.request.get(`/api/tradingdaysminus/${ids[1]}/2025`, { headers });
      expect((await previousYear.json()).dates).toEqual(fullCopy ? ['2025-01-02'] : []);
    }
    const openCopy = async () => {
      await calendar.locator('label[for="idYearSelect"]').click({ button: 'right' });
      await page.locator('[role="menu"]:visible').getByRole('menuitem').first().click();
      const dialog = page.locator('p-dynamicdialog [role="dialog"]');
      await expect(dialog.locator('select#name')).toBeVisible();
      return dialog;
    };
    const dialog = await openCopy();
    await dialog.locator('select#name').selectOption(String(ids[0]));
    // A transport failure must re-enable the form and preserve the selected source for retry.
    await page.route(
      '**/api/tradingdaysminus/copytradingdays',
      (route) =>
        route.fulfill({
          status: 503,
          contentType: 'text/plain',
          body: 'Calendar copy temporarily unavailable'
        }),
      { times: 1 }
    );
    await dialog.locator('button[type="submit"]').click();
    await expect(page.getByText('Calendar copy temporarily unavailable', { exact: true })).toBeVisible();
    await expect(dialog.locator('button[type="submit"]')).toBeEnabled();
    await expect(dialog.locator('select#name')).toHaveValue(String(ids[0]));
    const pending = await holdCalendarResponse(page, '**/api/tradingdaysminus/copytradingdays', 'PUT');
    try {
      await dialog.locator('button[type="submit"]').click();
      await pending.arrived;
      await page.keyboard.press('Escape');
      await expect(dialog).toHaveCount(0);
      const reopened = await openCopy();
      pending.release();
      await pending.done;
      await expect(reopened).toBeVisible();
      await reopened.locator('select#name').selectOption(String(ids[0]));
      await expect(reopened.locator('button[type="submit"]')).toBeEnabled();
      await reopened.locator('button.p-dialog-close-button').click();
      await expect(reopened).toHaveCount(0);
    } finally {
      pending.release();
    }
  } finally {
    for (const id of ids.reverse()) {
      const deleted = await page.request.delete(`/api/stockexchange/${id}`, { headers });
      expect(deleted.ok(), await deleted.text()).toBeTruthy();
    }
  }
});

async function openMissingQuoteCalendar(page: Page): Promise<Locator> {
  await page
    .getByRole('treeitem', { name: /^Portfolios-/ })
    .locator(':scope > .p-tree-node-content')
    .click();
  await page.getByRole('tab', { name: /^(Performance|Periodenertrag)$/ }).click();
  await page.getByRole('tab', { name: /Missing.*(EOD|quotes)|Fehlende Tagesendkurse/i }).click();
  return page.locator('ng-fullyearcalendar-lib');
}

/** Keep the UI cases independent of transactions added later in the roundtrip and of downloaded quotes. */
async function holdMissingQuoteResponses(page: Page, scenario: 'emptyTenant' | 'completeQuotes') {
  const fixtures = JSON.parse(
    fs.readFileSync(
      path.resolve(
        __dirname,
        '../../backend/grafioschtrader-server/src/test/resources/testdata/missing-quote-calendar.json'
      ),
      'utf8'
    )
  );
  let release!: () => void;
  let arrived!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const requestArrived = new Promise<void>((resolve) => (arrived = resolve));
  await page.route('**/api/holding/missingquotes/*', async (route) => {
    const year = Number(new URL(route.request().url()).pathname.split('/').pop());
    arrived();
    await gate;
    await route.fulfill({ json: { ...fixtures[scenario], year } });
  });
  return { release, arrived: requestArrived };
}

test('missing-quote calendar shows the current year for a tenant without holdings', async ({ page }) => {
  const currentYear = new Date().getFullYear();
  const data = await holdMissingQuoteResponses(page, 'emptyTenant');
  try {
    const calendar = await openMissingQuoteCalendar(page);
    await data.arrived;
    data.release();
    await expect(calendar.locator('month-calendar')).toHaveCount(12);
    const yearSelect = page.locator('p-select#idYearSelect');
    await expect(yearSelect).toContainText(String(currentYear));
    await yearSelect.click();
    await expect(page.getByRole('option')).toHaveCount(1);
    await expect(page.getByRole('option', { name: String(currentYear), exact: true })).toBeVisible();
  } finally {
    data.release();
  }
});

test('missing-quote calendar still loads and changes years with the shared signal navigation', async ({ page }) => {
  const currentYear = new Date().getFullYear();
  const data = await holdMissingQuoteResponses(page, 'completeQuotes');
  try {
    const calendar = await openMissingQuoteCalendar(page);
    await data.arrived;
    data.release();
    await expect(calendar.locator('month-calendar')).toHaveCount(12);
    await expect(page.locator('p-select#idYearSelect')).toContainText(String(currentYear));
    const response = page.waitForResponse((response) =>
      response.url().endsWith(`/api/holding/missingquotes/${currentYear - 1}`)
    );
    await page.locator('p-select#idYearSelect').click();
    await page.getByRole('option', { name: String(currentYear - 1), exact: true }).click();
    expect((await response).ok()).toBeTruthy();
    await expect(page.locator('p-select#idYearSelect')).toContainText(String(currentYear - 1));
    await expect(calendar.locator('month-calendar')).toHaveCount(12);
  } finally {
    data.release();
  }
});
