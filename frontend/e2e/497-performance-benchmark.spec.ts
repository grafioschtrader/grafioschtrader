import { expect, Locator, Page, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { performanceDates, periodResult } from './performance-notification.helpers';
import type { PerformancePeriod } from '../src/app/performanceperiod/model/performance.period';

/**
 * The benchmark of the period performance in the client view: choosing one for a single report, saving it as default
 * with the button behind the field, seeing it again after a reload, hiding it for one report and removing the default. The dates and the calculation
 * are display fixtures, so no valuation data is needed; the stored benchmark goes through the real endpoint. The spec
 * owns nothing but the benchmark of the client: it removes it at the start, so a run that failed halfway does not
 * break the next one, and again at the end, because no later spec expects a benchmark.
 */
test.use({ viewport: { width: 1800, height: 1100 }, actionTimeout: 15_000 });

const BENCHMARK_URL = '/api/performancebenchmark';

async function authHeaders(page: Page): Promise<{ 'x-auth-token': string }> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token).toBeTruthy();
  return { 'x-auth-token': token! };
}

/** Removes the benchmark of the client view through REST; removing an absent one is no error. */
async function removeTenantBenchmark(page: Page): Promise<void> {
  const response = await page.request.delete(BENCHMARK_URL, { headers: await authHeaders(page) });
  expect(response.status(), await response.text()).toBe(204);
}

/** The calculation fixture with a benchmark comparison: portfolio 0 %, benchmark +10 %, difference -10 pp. */
function periodWithBenchmark(): PerformancePeriod {
  const period = periodResult();
  const window = period.periodWindows[0];
  window.benchmarkTwrPercent = 10;
  window.excessTwrPercent = -10;
  window.benchmarkCoverage = 'BENCHMARK_COVERED';
  window.periodStepList = window.periodStepList.map((step) => ({
    ...step,
    benchmarkTwrPercent: 1.5,
    excessTwrPercent: 10.84,
    benchmarkCoverage: 'BENCHMARK_COVERED'
  }));
  period.benchmark = {
    idSecuritycurrency: 1,
    name: 'USD/CHF',
    currency: 'CHF',
    source: 'BENCHMARK_SOURCE_REQUEST',
    mainCurrency: 'CHF',
    fxPair: null,
    fxInverse: false,
    returnBasis: 'AS_SUPPLIED',
    requestedFrom: '2025-12-31',
    requestedTo: '2026-03-31',
    effectiveFrom: '2026-01-05',
    effectiveTo: '2026-03-31',
    lastInstrumentDate: '2026-03-31',
    lastRateDate: null,
    notices: [{ code: 'BENCHMARK_START_LATER', parameters: { startDate: '2026-01-05' } }],
    metrics: {
      portfolioTwrPercent: 0,
      benchmarkTwrPercent: 10,
      excessReturnPercent: -10,
      portfolioTwrAnnualizedPercent: null,
      benchmarkTwrAnnualizedPercent: null,
      excessReturnAnnualizedPercent: null,
      includedIntervals: 60
    },
    chart: [
      { date: '2026-01-05', portfolioTwrPercent: 0, benchmarkTwrPercent: 0 },
      { date: '2026-03-31', portfolioTwrPercent: 0, benchmarkTwrPercent: 10 }
    ]
  };
  return period;
}

/** Activates the view so that its menu appears in the menu bar, then runs one entry of the Show menu. */
async function runMenuEntry(page: Page, view: Locator, entry: RegExp): Promise<void> {
  await view.click({ position: { x: 5, y: 5 } });
  await page.getByRole('menuitem', { name: /^(Show|View|Ansicht)$/ }).click();
  await page.getByRole('menuitem', { name: entry }).click();
}

test('a benchmark is chosen for one report, saved with the button, hidden for one report and removed', async ({
  page
}) => {
  await loginAsFixtureUser(page, 'alledit');
  await removeTenantBenchmark(page);
  await page.route('**/api/holding/getdatesforform?*', (route) => route.fulfill({ json: performanceDates }));
  const requestedUrls: URL[] = [];
  await page.route('**/api/holding/*/*/WM_YEAR?*', (route) => {
    const url = new URL(route.request().url());
    requestedUrls.push(url);
    const json = url.searchParams.get('idBenchmark') === '0' ? periodResult() : periodWithBenchmark();
    return route.fulfill({ json });
  });

  try {
    await page.goto('/mainview/tenantTabMenu/performancetabmenu');
    const view = page.locator('.data-container').filter({ has: page.locator('select#periodSplit') });
    const benchmarkField = page.locator('#benchmarkName');
    await expect(page.locator('#dateTo input')).not.toHaveValue('');
    await expect(benchmarkField).toHaveValue('');
    await page.locator('select#periodSplit').selectOption('WM_YEAR');

    // Nothing to save before an instrument is chosen
    const saveButton = view.getByRole('button', { name: /^(Save as default|Als Standard speichern)$/ });
    await expect(saveButton).toBeDisabled();

    // Choose USD/CHF for this report only
    await view.locator('form-input-button').getByRole('button').click();
    const search = page.locator('securitycurrency-search-and-set');
    await expect(search.locator('select#idStockexchange option').last()).toBeAttached();
    await search.locator('select#assetclassType').selectOption('CURRENCY_PAIR');
    await search.locator('select#currency').selectOption('USD');
    await search.locator('button[type="submit"]').click();
    await search.locator('tbody tr').filter({ hasText: 'USD/CHF' }).click();
    await search.getByRole('button', { name: /Assign selected|Selektierte zuweisen/ }).click();
    await expect(benchmarkField).toHaveValue('USD/CHF');
    await expect(saveButton).toBeEnabled();

    await view.locator('button[type=submit]').click();
    const comparison = page.locator('performance-period-benchmark');
    await expect(comparison).toContainText('USD/CHF');
    await expect(comparison).toContainText(/-10/);
    await expect(comparison).toContainText(/05\.01\.(20)?26|1\/5\/(20)?26|2026-01-05/);
    expect(Number(requestedUrls.at(-1)?.searchParams.get('idBenchmark'))).toBeGreaterThan(0);

    const tree = page.locator('performance-period-treetable');
    await tree.locator('p-treetabletoggler button').first().click();
    await expect(tree).toContainText(/Benchmark return|Benchmark-Rendite/);
    await expect(tree).toContainText(/Difference to benchmark|Differenz zur Benchmark/);

    // The PDF dialog names the benchmark of the displayed calculation and offers the comparison section
    await runMenuEntry(page, view, /PDF.report|PDF-Bericht/i);
    const pdfDialog = page.locator('.p-dialog:visible');
    await expect(pdfDialog.locator('select#preset')).toBeVisible();
    await expect(pdfDialog).toContainText(/USD\/CHF \((Chosen for this report|Für diesen Bericht gewählt)\)/);
    await expect(pdfDialog).toContainText(/Benchmark comparison|Benchmark-Vergleich/);
    await page.keyboard.press('Escape');
    await expect(pdfDialog).toHaveCount(0);

    // Save it with the button behind the field; after a reload the field shows it with its source
    const saved = page.waitForResponse(
      (r) => new URL(r.url()).pathname === BENCHMARK_URL && r.request().method() === 'PUT'
    );
    await saveButton.click();
    expect((await saved).ok()).toBeTruthy();
    await expect(saveButton).toBeDisabled();
    await page.reload();
    await expect(benchmarkField).toHaveValue(/USD\/CHF \((Default of the client|Standard des Mandanten)\)/);

    // Hide it for one report: the request carries 0 and the comparison disappears
    await runMenuEntry(page, view, /^(Hide benchmark for this report|Benchmark für diesen Bericht ausblenden)$/);
    await expect(benchmarkField).toHaveValue(/No benchmark|Keine Benchmark/);
    await page.locator('select#periodSplit').selectOption('WM_YEAR');
    await view.locator('button[type=submit]').click();
    await expect(page.locator('performance-period-metrics')).toContainText(/12[.,]34/);
    expect(requestedUrls.at(-1)?.searchParams.get('idBenchmark')).toBe('0');
    await expect(comparison.locator('fieldset')).toHaveCount(0);

    // Remove the default: the stored benchmark is gone
    const removed = page.waitForResponse(
      (r) => new URL(r.url()).pathname === BENCHMARK_URL && r.request().method() === 'DELETE'
    );
    await runMenuEntry(
      page,
      view,
      /^(Remove default benchmark of the client|Standard-Benchmark des Mandanten entfernen)$/
    );
    expect((await removed).status()).toBe(204);
    await expect(benchmarkField).toHaveValue('');
  } finally {
    await removeTenantBenchmark(page);
  }
});
