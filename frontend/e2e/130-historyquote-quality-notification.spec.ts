import { expect, Locator, Page, Route, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';

/**
 * The completeness view of historical prices with fully intercepted data: nothing is read from or written to the
 * database, so the figures are deterministic and the spec is repeatable on any state of grafioschtrader_t.
 */
const TREE_ITEM = /^(Completeness of securities EOD|Vollständigkeit historischer Wertpapier-Kursdaten)$/;

function group(name: string, uniqueKey: number, children: object[], leaf = false): object {
  return {
    name,
    uniqueKey,
    idConnectorHistory: leaf ? 'gt.datafeed.e2e' : null,
    idStockexchange: leaf ? 1 : null,
    categoryType: leaf ? 0 : null,
    specialInvestmentInstrument: leaf ? 0 : null,
    numberOfSecurities: 2,
    activeNowSecurities: 1,
    connectorCreated: 480,
    manualImported: 12,
    filledLinear: 3,
    qualityPercentage: 97.5,
    ohlPercentage: 88.25,
    childrendHqg: children
  };
}

/** Exchange (or connector) / category / instrument — the depth whose leaf selects the securities. */
function head(topName: string): object {
  return {
    ...group('Head', 1, [group(topName, 2, [group('E2E Shares', 3, [group('E2E Direct', 4, [], true)])])]),
    lastUpdate: '2026-10-20'
  };
}

const SECURITIES = [
  {
    idSecurity: 999_001,
    name: 'E2E Quality Security',
    currency: 'CHF',
    activeFromDate: '2020-01-02',
    activeToDate: '2030-12-31',
    minDate: '2020-01-02',
    maxDate: '2026-10-23',
    missingStart: 0,
    missingEnd: 1,
    totalMissing: 4,
    expectedTotal: 1700,
    connectorCreated: 1690,
    manualImported: 5,
    filledLinear: 1,
    filledNoTradeDay: 0,
    qualityPercentage: 99.75,
    ohlPercentage: 95.5,
    toManyAsCalendar: 0,
    quoteSaturday: 0,
    quoteSunday: 0
  }
];

/** Answers every request matching url with json once the returned release is called. */
async function hold(page: Page, url: string, json: (route: Route) => object) {
  let release!: () => void, arrived!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const pending = new Promise<void>((resolve) => (arrived = resolve));
  await page.route(url, async (route) => {
    arrived();
    await gate;
    await route.fulfill({ json: json(route) }).catch(() => undefined);
  });
  return { release, arrived: pending };
}

async function selectGroup(page: Page, label: RegExp): Promise<void> {
  await page.locator('p-select#groupSelect').click();
  await page.locator('.p-select-overlay:visible').getByRole('option', { name: label }).click();
  await expect(page.locator('.p-select-overlay:visible')).toHaveCount(0);
}

function treeRow(tree: Locator, name: string): Locator {
  return tree.locator('tbody tr').filter({ has: tree.page().locator('td:has(p-treetabletoggler)', { hasText: name }) });
}

test.use({ viewport: { width: 1600, height: 1200 } });

test.beforeEach(async ({ page }) => {
  const info = await page.request.get('/api/gtinfo');
  expect(await info.json()).toMatchObject({ databaseName: 'grafioschtrader_t', activeProfile: 'e2e' });
  await loginAsFixtureUser(page, 'alledit');
});

test('delayed quality groups render, an older grouping cannot replace a newer one and the detail table publishes its menu', async ({
  page
}) => {
  /** The connector grouping is held separately so it can arrive after the stock exchange grouping it was replaced by. */
  const connector = await hold(page, '**/api/security/historyquotequality?groupedBy=CONNECTOR_GROUPED', () =>
    head('E2E Connector')
  );
  const exchange = await hold(page, '**/api/security/historyquotequality?groupedBy=STOCKEXCHANGE_GROUPED', () =>
    head('E2E Exchange')
  );
  const securities = await hold(page, '**/api/security/historyquotequalityids?*', () => SECURITIES);

  await page.getByRole('treeitem', { name: TREE_ITEM }).click();
  await exchange.arrived;
  const tree = page.locator('configurable-tree-table');
  await expect(tree.locator('tbody tr')).toHaveCount(0);
  exchange.release();
  await expect(treeRow(tree, 'E2E Exchange')).toHaveCount(1);
  await expect(page.locator('p-panel h4')).toContainText(/20/);
  await expect(tree.locator('tfoot, .p-treetable-tfoot')).toContainText('480');

  await selectGroup(page, /Connector grouped|Konnektor gruppiert/);
  await connector.arrived;
  await selectGroup(page, /Stock exchange grouped|Handelsplatz gruppiert/);
  await expect(treeRow(tree, 'E2E Exchange')).toHaveCount(1);
  connector.release();
  await page.waitForTimeout(500);
  await expect(treeRow(tree, 'E2E Connector')).toHaveCount(0);
  await expect(treeRow(tree, 'E2E Exchange')).toHaveCount(1);

  await treeRow(tree, 'E2E Exchange').locator('p-treetabletoggler button').click();
  await treeRow(tree, 'E2E Shares').locator('p-treetabletoggler button').click();
  await treeRow(tree, 'E2E Direct').click();
  await securities.arrived;
  const detail = page.locator('security-historyquote-quality-table');
  await expect(detail.locator('h5')).toHaveText('E2E Exchange / E2E Shares / E2E Direct');
  await expect(detail.locator('tbody tr')).toHaveCount(0);
  securities.release();
  const securityRow = detail.locator('tbody tr').filter({ hasText: 'E2E Quality Security' });
  await expect(securityRow).toHaveCount(1);
  await expect(detail.locator('th').first()).not.toHaveText('NAME');

  await securityRow.click();
  await tree.locator('tbody tr').first().click({ button: 'right' });
  await expect(
    page.locator('.p-contextmenu:visible').getByText(/EOD as line chart|Tagesendkurse als Linengrafik/)
  ).toBeVisible();
});
