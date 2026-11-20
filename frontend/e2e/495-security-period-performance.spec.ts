import { expect, test, type Page } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { performanceDates } from './performance-notification.helpers';
import type { SecurityPeriodPerformance } from '../src/app/performanceperiod/model/security-period-performance';

/**
 * The tab "Performance by security" beside the period performance, for the client and for a portfolio. The trading
 * days and the calculation are display fixtures served by page.route, so the spec creates no data and does not depend
 * on the security transactions of the database; only the portfolio of the fixture user is read.
 */
test.use({ viewport: { width: 1800, height: 1100 }, actionTimeout: 15_000 });

const TAB = /Performance by security|Ertrag je Wertpapier/;
const PERIOD_TAB = /^\s*(Performance|Ertrag)\s*$/;

function securityPerformance(): SecurityPeriodPerformance {
  const row = (id: number, name: string, gain: number | null, twr: number | null) => ({
    idSecuritycurrency: id,
    name,
    isin: null,
    currency: 'CHF',
    marginInstrument: false,
    holdingDays: 90,
    unitsStart: 10,
    unitsEnd: 10,
    valueStartMC: 1000,
    valueEndMC: gain === null ? null : 1000 + gain,
    inflowMC: gain === null ? null : 0,
    outflowMC: gain === null ? null : 0,
    gainPeriod: gain,
    gainPeriodMC: gain,
    twrPercent: twr,
    twrSecurityCurrencyPercent: twr,
    remark: gain === null ? ('MISSING_EXCHANGE_RATE' as const) : null
  });
  return {
    mainCurrency: 'CHF',
    dateFrom: '2025-06-30',
    dateTo: '2026-03-31',
    rows: [
      row(900001, 'Alpha Fixture', 300, 5),
      row(900002, 'Beta Fixture', 100, 40),
      row(900003, 'Gamma Fixture', null, null)
    ],
    totals: { valueStartMC: 3000, valueEndMC: 3400, inflowMC: 0, outflowMC: 0, gainPeriodMC: 400 },
    omittedRows: 1
  };
}

/** Serves the trading days and the calculation and stores a shared start date before the views read it. */
async function prepare(page: Page): Promise<void> {
  await loginAsFixtureUser(page, 'alledit');
  await page.route('**/api/holding/getdatesforform?*', (route) => route.fulfill({ json: performanceDates }));
  await page.route('**/api/holding/securityperformance/*/*?*', (route) =>
    route.fulfill({ json: securityPerformance() })
  );
  await page.evaluate(() => {
    sessionStorage.setItem('performanceDateFrom', '2025-06-30');
    sessionStorage.removeItem('performanceDateTo');
  });
}

const firstRow = (page: Page) => page.locator('security-period-performance-table tbody tr').first();

test('the client tab calculates, totals, sorts and expands, and shares its start date', async ({ page }) => {
  await prepare(page);
  await page.goto('/mainview/tenantTabMenu/performancetabmenu');
  await page.getByRole('tab', { name: TAB }).click();
  const dateFrom = page.locator('security-period-performance #dateFrom input');
  await expect(dateFrom).toHaveValue(/30|2025/);
  const preset = await dateFrom.inputValue();
  await expect(page.locator('security-period-performance #dateTo input')).not.toHaveValue('');

  const request = page.waitForRequest(/securityperformance\/2025-06-30\/2026-03-31/);
  await page.locator('security-period-performance button[type=submit]').click();
  await request;
  await expect(firstRow(page)).toContainText('Alpha Fixture');
  await expect(page.locator('security-period-performance-table')).toContainText(/Partial total|Teilsumme/);
  await expect(page.locator('security-period-performance-table')).toContainText(/400/);
  await expect(page.locator('security-period-performance-table')).toContainText(
    /Exchange rate missing|Wechselkurs für eine Buchung fehlt/
  );

  const twrHeader = page
    .locator('security-period-performance-table th')
    .filter({ hasText: /^\s*(Time-weighted return|Zeitgewichtete Rendite) %/ });
  await twrHeader.click();
  await twrHeader.click();
  await expect(firstRow(page)).toContainText('Beta Fixture');

  const transactions = page.waitForRequest(/security\/tenantsecurity\/900002\?.*untilDate=2026-03-31/);
  await page.route('**/api/security/tenantsecurity/900002?*', (route) => route.abort());
  await firstRow(page).locator('i.fa-chevron-circle-right').click();
  await transactions;

  await page.getByRole('tab', { name: PERIOD_TAB }).click();
  await expect(page.locator('#dateFrom input')).toHaveValue(preset);
});

test('the charts show return against profit and the profits as treemap; a click expands the row', async ({ page }) => {
  await prepare(page);
  await page.route('**/api/security/tenantsecurity/*?*', (route) => route.abort());
  await page.goto('/mainview/tenantTabMenu/performancetabmenu');
  await page.getByRole('tab', { name: TAB }).click();
  await expect(page.locator('security-period-performance #dateTo input')).not.toHaveValue('');
  await page.locator('security-period-performance button[type=submit]').click();
  await expect(firstRow(page)).toContainText('Alpha Fixture');

  // The direct child only: the result table inside renders a .data-container of its own.
  await page.locator('security-period-performance > .data-container').click({ position: { x: 5, y: 5 } });
  await page.getByRole('menuitem', { name: /^(Show|Ansicht)$/ }).click();
  await page.getByRole('menuitem', { name: /^(Show chart|Zeige Diagramm)$/i }).click();
  const chart = page.locator('security-period-performance-chart');
  await expect(chart.locator('select#chartType')).toHaveValue('SECURITY_PERIOD_CHART_SCATTER');
  // Gamma has neither return nor profit, so two of the three securities are drawn and one is named as left out
  const points = chart.locator('.scatterlayer .point');
  await expect(points).toHaveCount(2);
  await expect(chart).toContainText(/not shown|nicht dargestellt/);

  const alphaTransactions = page.waitForRequest(/security\/tenantsecurity\/900001\?.*untilDate=2026-03-31/);
  const box = await points.first().boundingBox();
  expect(box).not.toBeNull();
  await page.mouse.click(box!.x + box!.width / 2, box!.y + box!.height / 2);
  await alphaTransactions;

  await chart.locator('select#chartType').selectOption('SECURITY_PERIOD_CHART_TREEMAP');
  const betaTile = chart.locator('.treemaplayer g.slice').filter({ hasText: 'Beta Fixture' });
  await expect(betaTile).toHaveCount(1);
  await expect(chart).toContainText(/Profits|Gewinne/);
  const betaTransactions = page.waitForRequest(/security\/tenantsecurity\/900002\?.*untilDate=2026-03-31/);
  await betaTile.click();
  await betaTransactions;
});

test('the portfolio tab calculates for its portfolio', async ({ page }) => {
  await prepare(page);
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  const response = await page.request.get('/api/portfolio/tenant', { headers: { 'x-auth-token': token ?? '' } });
  expect(response.ok(), await response.text()).toBeTruthy();
  const portfolios = (await response.json()) as { idPortfolio: number; name: string; currency: string }[];
  test.skip(portfolios.length === 0, 'the fixture user has no portfolio');
  const { idPortfolio, name, currency } = portfolios[0];

  // The portfolio tabs take their portfolio from the main tree; a direct URL leaves them without one.
  await page
    .getByRole('treeitem', { name: `${name} / ${currency}`, exact: true })
    .locator(':scope > .p-tree-node-content')
    .click();
  await expect(page).toHaveURL(new RegExp(`/portfolioTabMenu/${idPortfolio}`));
  await page.getByRole('tab', { name: TAB }).click();
  await expect(page.locator('security-period-performance #dateTo input')).not.toHaveValue('');
  const request = page.waitForRequest(
    (r) => r.url().includes('/api/holding/securityperformance/') && r.url().includes(`idPortfolio=${idPortfolio}`)
  );
  await page.locator('security-period-performance button[type=submit]').click();
  await request;
  await expect(firstRow(page)).toContainText('Alpha Fixture');
});
