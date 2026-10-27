import { expect, Page, Route, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { watchlistTable } from './watchlist.helpers';
import { selectDynamicFormOptionByText } from './dynamic-form.helpers';

/**
 * Change notification of the seasonality heat map of Nestlé in the Switzerland watchlist of 060. The seasonal returns
 * are intercepted with fixed figures, so the spec reads and writes nothing in grafioschtrader_t.
 */
const WATCHLIST_NAME = 'Switzerland';
const SECURITY_RX = /Nestl.+AG/;

function result(periodType: 'MONTHLY' | 'QUARTERLY'): object {
  const periods = periodType === 'MONTHLY' ? 12 : 4;
  const row = (year: number, base: number) => ({
    year,
    periodReturns: Array.from({ length: periods }, (_, i) => base + i / 10),
    annualReturn: base * 3,
    partial: false
  });
  return {
    periodType,
    currency: 'CHF',
    dividendsIncluded: false,
    inTenantCurrency: false,
    dividendsAvailable: true,
    currencyConversionAvailable: false,
    yearRows: [row(2023, -1.5), row(2024, 2.25)],
    columnStats: Array.from({ length: periods + 1 }, () => ({
      mean: 0.5,
      median: 0.25,
      stdDev: 1,
      pctPositive: 50,
      count: 2
    }))
  };
}

test.use({ viewport: { width: 1600, height: 1200 } });

test.beforeEach(async ({ page }) => {
  const info = await page.request.get('/api/gtinfo');
  expect(await info.json()).toMatchObject({ databaseName: 'grafioschtrader_t', activeProfile: 'e2e' });
  await loginAsFixtureUser(page, 'alledit');
  await page.getByRole('treeitem', { name: WATCHLIST_NAME, exact: true }).first().click();
  await watchlistTable(page).waitFor({ state: 'visible' });
});

/** Answers seasonal returns with fixed data; the first monthly and every quarterly request wait for a release. */
async function interceptSeasonalReturns(page: Page) {
  const gates: { [key: string]: () => void } = {};
  const arrived: { [key: string]: Promise<void> } = {};
  const signalArrival: { [key: string]: () => void } = {};
  for (const key of ['MONTHLY', 'QUARTERLY']) {
    arrived[key] = new Promise<void>((resolve) => (signalArrival[key] = resolve));
  }
  const gate = (key: string) => new Promise<void>((resolve) => (gates[key] = resolve));
  const monthlyGate = gate('MONTHLY');
  const quarterlyGate = gate('QUARTERLY');
  let monthlyRequests = 0;
  await page.route('**/api/security/*/seasonalreturns*', async (route: Route) => {
    const periodType = new URL(route.request().url()).searchParams.get('periodType') as 'MONTHLY' | 'QUARTERLY';
    signalArrival[periodType]();
    if (periodType === 'QUARTERLY') {
      await quarterlyGate;
    } else if (monthlyRequests++ === 0) {
      await monthlyGate;
    }
    await route.fulfill({ json: result(periodType) }).catch(() => undefined);
  });
  return { release: (key: string) => gates[key](), arrived };
}

test('delayed heat map, capability toggles and a superseded period request', async ({ page }) => {
  const seasonal = await interceptSeasonalReturns(page);
  const security = page.locator('td').filter({ hasText: SECURITY_RX }).first();
  await security.click();
  await security.click({ button: 'right' });
  await page
    .locator('[role="menu"]:visible')
    .getByText(/^(Seasonality|Saisonalität)$/)
    .first()
    .click();

  await seasonal.arrived['MONTHLY'];
  const view = page.locator('seasonality-table').locator('..');
  await expect(view.locator('p-progressBar, p-progressbar')).toBeVisible();
  await expect(page.locator('seasonality-table th')).toHaveCount(0);
  seasonal.release('MONTHLY');

  const headers = page.locator('seasonality-table th');
  await expect(headers).toHaveCount(14);
  await expect(headers.last()).toHaveText(/Annual return|Jahresrendite/);
  await expect(page.locator('seasonality-table tbody tr')).toHaveCount(2);
  await expect(view.locator('p-progressBar, p-progressbar')).toHaveCount(0);
  await expect(view.getByText(/^\s*(Currency|Währung):\s*$/).locator('..')).toContainText('CHF');
  await expect(page.locator('#inTenantCurrency input, input#inTenantCurrency').first()).toBeDisabled();
  await expect(page.locator('#includeDividends input, input#includeDividends').first()).toBeEnabled();

  await selectDynamicFormOptionByText(page, page.locator('dynamic-form'), 'periodType', /Quarterly|Quartalsweise/);
  await seasonal.arrived['QUARTERLY'];
  await selectDynamicFormOptionByText(page, page.locator('dynamic-form'), 'periodType', /Monthly|Monatlich/);
  await expect(headers).toHaveCount(14);
  seasonal.release('QUARTERLY');
  await page.waitForTimeout(500);
  await expect(headers).toHaveCount(14);
  await expect(page.locator('seasonality-table tbody tr')).toHaveCount(2);
});
