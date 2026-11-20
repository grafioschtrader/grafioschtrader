import { expect, Page, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { openContextMenu } from './generic-connector.helpers';

test.use({ viewport: { width: 1440, height: 1200 }, actionTimeout: 10_000 });
const releases: (() => void)[] = [];
test.afterEach(() => releases.splice(0).forEach((release) => release()));
function gate() {
  let release!: () => void;
  const promise = new Promise<void>((resolve) => (release = resolve));
  releases.push(release);
  return { promise, release };
}
const instruments = [
  { idSecuritycurrency: 910011, name: 'Notification Nestle', tickerSymbol: 'NEST', currency: 'CHF' },
  { idSecuritycurrency: 910022, name: 'Notification ABB', tickerSymbol: 'ABB', currency: 'CHF' }
];
function result(value = 0.42, missing = false) {
  return {
    firstAvailableDate: '2020-01-03',
    lastAvailableDate: '2020-12-31',
    mmdhList: [],
    idsMissingDividends: missing ? [910011] : [],
    correlationInstruments: [
      { idSecuritycurrency: 910011, correlations: [1, value] },
      { idSecuritycurrency: 910022, correlations: [value, 1] }
    ]
  };
}
async function fixture(page: Page) {
  const sets = [1, 2, 3].map((number) => ({
    idCorrelationSet: 910000 + number,
    name: `Notification ${number}`,
    note: `Original note ${number}`,
    samplingPeriod: 'DAILY_RETURNS',
    rolling: 3,
    dateFrom: '2020-01-01',
    dateTo: '2020-12-31',
    adjustCurrency: false,
    includeDividends: number === 2,
    securitycurrencyList: number === 3 ? [] : instruments
  }));
  await page.route('**/api/correlationset/tenant', (route) => route.fulfill({ json: sets }));
  await page.route('**/api/correlationset/limit', (route) =>
    route.fulfill({
      json: {
        tenantLimit: { actual: 3, limit: 20 },
        dailyConfiguration: '1,3,5',
        monthlyConfiguration: '1,3,6',
        annualConfiguration: null,
        requiredMinPeriods: 3
      }
    })
  );
  await page.route('**/api/correlationset/limit/*', (route) => route.fulfill({ json: { actual: 2, limit: 20 } }));
  await page.route('**/api/correlationset/calculation/*', (route) => route.fulfill({ json: result() }));
  await page.route('**/api/correlationset', async (route) => {
    if (route.request().method() !== 'PUT') return route.fallback();
    const payload = route.request().postDataJSON();
    const index = sets.findIndex((set) => set.idCorrelationSet === payload.idCorrelationSet);
    sets[index] = {
      ...sets[index],
      ...payload,
      samplingPeriod:
        typeof payload.samplingPeriod === 'number'
          ? ['DAILY_RETURNS', 'MONTHLY_RETURNS', 'ANNUAL_RETURNS'][payload.samplingPeriod]
          : payload.samplingPeriod
    };
    await route.fulfill({ json: sets[index] });
  });
  await loginAsFixtureUser(page, 'alledit');
  await page
    .locator('.p-tree-node-content', { hasText: /Watchlist\s*-\s*(Korrelationsmatrix|Correlation\s*matrix)/i })
    .first()
    .click();
  await expect(page.locator('select#idCorrelationSet option')).toHaveCount(3);
  return sets;
}
async function choose(page: Page, id: number) {
  await page.locator('select#idCorrelationSet').selectOption(String(id));
}
const matrix = (page: Page) => page.locator('correlation-table > p-table');

test('replaces an obsolete calculation and clears results for an empty set', async ({ page }) => {
  await fixture(page);
  const old = gate();
  let requested = false;
  let obsoleteDelivered = false;
  await page.route('**/api/correlationset/calculation/*', async (route) => {
    const obsolete = route.request().url().endsWith('/910001');
    if (obsolete) {
      requested = true;
      await old.promise;
    }
    await route.fulfill({ json: result(obsolete ? 0.13 : 0.84, !obsolete) }).catch(() => {});
    if (obsolete) obsoleteDelivered = true;
  });
  await choose(page, 910001);
  await expect.poll(() => requested).toBe(true);
  await choose(page, 910002);
  await expect(matrix(page).getByText(/0[.,]84/, { exact: true })).toHaveCount(2);
  await expect(page.locator('.data-container > p').filter({ hasText: 'Notification Nestle' })).toBeVisible();
  old.release();
  await expect.poll(() => obsoleteDelivered).toBe(true);
  await expect(matrix(page).getByText(/0[.,]13/, { exact: true })).toHaveCount(0);
  await choose(page, 910003);
  await expect(matrix(page).locator('tbody tr')).toHaveCount(0);
  await expect(page.locator('.data-container > p')).toHaveCount(0);
});

test('reports a failed calculation once and clears the failure for the next set', async ({ page }) => {
  await fixture(page);
  let failures = 0;
  await page.route('**/api/correlationset/calculation/*', (route) => {
    if (!route.request().url().endsWith('/910001')) {
      return route.fulfill({ json: result(0.84) });
    }
    failures++;
    return route.fulfill({
      status: 400,
      json: { className: 'ValidationError', error: { fieldErrors: [{ field: 'rolling', message: 'rejected' }] } }
    });
  });
  const failure = page.getByText(/The data could not be loaded\.|Die Daten konnten nicht geladen werden\./);
  const toasts = page.locator('.p-toast-message-error');
  await choose(page, 910001);
  await expect(toasts).toHaveCount(1);
  await expect(failure).toBeVisible();
  await toasts.locator('.p-toast-close-button').click();
  await choose(page, 910002);
  await expect(matrix(page).getByText(/0[.,]84/, { exact: true })).toHaveCount(2);
  await expect(failure).toHaveCount(0);
  await expect(toasts).toHaveCount(0);
  expect(failures).toBe(1);
});

test('keeps the newer selected set and draft when an earlier save finishes', async ({ page }) => {
  await fixture(page);
  const saved = gate();
  let requested = false;
  await page.route('**/api/correlationset', async (route) => {
    const payload = route.request().postDataJSON();
    expect(payload.idCorrelationSet).toBe(910001);
    requested = true;
    await saved.promise;
    await route.fulfill({ json: { ...payload, samplingPeriod: 'DAILY_RETURNS' } }).catch(() => {});
  });
  await choose(page, 910001);
  await expect(matrix(page).getByText(/0[.,]42/, { exact: true })).toHaveCount(2);
  await page.locator('textarea#note').fill('First set draft');
  await page.locator('dynamic-form button[type="submit"]').click();
  await expect.poll(() => requested).toBe(true);
  await choose(page, 910002);
  await expect(page.locator('textarea#note')).toHaveValue('Original note 2');
  await page.locator('textarea#note').fill('Second set draft');
  const response = page.waitForResponse(
    (response) => response.request().method() === 'PUT' && new URL(response.url()).pathname === '/api/correlationset'
  );
  saved.release();
  await (await response).finished();
  await expect(page.locator('dynamic-form button[type="submit"]')).toBeEnabled();
  await expect(page.locator('select#idCorrelationSet')).toHaveValue('910002');
  await expect(page.locator('textarea#note')).toHaveValue('Second set draft');
  const menu = await openContextMenu(page);
  await menu.getByText(/^(Edit|Bearbeiten)\s+(Correlation set|Korrelationsset)/i).click();
  const dialog = page.locator('correlation-set-edit .p-dialog');
  await expect(dialog.locator('input#name')).toHaveValue('Notification 2');
  await dialog.locator('button.p-dialog-close-button').click();
  await expect(page.locator('correlation-set-edit')).toHaveCount(0);
});

test('rebuilds rolling choices when the set editor is reopened', async ({ page }) => {
  await fixture(page);
  await choose(page, 910001);
  await expect(matrix(page).getByText(/0[.,]42/, { exact: true })).toHaveCount(2);
  const edit = async () => {
    const menu = await openContextMenu(page);
    await menu.getByText(/^(Edit|Bearbeiten)\s+(Correlation set|Korrelationsset)/i).click();
    const dialog = page.locator('correlation-set-edit .p-dialog');
    await expect(dialog.locator('input#name')).toHaveValue('Notification 1');
    return dialog;
  };
  let dialog = await edit();
  await dialog.locator('select#samplingPeriod').selectOption('ANNUAL_RETURNS');
  await expect(dialog.locator('select#rolling')).toBeDisabled();
  await dialog.locator('select#samplingPeriod').selectOption('MONTHLY_RETURNS');
  await expect(dialog.locator('select#rolling option')).toHaveCount(4);
  await dialog.locator('input#name').fill('Discarded editor draft');
  await dialog.locator('button.p-dialog-close-button').click();
  await expect(page.locator('correlation-set-edit')).toHaveCount(0);
  dialog = await edit();
  await expect(dialog.locator('select#samplingPeriod')).toHaveValue('DAILY_RETURNS');
  await expect(dialog.locator('select#rolling option')).toHaveCount(3);
  await expect(dialog.locator('select#rolling')).toBeEnabled();
  await dialog.locator('input#name').fill('Discarded second draft');
  await dialog.locator('button.p-dialog-close-button').click();
  await expect(page.locator('correlation-set-edit')).toHaveCount(0);
});

test('keeps the existing search dialog usable below the OnPush matrix host', async ({ page }) => {
  const sets = await fixture(page);
  const searched = gate();
  const extra = {
    idSecuritycurrency: 910033,
    name: 'Notification search result',
    tickerSymbol: 'NEW',
    currency: 'CHF',
    isin: 'CH0038863350'
  };
  let available = [extra];
  let requested = false;
  await page.route('**/api/correlationset/910001/search**', async (route) => {
    requested = true;
    await searched.promise;
    await route.fulfill({ json: { securityList: available, currencypairList: [] } }).catch(() => {});
  });
  await page.route('**/api/correlationset/910001/addSecuritycurrency', async (route) => {
    sets[0].securitycurrencyList = [...instruments, extra];
    available = [];
    await route.fulfill({ json: sets[0] });
  });
  await choose(page, 910001);
  await expect(matrix(page).getByText(/0[.,]42/, { exact: true })).toHaveCount(2);
  const menu = await openContextMenu(page);
  await menu.getByText(/Bestehendes.*Instrument|Add existing instrument/i).click();
  const dialog = page.locator('correlation-add-instrument .p-dialog');
  await expect(dialog.locator('select#idStockexchange option')).not.toHaveCount(0);
  // The tenant limit line shows the instruments already in the set and follows an addition.
  const limitLine = dialog.locator('correlation-set-add-instrument-table h5').first();
  await expect(limitLine).toHaveText(/^\s*2\s/);
  await dialog.locator('input#name').fill('Notification');
  await dialog.locator('button[type="submit"]').click();
  await expect.poll(() => requested).toBe(true);
  searched.release();
  await expect(dialog.getByText('Notification search result', { exact: true })).toBeVisible();
  await dialog.locator('tbody p-tablecheckbox').first().click();
  await dialog.getByRole('button', { name: /^(Add|Hinzuf.gen)$/ }).click();
  await expect(matrix(page).locator('tbody tr')).toHaveCount(3);
  await expect(limitLine).toHaveText(/^\s*3\s/);
  await dialog.getByRole('button', { name: /^(Beenden|Close)$/ }).click();
  await expect(page.locator('correlation-add-instrument')).toHaveCount(0);
});

test('notifies chart modes, pending data, missing closes and an empty selection', async ({ page }) => {
  await fixture(page);
  let missing = false;
  let pending: ReturnType<typeof gate> | null = null;
  let requested = 0;
  await page.route('**/api/correlationset/corrrolling/*', async (route) => {
    requested++;
    const wait = pending;
    if (wait) await wait.promise;
    await route
      .fulfill({
        json: [
          {
            securitycurrencyList: instruments,
            dates: ['2020-01-03', '2020-01-06'],
            correlation: [0.42, 0.84],
            closeDates: ['2020-01-03', '2020-01-06'],
            closes: missing
              ? []
              : [
                  [100, 105],
                  [200, 210]
                ]
          }
        ]
      })
      .catch(() => {});
  });
  await choose(page, 910002);
  await expect(matrix(page).getByText(/0[.,]42/, { exact: true })).toHaveCount(2);
  await matrix(page).locator('tbody tr').first().locator('td').nth(5).click();
  const menu = await openContextMenu(page);
  await menu
    .getByText(/(als\s+Grafik|as\s+graph)/i)
    .first()
    .click();
  const chart = page.locator('.correlation-chart');
  const plot = chart.locator('.js-plotly-plot');
  await expect(plot).toBeVisible();
  await chart.locator('select#chartType').selectOption('CORRELATION_CHART_COMBINED');
  await expect.poll(() => plot.evaluate((el: any) => el.data?.length)).toBe(3);
  await chart.locator('select#chartType').selectOption('CORRELATION_CHART_ONLY');
  await expect.poll(() => plot.evaluate((el: any) => el.data?.length)).toBe(1);
  await chart.locator('select#chartType').selectOption('CORRELATION_CHART_COMBINED');
  pending = gate();
  missing = true;
  const previous = requested;
  await page.locator('.data-container').first().locator('button[type="submit"]').click();
  await expect.poll(() => requested).toBeGreaterThan(previous);
  await expect(plot).toBeHidden();
  pending.release();
  await expect(plot).toBeVisible();
  await expect.poll(() => plot.evaluate((el: any) => el.data?.length)).toBe(1);
  await expect(chart.locator('p')).toHaveCount(1);
  await choose(page, 910003);
  await expect(plot).toBeHidden();
  await expect(chart.locator('p')).toHaveCount(1);
});
