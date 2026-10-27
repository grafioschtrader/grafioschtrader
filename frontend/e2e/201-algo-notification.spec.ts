import { expect, Page, Route, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';

const NAME = 'Algo signals e2e';
const root = (page: Page) => page.getByRole('treeitem', { name: /^(Rule-based Trading|Regelbasierter Handel)$/ });
const tree = (page: Page) => page.locator('configurable-tree-table').first();

async function headers(page: Page) {
  return { 'x-auth-token': (await page.evaluate(() => sessionStorage.getItem('jwt')))! };
}
async function json(response: Awaited<ReturnType<Page['request']['get']>>) {
  expect(response.ok(), await response.text()).toBeTruthy();
  const body = await response.text();
  return body ? JSON.parse(body) : null;
}
async function cleanup(page: Page) {
  const auth = await headers(page);
  for (const top of await json(await page.request.get('/api/algotop/tenant', { headers: auth }))) {
    if (top.name.startsWith(NAME))
      await json(await page.request.delete(`/api/algotop/${top.idAlgoAssetclassSecurity}`, { headers: auth }));
  }
  for (const list of await json(await page.request.get('/api/watchlist/tenant', { headers: auth }))) {
    if (list.name === NAME)
      await json(await page.request.delete(`/api/watchlist/${list.idWatchlist}`, { headers: auth }));
  }
}
async function hold(page: Page, url: string | RegExp) {
  let release!: () => void,
    arrived = false;
  const gate = new Promise<void>((resolve) => (release = resolve));
  await page.route(url, async (route: Route) => {
    const response = await route.fetch();
    expect(response.ok()).toBeTruthy();
    arrived = true;
    await gate;
    await route.fulfill({ response });
  });
  return { release, arrived: () => expect.poll(() => arrived, { timeout: 15_000 }).toBe(true) };
}
async function rootMenu(page: Page, label: RegExp) {
  await root(page).click({ button: 'right' });
  await page.locator('[role="menu"]:visible').getByRole('menuitem', { name: label }).click();
}
async function editMenu(page: Page, label: RegExp) {
  await page
    .locator('p-menubar')
    .getByRole('menuitem', { name: /^(Edit|Bearbeiten)$/ })
    .click();
  await page.getByRole('menuitem', { name: label }).click();
}
async function close(page: Page) {
  await page.locator('.p-dialog:visible').last().locator('.p-dialog-header button').last().click();
}

test.use({ viewport: { width: 1700, height: 1300 }, actionTimeout: 15_000 });

test.beforeEach(async ({ page }, testInfo) => {
  const info = await json(await page.request.get('/api/gtinfo'));
  expect(info).toMatchObject({ databaseName: 'grafioschtrader_t', activeProfile: 'e2e' });
  await loginAsFixtureUser(page, 'alledit');
  await cleanup(page);
  if (testInfo.title.startsWith('standalone')) return;
  const auth = await headers(page);
  const lists = await json(await page.request.get('/api/watchlist/tenant', { headers: auth }));
  const spain = lists.find((list) => list.name === 'Spain');
  expect(spain).toBeTruthy();
  const group = await json(await page.request.get(`/api/watchlist/${spain.idWatchlist}`, { headers: auth }));
  const security = group.securityPositionList.find(
    (row) => row.securitycurrency.isin === 'ES0113900J37'
  ).securitycurrency;
  const owned = await json(await page.request.post('/api/watchlist', { headers: auth, data: { name: NAME } }));
  await json(
    await page.request.put(`/api/watchlist/${owned.idWatchlist}/addSecuritycurrency`, {
      headers: auth,
      data: { securityList: [security], currencypairList: [] }
    })
  );
  await page.reload();
  await expect(root(page)).toBeVisible();
});

test('hierarchy creation, delayed forms, category search and simple strategies notify their hosts', async ({
  page
}) => {
  test.setTimeout(120_000);
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  const watchlists = await hold(page, '**/api/watchlist/tenant');
  await rootMenu(page, /Create strategy from watchlist|Strategie aus Watchlist erstellen/);
  let dialog = page.locator('.p-dialog:visible');
  await watchlists.arrived();
  watchlists.release();
  await expect(dialog.locator('#idWatchlist option', { hasText: NAME })).toHaveCount(1);
  await dialog.locator('#name').fill(NAME);
  await dialog.locator('#idWatchlist').selectOption({ label: NAME });
  const created = page.waitForResponse(
    (r) => r.url().endsWith('/api/algotop/createfromwatchlist') && r.request().method() === 'POST'
  );
  await dialog.locator('button[type="submit"]').click();
  const top = await json(await created);
  await expect(dialog).toHaveCount(0);
  const hierarchy = await hold(page, new RegExp(`/api/algotop/${top.idAlgoAssetclassSecurity}/hierarchy$`));
  await root(page).getByRole('treeitem', { name: NAME, exact: true }).click();
  await hierarchy.arrived();
  hierarchy.release();
  await expect(tree(page).locator('tbody tr')).toHaveCount(3);
  await expect(tree(page)).toContainText('Banco Santander');
  const category = tree(page).locator('tbody tr').nth(1);
  await category.click();
  const definition = await hold(page, '**/api/globalparameters/formdefinition/AlgoAssetclass*');
  await editMenu(page, /Edit.*asset class|Bearbeiten.*Anlageklasse/i);
  await definition.arrived();
  await expect(page.locator('algo-assetclass-edit')).toHaveCount(0);
  definition.release();
  const assetForm = page.locator('algo-assetclass-edit');
  await expect(assetForm.locator('input#percentage')).toHaveValue('100');
  await expect(assetForm.locator('#idSecurityaccount1 option')).not.toHaveCount(0);
  await close(page);

  // A new custom category can open the search form as the asynchronous continuation of its save.
  await tree(page).locator('tbody tr').first().click();
  await editMenu(page, /Add.*asset class|Hinzufügen.*Anlageklasse/i);
  await expect(assetForm.locator('#idSecurityaccount1 option').last()).toHaveAttribute('value', /\d+/);
  await assetForm.locator('#customCategory').check();
  await assetForm.locator('#name').fill(NAME + ' category');
  await assetForm.locator('#addInstrumentsBySearch').check();
  await assetForm.locator('input#percentage').fill('10');
  const savedCategory = page.waitForResponse(
    (r) => r.url().endsWith('/api/algoassetclass') && r.request().method() === 'POST'
  );
  await assetForm.locator('button[type="submit"]').click();
  expect((await savedCategory).ok()).toBeTruthy();
  const search = page.locator('algo-assetclass-add-instrument');
  await expect(search.locator('select#idStockexchange option')).not.toHaveCount(0);
  const results = await hold(page, /\/api\/algoassetclass\/\d+\/search\?/);
  await search.locator('#isin').fill('ES0113900J37');
  await search.locator('button[type="submit"]').click();
  await results.arrived();
  results.release();
  await expect(search.locator('tbody tr').filter({ hasText: 'ES0113900J37' })).toHaveCount(1);
  await close(page);

  await expect(tree(page).locator('tbody tr').filter({ hasText: 'Banco Santander' })).toHaveCount(1);
  await tree(page).locator('tbody tr').filter({ hasText: 'Banco Santander' }).click();
  await editMenu(page, /Edit.*security|Bearbeiten.*Wertpapier/i);
  const securityForm = page.locator('algo-security-edit');
  await expect(securityForm.locator('#security')).toContainText('Banco Santander');
  await expect(securityForm.locator('#idSecurityaccount1 option')).not.toHaveCount(0);
  await close(page);
  // The strategy schema arrives after the selector was changed; it must render without another click.
  await editMenu(page, /Create.*strategy|Erstellen.*Strategie/i);
  const strategy = page.locator('algo-strategy-edit');
  const schema = await hold(page, '**/api/algostrategy/form/65');
  await strategy.locator('#algoStrategyImplementations').selectOption('AS_OBSERVED_SECURITY_ABSOLUTE_PRICE');
  await schema.arrived();
  schema.release();
  await expect(strategy.locator('input').filter({ visible: true }).first()).toBeVisible();
  await expect(strategy.locator('#lowerValue')).toBeVisible();
  const obsolete = await hold(page, '**/api/algostrategy/form/67');
  await strategy
    .locator('#algoStrategyImplementations')
    .selectOption('AS_OBSERVED_SECURITY_PERIOD_PRICE_GAIN_LOSE_PERCENT');
  await obsolete.arrived();
  await strategy.locator('#algoStrategyImplementations').selectOption('AS_OBSERVED_SECURITY_ABSOLUTE_PRICE');
  obsolete.release();
  await expect(strategy.locator('#lowerValue')).toBeVisible();
  await expect(strategy.locator('#algoStrategyImplementations')).toHaveValue('AS_OBSERVED_SECURITY_ABSOLUTE_PRICE');
  await close(page);
  expect(errors).toEqual([]);
  await cleanup(page);
});

test('manual allocation rows and portfolio creation remain usable after options arrive', async ({ page }) => {
  await rootMenu(page, /Create.*portfolio-based strategy|Erstellen.*Portfoliobasierte Strategie/i);
  const dialog = page.locator('.p-dialog:visible');
  await expect(dialog.locator('#idWatchlist option', { hasText: NAME })).toHaveCount(1);
  await dialog.locator('#name').fill(NAME + ' manual');
  await dialog.locator('#idWatchlist').selectOption({ label: NAME });
  await expect(dialog.locator('#idAssetclass1 option')).not.toHaveCount(0);
  await dialog.getByRole('button', { name: /Add asset class|Hinzufügen Anlageklasse/ }).click();
  await expect(dialog.locator('#idAssetclass2')).toBeVisible();
  await dialog.getByRole('button', { name: /Remove last asset class|Letzte Anlageklasse entfernen/ }).click();
  await expect(dialog.locator('#idAssetclass2')).toHaveCount(0);
  await close(page);
  await rootMenu(page, /Create strategy from portfolio|Strategie aus Portfolio erstellen/);
  await expect(page.locator('.p-dialog:visible #referenceDate input')).toBeVisible();
  await close(page);
  await cleanup(page);
});

test('standalone alerts show delayed rows and roll back a refused preference change', async ({ page }) => {
  let release!: () => void;
  let arrived = false;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const strategy = {
    idAlgoRuleStrategy: 91002,
    idAlgoAssetclassSecurity: 91001,
    algoStrategyImplementations: 'AS_OBSERVED_SECURITY_ABSOLUTE_PRICE',
    alertEnabled: true
  };
  await page.route('**/api/algosecurity/tenant', async (route) => {
    arrived = true;
    await gate;
    await route.fulfill({
      json: [
        {
          idAlgoAssetclassSecurity: 91001,
          security: { name: 'Standalone notification e2e', currency: 'CHF' },
          algoStrategyList: [strategy]
        }
      ]
    });
  });
  await page.route('**/api/algoalerts/hierarchies', (route) =>
    route.fulfill({ json: [{ idAlgoTop: 91003, name: 'Dormant notification e2e', assigned: false, nodes: [] }] })
  );
  await page.goto('/mainview/algooverview');
  await expect.poll(() => arrived).toBe(true);
  await expect(tree(page)).not.toContainText('Standalone notification e2e');
  release();
  await expect(tree(page)).toContainText('Standalone notification e2e');
  await expect(page.locator('algo-hierarchy-alert-overview')).toContainText('Dormant notification e2e');
  let patchArrived = false,
    finish!: () => void,
    attempts = 0;
  const pending = new Promise<void>((resolve) => (finish = resolve));
  await page.route('**/api/algostrategy/91002/alert-enabled', async (route) => {
    attempts++;
    if (attempts === 1) {
      patchArrived = true;
      await pending;
      await route.fulfill({ json: { ...strategy, alertEnabled: false } });
    } else await route.fulfill({ status: 400, json: { message: 'Refused preference change' } });
  });
  const checkbox = tree(page).locator('tbody input[type="checkbox"]');
  await expect(checkbox).toBeChecked();
  await checkbox.uncheck();
  await expect.poll(() => patchArrived).toBe(true);
  await expect(checkbox).toBeDisabled();
  finish();
  await expect(checkbox).toBeEnabled();
  await expect(checkbox).not.toBeChecked();
  await checkbox.click();
  await expect.poll(() => attempts).toBe(2);
  await expect(checkbox).not.toBeChecked();
  await expect(checkbox).toBeEnabled();
});
