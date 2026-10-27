import { expect, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { dividendReport, holdTenantResponse, openTenantTab } from './tenant-notification.helpers';

test('income chart renders delayed data and follows the selected year', async ({ page }) => {
  await loginAsFixtureUser(page, 'alledit');
  const report = dividendReport();
  report.securityDividendsYearGroup.push({ ...report.securityDividendsYearGroup[0], year: 2024 });
  await page.route('**/api/portfolio/dividends?*', (route) => route.fulfill({ json: report }));
  const months = Array(12).fill(0);
  months[0] = 123;
  const held = await holdTenantResponse(page, '**/api/portfolio/dividendschart?*', {
    mainCurrency: 'CHF',
    incomeAssetclasses: [],
    assetclasses: [],
    incomeYears: [2024, 2025].map((year) => ({
      year,
      dividendNetMC: year === 2025 ? 123 : 0,
      dividendTaxMC: 0,
      interestNetMC: 0,
      interestTaxMC: 0,
      cashInterestMC: 0,
      netIncomeMC: 123,
      feeMC: 0,
      financeCostCfdMC: 0,
      financeCostForexMC: 0,
      dividendNetMonthMC: months,
      dividendTaxMonthMC: Array(12).fill(0),
      interestNetMonthMC: months,
      interestTaxMonthMC: months,
      cashInterestMonthMC: months,
      netIncomeMonthMC: months,
      taxMonthMC: months
    }))
  });
  try {
    await openTenantTab(page, /^(Dividends\/Interest|Dividende\/Zins)$/);
    await expect(page.locator('p-table').first()).toContainText('2025');
    await page.getByRole('menuitem', { name: /^(Show|Ansicht)$/ }).click();
    await page.getByRole('menuitem', { name: /Show charts|Zeige Diagramme/ }).click();
    await held.arrived;
    await expect(page.locator('.dividends-chart')).toBeVisible();
    held.release();
    await expect(page.locator('.dividends-chart .js-plotly-plot .main-svg').first()).toBeVisible();
    await page.locator('p-table').first().getByRole('row').filter({ hasText: '2024' }).click();
    await expect(page.locator('.dividends-chart h4')).toHaveText(
      /No income in this year|Keine Erträge in diesem Jahr|Kein Einkommen/
    );
    await page.locator('.dividends-chart select#chartType').selectOption('INCOME_CHART_INCOME_COST');
    await expect(page.locator('.dividends-chart h4')).toHaveCount(0);
    await expect(page.locator('.dividends-chart .js-plotly-plot .main-svg').first()).toBeVisible();
  } finally {
    held.release();
  }
});

test('holdings chart renders the report and replaces its empty treemap hint', async ({ page }) => {
  await loginAsFixtureUser(page, 'alledit');
  await page.route('**/api/securityaccount/tenantsecurityaccountsummary/assetclasstypewithcash?*', (route) =>
    route.fulfill({
      json: { currency: 'CHF', securityPositionGroupSummaryList: [], holdingsTreemap: { nodes: [] } }
    })
  );
  await openTenantTab(page, /^(Security asset classes with cash|Anlageklassen mit Cash)$/);
  await page.getByRole('menuitem', { name: /^(Show|Ansicht)$/ }).click();
  await page.getByRole('menuitem', { name: /^(Show chart|Zeige Diagramm)$/ }).click();
  const chart = page.locator('.holdings-chart');
  await expect(chart).toBeVisible();
  await chart.locator('select#chartType').selectOption('HOLDINGS_CHART_TREEMAP');
  await expect(chart.locator('h4')).toBeVisible();
  await chart.locator('select#chartType').selectOption('HOLDINGS_CHART_ASSETCLASS');
  await expect(chart.locator('h4')).toHaveCount(0);
  await expect(chart.locator('.js-plotly-plot .main-svg').first()).toBeVisible();
});
