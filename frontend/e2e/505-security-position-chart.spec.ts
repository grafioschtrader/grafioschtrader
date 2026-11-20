import { expect, test, type Locator, type Page, type Response } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { scenario } from './cash-security-transaction.helpers';
import { loadPortfolios, portfolioNode, portfolioSubtree, readFixturePortfolio, RX as TREE } from './portfolio.helpers';
import type {
  SecurityPositionCurrencySeries,
  SecurityPositionSeries
} from '../src/app/performanceperiod/model/security-position-series';

/**
 * The security position chart on the position that spec 255 creates for `alledit` (Banco Santander in the securities
 * account CT of CornèrTrader). The chart is opened through the context menu of the holdings table, once from the
 * securities account and once from the portfolio, so the two requests carry different scope parameters and the
 * headline names the scope. The chart type select is walked through, the performance basis switched and the drawdown
 * summary checked against the response of the backend; the expected figures come from that response, so the spec does
 * not depend on prices. The price chart and the position chart lead to each other through their context menus.
 *
 * The spec only reads data. The chart type the component remembers in the local storage is restored at the end, so
 * later specs do not inherit it.
 */
test.use({ viewport: { width: 1800, height: 1100 }, actionTimeout: 15_000 });

const STORE_KEY = /_securityposition_chart_type$/;

const RX = {
  positionChart: /^\s*(Position chart|Positionschart)\s*$/,
  lineChart: /^\s*(EOD as line chart|Tagesendkurse als Linengrafik)\s*$/,
  maxDrawdown: /(Maximum drawdown|Maximaler Rückgang)/,
  currentDrawdown: /(Current drawdown|Aktueller Rückgang)/,
  openNote: /(Open position|Offene Position):/,
  totalNote: /(Total since entry|Gesamt seit Einstieg):/
};

const CHART_TYPES = [
  'POSITION_CHART_RESULT',
  'POSITION_CHART_VALUE',
  'POSITION_CHART_PERFORMANCE',
  'POSITION_CHART_DRAWDOWN'
] as const;
const CHART_TYPES_WITH_BASIS: string[] = ['POSITION_CHART_RESULT', 'POSITION_CHART_PERFORMANCE'];

const chartView = (page: Page) => page.locator('security-position-chart');
const plotElement = (page: Page) => chartView(page).locator('.security-position-chart-plot');

/** Reads the chart types remembered per tenant, so the spec can put them back. */
async function storedChartTypes(page: Page): Promise<Record<string, string>> {
  return page.evaluate((pattern) => {
    const stored: Record<string, string> = {};
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key && new RegExp(pattern).test(key)) {
        stored[key] = localStorage.getItem(key) ?? '';
      }
    }
    return stored;
  }, STORE_KEY.source);
}

async function restoreChartTypes(page: Page, before: Record<string, string>): Promise<void> {
  await page.evaluate(
    ({ pattern, values }) => {
      const keys: string[] = [];
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        key && new RegExp(pattern).test(key) && keys.push(key);
      }
      keys.forEach((key) => localStorage.removeItem(key));
      Object.entries(values).forEach(([key, value]) => localStorage.setItem(key, value));
    },
    { pattern: STORE_KEY.source, values: before }
  );
}

/** The scope parameter a request must carry; the other scope parameter must be absent. */
interface ScopeParam {
  name: 'idPortfolio' | 'idSecurityaccount';
  value: number;
}

function positionResponse(page: Page, scope: ScopeParam): Promise<Response> {
  const other = scope.name === 'idPortfolio' ? 'idSecurityaccount' : 'idPortfolio';
  return page.waitForResponse(
    (r) => {
      const url = new URL(r.url());
      return (
        r.request().method() === 'GET' &&
        url.pathname.includes('/api/holding/securityposition/') &&
        url.searchParams.get(scope.name) === String(scope.value) &&
        !url.searchParams.has(other)
      );
    },
    { timeout: 30_000 }
  );
}

async function readSeries(response: Response): Promise<SecurityPositionSeries> {
  const body = await response.text();
  expect(response.ok(), `loading the position series: ${response.status()} ${body}`).toBeTruthy();
  return JSON.parse(body) as SecurityPositionSeries;
}

/** Trace names and value axis title of the drawn figure, which tell the chart types apart. */
async function plotSignature(page: Page): Promise<string> {
  return plotElement(page).evaluate((element: any) => {
    const names = (element.data ?? []).map((trace: any) => String(trace.name ?? '')).join('|');
    const title = element.layout?.yaxis?.title?.text ?? element.layout?.yaxis?.title ?? '';
    return element.data?.length ? `${names}#${title}` : '';
  });
}

/** Waits until a figure is drawn that differs from the previous one; returns its signature. */
async function waitForNewPlot(page: Page, previous: string): Promise<string> {
  let signature = '';
  await expect
    .poll(
      async () => {
        signature = await plotSignature(page).catch(() => '');
        return signature !== '' && signature !== previous;
      },
      { timeout: 30_000, message: 'the position chart was not redrawn' }
    )
    .toBe(true);
  return signature;
}

/** Selects the position of the scenario in the holdings table and opens the position chart from its context menu. */
async function openPositionChartFromHoldings(page: Page, scope: ScopeParam): Promise<SecurityPositionSeries> {
  const row = page.locator('tr[data-p-selectable-row]').filter({ hasText: scenario.securityName }).first();
  await expect(row, `${scenario.securityName} must be held (Playwright 255)`).toBeVisible({ timeout: 30_000 });
  await row.click();
  await page.waitForTimeout(300);
  await row.click({ button: 'right' });
  const item = page.locator('[role="menu"]:visible').getByRole('menuitem', { name: RX.positionChart }).first();
  await expect(item).toBeVisible({ timeout: 5_000 });
  const response = positionResponse(page, scope);
  await item.click();
  return readSeries(await response);
}

/** The series the chart shows: main currency when the response has one, as the currency select starts with it. */
function shownSeries(result: SecurityPositionSeries): SecurityPositionCurrencySeries {
  return result.main ?? result.security!;
}

async function expectScope(page: Page, result: SecurityPositionSeries, scopeName: string): Promise<void> {
  expect(result.scopeName).toBe(scopeName);
  expect(result.securityName).toBe(scenario.securityName);
  expect(result.dates.length, 'points of the phase').toBeGreaterThan(0);
  const headline = chartView(page).locator('.position-headline');
  await expect(headline).toContainText(scenario.securityName);
  await expect(headline).toContainText(scopeName);
  await expect(chartView(page).locator('select#holdingPhase')).toBeVisible({ visible: result.phases.length > 1 });
  await expect(chartView(page).locator('select#currencyKind')).toBeVisible({ visible: result.main != null });
}

async function openSecurityAccount(page: Page, portfolioTree: Locator): Promise<void> {
  await portfolioTree
    .locator('.p-tree-node-content')
    .filter({ hasText: TREE.securityAccountsFolder })
    .first()
    .dblclick();
  await portfolioTree
    .locator('.p-tree-node-content')
    .filter({ hasText: new RegExp(`^\\s*${scenario.securityAccountName}\\s*$`) })
    .first()
    .click();
}

test('the position chart from the securities account and from its portfolio', async ({ page }) => {
  const p = loadPortfolios().find(
    (portfolio) => portfolio.loginNickname === scenario.loginNickname && portfolio.name === scenario.portfolioName
  );
  expect(p, `fixture portfolio ${scenario.portfolioName}`).toBeTruthy();
  await loginAsFixtureUser(page, p!.loginNickname);
  const portfolio = await readFixturePortfolio(page, p!);
  const account = portfolio!.securityaccountList.find((row) => row.name === scenario.securityAccountName);
  expect(account, `securities account ${scenario.securityAccountName}`).toBeTruthy();
  const before = await storedChartTypes(page);

  try {
    await portfolioNode(page, p!).dblclick();
    const portfolioTree = portfolioSubtree(page, p!);

    const accountParam: ScopeParam = { name: 'idSecurityaccount', value: account!.idSecuritycashAccount };
    let result!: SecurityPositionSeries;
    await test.step('opened from the securities account, the chart covers that account', async () => {
      await openSecurityAccount(page, portfolioTree);
      result = await openPositionChartFromHoldings(page, accountParam);
      expect(result.idSecuritycurrency).toBeGreaterThan(0);
      await expectScope(page, result, account!.name);
    });

    let signature = '';
    await test.step('every chart type of the select is drawn, the basis is offered where it applies', async () => {
      const chartType = chartView(page).locator('select#chartType');
      const offered = await chartType
        .locator('option')
        .evaluateAll((options) => options.map((o) => (o as HTMLOptionElement).value).filter((v) => v !== ''));
      expect(offered).toEqual([...CHART_TYPES]);
      signature = await waitForNewPlot(page, '');
      for (const type of CHART_TYPES) {
        if ((await chartType.inputValue()) !== type) {
          await chartType.selectOption(type);
          signature = await waitForNewPlot(page, signature);
        }
        await expect(chartView(page).locator('select#performanceBasis')).toBeVisible({
          visible: CHART_TYPES_WITH_BASIS.includes(type)
        });
      }
    });

    await test.step('the performance basis changes the base of the percentage', async () => {
      await chartView(page).locator('select#chartType').selectOption('POSITION_CHART_PERFORMANCE');
      signature = await waitForNewPlot(page, signature);
      const basis = chartView(page).locator('select#performanceBasis');
      await expect(basis).toHaveValue('OPEN_POSITION');
      await expect(chartView(page).locator('.position-note').filter({ hasText: RX.openNote })).toHaveCount(1);
      await basis.selectOption('TOTAL_SINCE_ENTRY');
      signature = await waitForNewPlot(page, signature);
      await expect(chartView(page).locator('.position-note').filter({ hasText: RX.totalNote })).toHaveCount(1);
      await expect(chartView(page).locator('.position-note').filter({ hasText: RX.openNote })).toHaveCount(0);
    });

    await test.step('the drawdown chart states its summary and is remembered', async () => {
      await chartView(page).locator('select#chartType').selectOption('POSITION_CHART_DRAWDOWN');
      signature = await waitForNewPlot(page, signature);
      const series = shownSeries(result);
      const summary = chartView(page).locator('.position-summary');
      await expect(summary).toHaveCount(series.longestUnderwater ? 3 : 2);
      await expect(summary.first()).toContainText(RX.maxDrawdown);
      if (!result.phaseTo) {
        await expect(summary.last()).toContainText(RX.currentDrawdown);
      }
      const stored = await storedChartTypes(page);
      expect(Object.values(stored)).toContain(JSON.stringify('POSITION_CHART_DRAWDOWN'));
    });

    await test.step('the price chart and the position chart lead to each other with the same scope', async () => {
      await chartView(page).locator('.position-headline').click({ button: 'right' });
      const lineChart = page.locator('[role="menu"]:visible').getByRole('menuitem', { name: RX.lineChart }).first();
      await expect(lineChart).toBeVisible({ timeout: 5_000 });
      const priceData = page.waitForResponse(
        (r) =>
          r.request().method() === 'GET' &&
          new URL(r.url()).pathname.endsWith(`/securitycurrency/${result.idSecuritycurrency}/forchart`),
        { timeout: 30_000 }
      );
      await lineChart.click();
      expect((await priceData).ok()).toBeTruthy();
      const priceChart = page.locator('.fullChart').first();
      await expect(priceChart.locator('.js-plotly-plot')).toBeVisible({ timeout: 30_000 });
      await priceChart.click({ position: { x: 8, y: 8 } });
      await page.waitForTimeout(250);
      await priceChart.click({ button: 'right', position: { x: 8, y: 8 } });
      const back = page.locator('[role="menu"]:visible').getByRole('menuitem', { name: RX.positionChart }).first();
      await expect(back).not.toHaveAttribute('aria-disabled', 'true');
      const response = positionResponse(page, accountParam);
      await back.click();
      await expectScope(page, await readSeries(await response), account!.name);
      await expect(chartView(page).locator('select#chartType')).toHaveValue('POSITION_CHART_DRAWDOWN');
    });

    await test.step('opened from the portfolio, the chart covers the portfolio', async () => {
      await portfolioTree
        .locator('.p-tree-node-content')
        .filter({ hasText: TREE.securityAccountsFolder })
        .first()
        .click();
      const portfolioResult = await openPositionChartFromHoldings(page, {
        name: 'idPortfolio',
        value: portfolio!.idPortfolio
      });
      expect(portfolioResult.idSecuritycurrency).toBe(result.idSecuritycurrency);
      expect(portfolioResult.scopeName).not.toBe(result.scopeName);
      await expectScope(page, portfolioResult, portfolio!.name);
      // The remembered chart type is the start value for the next opening
      await expect(chartView(page).locator('select#chartType')).toHaveValue('POSITION_CHART_DRAWDOWN');
      await expect(chartView(page).locator('.position-summary').first()).toContainText(RX.maxDrawdown);
    });
  } finally {
    await restoreChartTypes(page, before);
  }
});
