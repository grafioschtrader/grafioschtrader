import { expect, test, Page, APIResponse } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { loginAsFixtureUser } from './helpers';
import { watchlistTable } from './watchlist.helpers';
import { pickMultiSelect, setCheckbox } from './generic-connector.helpers';

const fixture = JSON.parse(
  readFileSync(
    resolve(__dirname, '../../backend/grafioschtrader-server/src/test/resources/testdata/watchlist-indicators.json'),
    'utf8'
  )
);
const base = '/api/watchlistindicatorcolumn';
async function ok(response: APIResponse): Promise<any> {
  expect(response.ok(), await response.text()).toBeTruthy();
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
async function headers(page: Page) {
  return { 'x-auth-token': await page.evaluate(() => sessionStorage.getItem('jwt')), Accept: 'application/json' };
}
async function cleanup(page: Page): Promise<void> {
  const auth = await headers(page);
  for (const column of await ok(await page.request.get(base, { headers: auth }))) {
    if (column.label.startsWith('Indicator E2E '))
      await ok(await page.request.delete(`${base}/${column.idWatchlistIndicatorColumn}`, { headers: auth }));
  }
  for (const list of await ok(await page.request.get('/api/watchlist/tenant', { headers: auth }))) {
    if (list.name !== fixture.watchlist) continue;
    const report = await ok(await page.request.get(`/api/watchlist/${list.idWatchlist}/indicator`, { headers: auth }));
    for (const position of report.currencypairPositionList)
      await ok(
        await page.request.delete(
          `/api/watchlist/${list.idWatchlist}/removeCurrencypair/${position.securitycurrency.idSecuritycurrency}`,
          { headers: auth }
        )
      );
    await ok(await page.request.delete(`/api/watchlist/${list.idWatchlist}`, { headers: auth }));
  }
}
async function editor(page: Page) {
  await watchlistTable(page).click();
  await watchlistTable(page).click({ button: 'right' });
  await page.getByRole('menuitem', { name: /Edit indicator columns|Indikatorspalten bearbeiten/ }).click();
  return page.getByRole('dialog', { name: /Edit indicator columns|Indikatorspalten bearbeiten/ });
}

test('indicator editor persists columns for selected watchlists, table and expanded-row display, missing data', async ({
  page
}) => {
  test.setTimeout(120_000);
  page.setDefaultTimeout(15_000);
  for (const url of ['/api/gtinfo', `${process.env.E2E_BACKEND_URL ?? 'http://localhost:8080'}/api/gtinfo`]) {
    const info = await ok(await page.request.get(url));
    expect(info.databaseName).toBe('grafioschtrader_t');
    expect(info.activeProfile.split(',')).toContain('e2e');
  }
  await loginAsFixtureUser(page, fixture.nickname);
  await cleanup(page);
  const auth = await headers(page);
  const list = await ok(
    await page.request.post('/api/watchlist', { headers: auth, data: { name: fixture.watchlist } })
  );
  const pairs = await ok(await page.request.get('/api/currencypair', { headers: auth }));
  const selected = fixture.currencies.map((c) =>
    pairs.find((p) => p.fromCurrency === c.fromCurrency && p.toCurrency === c.toCurrency)
  );
  expect(selected.every(Boolean)).toBeTruthy();
  await ok(
    await page.request.put(`/api/watchlist/${list.idWatchlist}/addSecuritycurrency`, {
      headers: auth,
      data: { securityList: [], currencypairList: selected }
    })
  );
  await page.reload();
  await page.getByRole('treeitem', { name: fixture.watchlist, exact: true }).click();
  await page.getByRole('tab', { name: /^(Indicators|Indikatoren)$/ }).click();
  await expect(page).toHaveURL(/watchlistindicator/);
  const table = watchlistTable(page);
  for (const column of fixture.columns) {
    const manage = await editor(page);
    await manage.getByRole('button', { name: /Create column|Spalte erstellen/ }).click();
    const form = page.getByRole('dialog', { name: /^(Indicator column|Indikatorspalte)$/ });
    // The placeholder is set when the dialog initializes, after which the expression is watched for its result type.
    await expect(form.locator('p-multiselect#idWatchlists')).toContainText(/All watchlists|Alle Beobachtungslisten/);
    await form.locator('#label').fill(column.label);
    const typed = page.waitForResponse(
      (r) => r.url().endsWith('/resulttype') && r.request().postData() === column.expression
    );
    await form.locator('#expression').fill(column.expression);
    await typed;
    await pickMultiSelect(page, form, 'idWatchlists', [fixture.watchlist]);
    if (column.showInTable === false) await setCheckbox(form, 'showInTable', false);
    if (column.showInExpansion) await setCheckbox(form, 'showInExpansion', true);
    if (column.expression.includes('>')) await expect(form.locator('#decimals')).toBeHidden();
    else {
      await form.locator('#decimals').fill('2');
      await form.locator('select#markColors').selectOption('NEUTRAL');
    }
    const saved = page.waitForResponse((r) => new URL(r.url()).pathname === base && r.request().method() === 'POST');
    await form.locator('button[type="submit"]').click();
    const response = await saved;
    expect(response.ok(), await response.text()).toBeTruthy();
    await expect(form).toHaveCount(0);
    await expect(manage.getByRole('cell', { name: column.label, exact: true })).toBeVisible();
    await manage.getByRole('button', { name: /close|schlie/i }).click();
    const header = table.getByRole('columnheader', { name: column.label, exact: true });
    if (column.showInTable === false) await expect(header).toHaveCount(0);
    else await expect(header).toBeVisible();
  }
  await expect(table.locator('tbody tr')).toHaveCount(2);
  const report = await ok(await page.request.get(`/api/watchlist/${list.idWatchlist}/indicator`, { headers: auth }));
  const zero = report.columns.find((c) => c.label === fixture.columns[0].label);
  const missing = report.columns.find((c) => c.label === fixture.columns[2].label);
  for (const row of Object.values(report.cells) as any[]) {
    expect(row[zero.idWatchlistIndicatorColumn]).toMatchObject({ value: 0, status: 'OK' });
    expect(row[missing.idWatchlistIndicatorColumn]).toMatchObject({ value: null, status: 'NOT_APPLICABLE' });
  }
  await expect(
    table
      .locator('tbody tr')
      .first()
      .getByRole('cell', { name: /^0([.,]0+)?$/ })
  ).toBeVisible();
  // The missing-data column is shown in the expanded row only, grouped as a fundamental indicator.
  expect(report.columnCategories[missing.idWatchlistIndicatorColumn]).toBe('FUNDAMENTAL');
  await expect(table.locator('tbody .fa-info-circle')).toHaveCount(0);
  await table.locator('tbody tr a .fa-chevron-circle-right').first().click();
  const expanded = table.locator('watchlist-indicator-expanded');
  const fundamental = expanded.locator('fieldset', { hasText: /Fundamental indicators|Fundamentale Indikatoren/ });
  await expect(fundamental).toContainText(fixture.columns[2].label);
  await expect(fundamental.locator('.fa-info-circle')).toHaveCount(1);
  await expect(expanded.locator('fieldset')).toHaveCount(1);
  await table.getByRole('columnheader', { name: fixture.columns[0].label, exact: true }).click();
  await page.reload();
  await page.getByRole('tab', { name: /^(Indicators|Indikatoren)$/ }).click();
  await expect(table.getByRole('columnheader', { name: fixture.columns[0].label, exact: true })).toBeVisible();
  const manage = await editor(page);
  await manage
    .getByRole('row')
    .filter({ hasText: fixture.columns[0].label })
    .getByRole('button', { name: /^(Edit|Bearbeiten)$/ })
    .click();
  const form = page.getByRole('dialog', { name: /^(Indicator column|Indikatorspalte)$/ });
  await expect(form.locator('#expression')).toHaveValue('0');
  await expect(form.locator('p-multiselect#idWatchlists')).toContainText(fixture.watchlist);
  await form.locator('form-input-button').getByRole('button').click();
  const search = page.locator('securitycurrency-search-and-set');
  await expect(search.locator('select#idStockexchange option').last()).toBeAttached();
  await search.locator('select#assetclassType').selectOption('CURRENCY_PAIR');
  await search.locator('select#currency').selectOption('USD');
  await search.locator('button[type="submit"]').click();
  await search.locator('tbody tr').filter({ hasText: 'USD/CHF' }).click();
  await search.getByRole('button', { name: /Assign selected|Selektierte zuweisen/ }).click();
  await expect(form.locator('#benchmarkName')).toHaveValue('USD/CHF');
  await form.locator('#lowerMark').fill('-1');
  await form.locator('#upperMark').fill('1');
  const updated = page.waitForResponse((r) => new URL(r.url()).pathname === base && r.request().method() === 'PUT');
  await form.locator('button[type="submit"]').click();
  expect((await updated).ok()).toBeTruthy();
  await expect(form).toHaveCount(0);
  await manage
    .getByRole('row')
    .filter({ hasText: fixture.columns[0].label })
    .getByRole('button', { name: /^(Edit|Bearbeiten)$/ })
    .click();
  await expect(form.locator('#benchmarkName')).toHaveValue('USD/CHF');
  await expect(form.locator('#lowerMark')).toHaveValue('-1');
  await form.getByRole('button', { name: /Clear benchmark|Vergleichsinstrument entfernen/ }).click();
  await expect(form.locator('#benchmarkName')).toBeEmpty();
  await form.locator('button[type="submit"]').click();
  await expect(form).toHaveCount(0);
  const refreshed = page.waitForResponse((r) => r.url().endsWith(`/watchlist/${list.idWatchlist}/indicator`));
  await manage.getByRole('button', { name: /close|schlie/i }).click();
  await refreshed;
  await expect(manage).toBeHidden();
  await expect(table.getByRole('columnheader', { name: fixture.columns[0].label, exact: true })).toBeVisible();
  await expect(table.locator('.p-datatable-mask')).toBeHidden();
  await page.screenshot({ path: 'test-results/indicator-tab.png', fullPage: true });
  await cleanup(page);
});
