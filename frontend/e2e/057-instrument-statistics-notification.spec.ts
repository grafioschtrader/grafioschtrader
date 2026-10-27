import { expect, Page, Route, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';

/** Watchlist seeded by 050 with the Spanish securities of alledit. Nothing is written by this spec. */
const WATCHLIST = 'Spain';

/** Deterministic statistics: one negative year and one negative summary value to check the red marking. */
const STATISTICS = {
  annualisedPerformance: {
    securityCurrency: 'EUR',
    mainCurrency: 'CHF',
    dateFrom: '2020-01-01',
    dateTo: '2024-12-31',
    lastYears: [
      { year: 2024, performanceYear: -3.25, performanceYearMC: -1.5 },
      { year: 2023, performanceYear: 7.75, performanceYearMC: 6.5 }
    ],
    annualisedYears: [
      { numberOfYears: 3, performanceAnnualised: 4.5, performanceAnnualisedMC: 3.75, sharpeRatio: 0.42 }
    ]
  },
  statisticsSummary: {
    statsPropertyMap: { MONTHLY: [{ property: 'min', value: -4.25, valueMC: -3.5 }] }
  }
};

test.use({ viewport: { width: 1600, height: 1200 } });

/** Holds the statistics request of the expanded row until the test releases it, then answers with STATISTICS. */
async function holdStatistics(page: Page) {
  let release!: () => void, arrived!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const pending = new Promise<void>((resolve) => (arrived = resolve));
  await page.route('**/api/security/*/securitystatistics*', async (route: Route) => {
    arrived();
    await gate;
    await route.fulfill({ json: STATISTICS });
  });
  return { release, arrived: pending };
}

test.beforeEach(async ({ page }) => {
  const info = await page.request.get('/api/gtinfo');
  expect(await info.json()).toMatchObject({ databaseName: 'grafioschtrader_t', activeProfile: 'e2e' });
  await loginAsFixtureUser(page, 'alledit');
  await page.getByRole('treeitem', { name: WATCHLIST, exact: true }).first().click();
  // Every row of the price feed tab can be expanded, the performance tab only rows with a position.
  await page
    .locator('p-tab')
    .filter({ hasText: /Price feed|Kurs Datenfeed/ })
    .click();
});

test('statistics tables render their delayed data with translated headers and red negative values', async ({
  page
}) => {
  const held = await holdStatistics(page);
  await page.locator('configurable-table tbody tr a .fa-chevron-circle-right').first().click();
  const expanded = page.locator('watchlist-price-feed-expanded').first();
  await expanded.locator('p-accordion-header').nth(1).click();
  await held.arrived;

  const result = expanded.locator('instrument-statistics-result');
  // Without data and without title the component is mounted but has no size yet.
  await expect(result).toHaveCount(1);
  await expect(result.locator('instrument-year-performance-table')).toHaveCount(0);
  held.release();

  const years = result.locator('instrument-year-performance-table');
  await expect(years.locator('tbody tr')).toHaveCount(2);
  await expect(years.locator('h5')).toHaveText(/Return per calendar year|Rendite pro Kalenderjahr/);
  await expect(years.locator('th').first()).toHaveText(/Calendar year|Kalenderjahr/);
  await expect(years.locator('th').nth(2)).toContainText('CHF %');
  const negativeYear = years.locator('tbody tr').filter({ hasText: '2024' }).locator('span').nth(1);
  await expect(negativeYear).toHaveCSS('color', 'rgb(255, 0, 0)');

  const annualised = result.locator('instrument-annualised-return-table');
  await expect(annualised.locator('tbody tr')).toHaveCount(1);
  await expect(annualised.locator('th').first()).toHaveText(/Number of years|Anzahl Jahre/);
  await expect(annualised.locator('th').nth(3)).toHaveText(/Sharpe/);

  const summary = result.locator('instrument-statistics-summary');
  await expect(summary).toContainText(/Statistical data|Statistische Daten/);
  await expect(summary).toContainText(/Monthly|Monatliche/);
  await expect(summary).toContainText(/Maximum loss in %|Maximaler Verlust in %/);
  await expect(summary.locator('th').nth(2)).toContainText('CHF');
  await expect(summary.getByText(/-4[.,]25/)).toHaveCSS('color', 'rgb(255, 0, 0)');
});
