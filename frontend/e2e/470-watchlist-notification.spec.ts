import { expect, Page, Route, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { ensureWatchlist, watchlistTable } from './watchlist.helpers';

const NAME = 'Watchlist OnPush e2e';

async function auth(page: Page) {
  return { 'x-auth-token': (await page.evaluate(() => sessionStorage.getItem('jwt')))!, Accept: 'application/json' };
}

/** Remove only this spec's watchlist and its associations; shared instruments remain untouched. */
async function cleanup(page: Page) {
  const headers = await auth(page);
  const response = await page.request.get('/api/watchlist/tenant', { headers });
  expect(response.ok()).toBeTruthy();
  for (const list of await response.json()) {
    if (list.name !== NAME) continue;
    const report = await page.request.get(`/api/watchlist/${list.idWatchlist}`, { headers });
    expect(report.ok()).toBeTruthy();
    const group = await report.json();
    for (const [kind, rows] of [
      ['Security', group.securityPositionList],
      ['Currencypair', group.currencypairPositionList]
    ] as const) {
      for (const row of rows) {
        const removed = await page.request.delete(
          `/api/watchlist/${list.idWatchlist}/remove${kind}/${row.securitycurrency.idSecuritycurrency}`,
          { headers }
        );
        expect(removed.ok()).toBeTruthy();
      }
    }
    expect((await page.request.delete(`/api/watchlist/${list.idWatchlist}`, { headers })).ok()).toBeTruthy();
  }
}

/** Hold a real response, then release it without a browser event inside the view being checked. */
async function hold(page: Page, pattern: RegExp, modify: (body: any) => any = (body) => body) {
  let release!: () => void;
  let arrived = false;
  const gate = new Promise<void>((resolve) => (release = resolve));
  await page.route(pattern, async (route: Route) => {
    const response = await route.fetch();
    expect(response.ok()).toBeTruthy();
    const body = modify(await response.json());
    arrived = true;
    await gate;
    await route.fulfill({ response, json: body });
  });
  return { release, arrived: () => expect.poll(() => arrived, { timeout: 15_000 }).toBe(true) };
}

async function menu(page: Page, type: 'show' | 'edit', item: RegExp) {
  await page
    .locator('p-menubar')
    .getByRole('menuitem', { name: type === 'show' ? /^(Show|Ansicht)$/ : /^(Edit|Bearbeiten)$/ })
    .click();
  await page.getByRole('menuitem', { name: item }).click();
}

test.use({ viewport: { width: 1800, height: 1200 } });

test('watchlist forms, delayed reads, persisted filters and expanded feed tables notify their views', async ({
  page
}) => {
  test.setTimeout(120_000);
  page.setDefaultTimeout(15_000);
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  const info = await page.request.get('/api/gtinfo');
  expect(await info.json()).toMatchObject({ databaseName: 'grafioschtrader_t', activeProfile: 'e2e' });
  await loginAsFixtureUser(page, 'alledit');
  await cleanup(page);
  await page.reload();
  await ensureWatchlist(page, {
    loginNickname: 'alledit',
    name: NAME,
    main: false,
    delete: true,
    securities: [],
    currencyPairs: [],
    e2e: 'e'
  });
  await page.getByRole('treeitem', { name: NAME, exact: true }).click();
  const table = watchlistTable(page);
  await expect(table.locator('h4')).toContainText(NAME);

  // The menu bar is outside the OnPush watchlist. It must still mount the search form.
  await menu(page, 'edit', /Add existing instrument|Bestehendes Instrument hinzufügen/);
  const search = page.locator('watchlist-add-instrument');
  await expect(search.locator('select#idStockexchange option')).not.toHaveCount(0);
  const heldSearch = await hold(page, /\/api\/watchlist\/\d+\/search\?/);
  await search.locator('#isin').fill('ES0113900J37');
  await search.locator('button[type="submit"]').click();
  await heldSearch.arrived();
  await expect(search.locator('tbody tr')).toHaveCount(0);
  heldSearch.release();
  const result = search.locator('tbody tr').filter({ hasText: 'ES0113900J37' }).filter({ hasText: 'EUR' });
  await expect(result).toHaveCount(1);
  await result.locator('p-tablecheckbox').click();
  const prices = await hold(page, /\/api\/watchlist\/\d+\/quote\?/, (group) => {
    for (const position of group.securityPositionList) {
      position.ytdChangePercentage = 12.34;
      position.units = 7;
      position.valueSecurity = 7654;
    }
    return group;
  });
  await search.getByRole('button', { name: /^(Add|Hinzufügen)$/ }).click();
  await prices.arrived();
  await search.getByRole('button', { name: /^(Close|Beenden)$/ }).click();
  prices.release();
  await expect(table.locator('tbody tr').filter({ hasText: 'Banco Santander' })).toContainText(/12[.,]34/);
  await table.locator('tbody tr').filter({ hasText: 'Banco Santander' }).click();
  await menu(page, 'show', /Position in header|Position in Kopfzeile|Position.*Kopf/);
  await expect(table.locator('.position-header')).toContainText('7');

  await menu(page, 'show', /Filter.*(on|off)|Filter.*(ein|aus)/i);
  await table.locator('p-columnfilter').first().getByRole('button').click();
  const nameFilter = page.locator('.p-datatable-filter-overlay input:visible').first();
  await expect(nameFilter).toBeVisible();
  await nameFilter.fill('No such instrument e2e');
  await page
    .locator('.p-datatable-filter-overlay')
    .getByRole('button', { name: /Apply|Anwenden/ })
    .click();
  await expect(table.locator('tbody tr').filter({ hasText: 'Banco Santander' })).toHaveCount(0);
  await table.locator('p-columnfilter').first().getByRole('button').click();
  await nameFilter.fill('Banco');
  await page
    .locator('.p-datatable-filter-overlay')
    .getByRole('button', { name: /Apply|Anwenden/ })
    .click();
  await expect(table.locator('tbody tr').filter({ hasText: 'Banco Santander' })).toHaveCount(1);

  await page.getByRole('tab', { name: /Price feed|Kurs Datenfeed/ }).click();
  await expect(table.locator('tbody tr').filter({ hasText: 'Banco Santander' })).toHaveCount(1);
  await menu(page, 'show', /Filter.*(on|off)|Filter.*(ein|aus)/i);
  await table.locator('p-columnfilter').first().getByRole('button').click();
  await expect(nameFilter).toHaveValue('Banco');
  await page.keyboard.press('Escape');
  await menu(page, 'show', /Filter.*sort.*settings|Einstellungen für Filter und Sortierung/i);
  const settings = page.locator('watchlist-filter-sort-settings-dialog');
  await expect(settings).toContainText('Banco');
  await settings
    .getByRole('button', { name: /^(Clear filters of this watchlist|Filter dieser Watchlist löschen)$/ })
    .click();
  await expect(settings).not.toContainText('Banco');
  await page
    .getByRole('dialog')
    .filter({ has: settings })
    .getByRole('button', { name: /close|schlie/i })
    .click();

  await table.locator('tbody tr a .fa-chevron-circle-right').first().click();
  const expanded = page.locator('watchlist-price-feed-expanded');
  await expanded.locator('p-accordion-header').first().click();
  await expect(expanded.locator('securitycurrency-extended-info')).toContainText('Banco Santander');

  const udf = await hold(page, /\/api\/watchlist\/\d+\/udf$/);
  await page.getByRole('tab', { name: /^(UDF|Zusatzfeld)$/ }).click();
  await udf.arrived();
  udf.release();
  await expect(table.locator('tbody tr').filter({ hasText: 'Banco Santander' })).toHaveCount(1);
  await table.locator('tbody tr a .fa-chevron-circle-right').first().click();
  await expect(page.locator('securitycurrency-udf')).toContainText('Banco Santander');

  const dividends = await hold(page, /\/api\/dividend\/\d+$/, () => [
    {
      exDate: '2024-05-02',
      payDate: '2024-05-08',
      amount: 1.23,
      amountAdjusted: 1.23,
      currency: 'EUR',
      createType: 'CONNECTOR_CREATED',
      idDividend: 1
    }
  ]);
  const splits = await hold(page, /\/api\/securitysplit\/\d+\/security$/, () => [
    { splitDate: '2024-06-03', fromFactor: 1, toFactor: 3, createType: 'CONNECTOR_CREATED', idSecuritysplit: 1 }
  ]);
  await page.getByRole('tab', { name: /Dividend.*Split/ }).click();
  await expect(table.locator('tbody tr').filter({ hasText: 'Banco Santander' })).toHaveCount(1);
  await table.locator('tbody tr a .fa-chevron-circle-right').first().click();
  await Promise.all([dividends.arrived(), splits.arrived()]);
  dividends.release();
  splits.release();
  await expect(page.locator('watchlist-dividend-table tbody tr')).toContainText(/1[.,]23/);
  await expect(page.locator('watchlist-securitysplit-table tbody tr')).toContainText('3');
  expect(errors).toEqual([]);
  await cleanup(page);
});

test('the price-problem form opens from the menu bar and validates its selection', async ({ page }) => {
  page.setDefaultTimeout(15_000);
  const info = await page.request.get('/api/gtinfo');
  expect(await info.json()).toMatchObject({ databaseName: 'grafioschtrader_t', activeProfile: 'e2e' });
  await loginAsFixtureUser(page, 'admin');
  await cleanup(page);
  await page.reload();
  await ensureWatchlist(page, {
    loginNickname: 'admin',
    name: NAME,
    main: false,
    delete: true,
    securities: [],
    currencyPairs: [],
    e2e: 'e'
  });
  await page.getByRole('treeitem', { name: NAME, exact: true }).click();
  await page.getByRole('tab', { name: /Price feed|Kurs Datenfeed/ }).click();
  await expect(watchlistTable(page).locator('h4')).toContainText(NAME);
  await menu(page, 'edit', /Add price data problems|Kursdaten Probleme hinzufügen/);
  const form = page.locator('watchlist-add-edit-price-problem-instrument');
  await expect(form.locator('#daysSinceLastWork')).toHaveValue('60');
  const submit = form.locator('button[type="submit"]');
  await form.locator('#addIntraday').uncheck();
  await form.locator('#addHistorical').uncheck();
  await expect(submit).toBeDisabled();
  await form.locator('#addHistorical').check();
  await expect(submit).toBeEnabled();
  await form.locator('.p-dialog-header button').click();
  await expect(form).toHaveCount(0);
  await cleanup(page);
});
