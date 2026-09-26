import { expect, Locator, Page, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { expandTreeNode, openTreeContextMenu } from './portfolio.helpers';

/**
 * The historical replay of a simulation environment, driven from the tree node of that environment.
 *
 * The environment is opened with manual cash on purpose. A cash-only ledger in the portfolio currency is the one case
 * whose replayed result is known without a single price: equity cannot move, so the run must complete with no trades,
 * no drawdown and no Sharpe ratio. Cash in any other currency is revalued daily and does move. What a replay does with prices is covered by the strategy specs 205 to 215 and by the pure backend
 * tests; what this spec covers is the workflow around it — the menu entry, the start dialog, the background job, the
 * replay panel that reports it, the conventions it names and the audit trail it writes.
 *
 * Three things are worth knowing before changing anything here.
 *
 * 1. The replay is started from the main tenant, never from inside a switched-in simulation: the backend books the
 *    fills as the owner of the environment, so the menu entry is deliberately absent while a simulation is entered.
 * 2. Neither the date picker nor p-inputNumber commits a value that arrives without key events, which is why the end
 *    date is typed with `pressSequentially` in the de-CH short form and read back — the same trap as in spec 195.
 * 3. The run is asynchronous. Its status is polled through the REST endpoint rather than by waiting for a rendered
 *    number, because a replay over two weeks of trading days finishes in well under a second and the replay panel
 *    would already show the result before an assertion on the running state could see it.
 *
 * The spec owns everything it touches — one watchlist, one AlgoTop and the environments below it, all carrying
 * {@link PREFIX} — and removes them at the start of the run rather than only at the end, so a previous run that died
 * halfway does not break the retry.
 *
 * Prerequisites from earlier specs: the ALGO feature must be enabled, and 025-create-portfolio must have left a
 * portfolio with a cash account, because the opening balance needs one.
 */

const PREFIX = 'Replay e2e';
const OWNER = 'alledit';

/** Each regex also matches the raw NLS key: after a hard reload the chrome can render before the texts arrive. */
const RX = {
  algoRoot: /^(Rule-based Trading|Regelbasierter Handel|ALGO_OVERVIEW)/,
  startRun: /(Start replay|Wiederholung starten|SIMULATION_RUN_START)/i,
  completed: /(Completed|Abgeschlossen|COMPLETED)/,
  applyTaxModels: /(Apply simulation tax models|Simulationssteuermodelle anwenden|APPLY_TAX_MODELS)/,
  generateBondCoupons: /(Generate regular bond coupons|Regelmässige Anleihencoupons erzeugen|GENERATE_BOND_COUPONS)/
};

interface ApiSimulation {
  idTenant: number;
  tenantName: string;
  simulationStartDate?: string;
  initializationMode?: string;
}

interface ApiNamed {
  name: string;
}

interface ApiWatchlist extends ApiNamed {
  idWatchlist: number;
}

interface ApiAlgoTop extends ApiNamed {
  idAlgoAssetclassSecurity: number;
}

interface ApiCashaccount {
  idSecuritycashAccount: number;
  name: string;
  currency: string;
}

interface ApiPortfolio {
  currency: string;
  cashaccountList?: ApiCashaccount[];
}

interface ApiRun {
  idSimulationResult: number;
  status: string;
  openingDate: string;
  endDate: string;
  tradingDaysTotal: number;
  tradingDaysDone: number;
  conventions: string;
  totalTrades?: number;
  maxDrawdown?: number;
  failureMessage?: string;
}

interface ApiEvent {
  eventType: string;
  eventDate: string;
}

test.use({ viewport: { width: 1600, height: 1200 }, actionTimeout: 15000 });

async function authHeaders(page: Page): Promise<Record<string, string>> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  return { 'x-auth-token': token! };
}

async function getJson<T>(page: Page, url: string): Promise<T> {
  const response = await page.request.get(url, { headers: await authHeaders(page) });
  expect(response.ok(), `GET ${url}: ${await response.text()}`).toBeTruthy();
  return response.json() as Promise<T>;
}

/** Removes the spec's own leftovers, tolerating an object that cannot be deleted. */
async function cleanup(page: Page): Promise<void> {
  const headers = await authHeaders(page);
  for (const simulation of await getJson<ApiSimulation[]>(page, '/api/tenant/simulations')) {
    if (simulation.tenantName.startsWith(PREFIX)) {
      await page.request.delete(`/api/tenant/simulation/${simulation.idTenant}`, { headers });
    }
  }
  for (const top of await getJson<ApiAlgoTop[]>(page, '/api/algotop/tenant')) {
    if (top.name === PREFIX) {
      await page.request.delete(`/api/algotop/${top.idAlgoAssetclassSecurity}`, { headers });
    }
  }
  for (const list of await getJson<ApiWatchlist[]>(page, '/api/watchlist/tenant')) {
    if (list.name === PREFIX) {
      await page.request.delete(`/api/watchlist/${list.idWatchlist}`, { headers });
    }
  }
}

function toIso(date: Date): string {
  const pad = (value: number): string => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function daysBackIso(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return toIso(date);
}

/** '2026-07-24' → '24.07.26', the de-CH short form the picker renders and parses. */
function toDeChDate(iso: string): string {
  const [year, month, day] = iso.split('-');
  return `${day}.${month}.${year.slice(2)}`;
}

async function typeDate(dialog: Locator, selector: string, iso: string): Promise<void> {
  const deCh = toDeChDate(iso);
  const input = dialog.locator(`${selector} input`).first();
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(deCh, { delay: 20 });
  await input.blur();
  await expect(input, `date after typing "${deCh}"`).toHaveValue(deCh);
}

/**
 * Right-clicks the tree node or the replay panel and picks the start entry. The panel appends its context menu to the
 * body, outside any p-contextmenu element, so the entry is looked up in whichever menu is visible.
 */
async function openStartMenu(page: Page, target: Locator): Promise<void> {
  await target.waitFor({ state: 'visible', timeout: 15_000 });
  await target.click({ button: 'right' });
  const item = page.locator('[role="menu"]:visible').getByText(RX.startRun).first();
  await item.waitFor({ state: 'visible', timeout: 5_000 });
  await item.click();
}

/** The destructive replay must be acknowledged before either entry point opens its input dialog. */
async function confirmReplay(page: Page, accept: boolean): Promise<void> {
  const confirmation = page.getByRole('alertdialog', { name: /^(Confirmation|Bestätigung)$/ });
  await expect(confirmation).toBeVisible();
  await expect(confirmation).toContainText(/manually entered transactions|manuell erfassten Buchungen/);
  await confirmation.getByRole('button', { name: accept ? /^(Yes|Ja)$/ : /^(No|Nein)$/ }).click();
  await expect(confirmation).toBeHidden();
}

/** Polls the run until it leaves the running state, which is what a background job forces a client to do. */
async function awaitTerminalRun(page: Page, idTenant: number): Promise<ApiRun> {
  for (let attempt = 0; attempt < 120; attempt++) {
    const run = await getJson<ApiRun>(page, `/api/tenant/simulation/${idTenant}/run`);
    if (run && run.status !== 'RUNNING') {
      return run;
    }
    await page.waitForTimeout(1000);
  }
  throw new Error('the replay did not reach a terminal state within two minutes');
}

test.describe('historical replay', () => {
  let openingDate: string;
  let endDate: string;
  let simulation: ApiSimulation;

  test.beforeEach(async ({ page }) => {
    test.setTimeout(240_000);
    await loginAsFixtureUser(page, OWNER);
    await cleanup(page);

    const headers = await authHeaders(page);
    // Two weeks of history give the run several trading days without making it long.
    openingDate = daysBackIso(15);
    endDate = daysBackIso(1);

    const watchlist = await page.request.post('/api/watchlist', { headers, data: { name: PREFIX } });
    expect(watchlist.ok(), await watchlist.text()).toBeTruthy();
    const idWatchlist = ((await watchlist.json()) as ApiWatchlist).idWatchlist;

    // A strategy is only ready for an environment when the weightings below it add up to 100%. One asset class
    // without a security satisfies that and stays uninvested, so the environment still holds nothing but its opening.
    const assetclasses = await getJson<{ idAssetClass: number }[]>(page, '/api/assetclass');
    expect(assetclasses.length, 'an asset class to weight the strategy with').toBeGreaterThan(0);
    const top = await page.request.post('/api/algotop/create', {
      headers,
      data: {
        name: PREFIX,
        idWatchlist,
        percentage: 100,
        assetclassPercentageList: [{ idAssetclass: assetclasses[0].idAssetClass, percentage: 100 }]
      }
    });
    expect(top.ok(), await top.text()).toBeTruthy();

    const portfolios = await getJson<ApiPortfolio[]>(page, '/api/portfolio/tenant');
    // Only cash in the portfolio's own currency keeps its value: a foreign-currency or crypto account is revalued every
    // day and would show a drawdown without a single trade. The list order is unspecified, so filter explicitly.
    const cashaccount = portfolios.flatMap((portfolio) =>
      (portfolio.cashaccountList ?? []).filter((account) => account.currency === portfolio.currency)
    )[0];
    expect(cashaccount, 'a cash account in its portfolio currency to open the environment with').toBeTruthy();

    const created = await page.request.post('/api/tenant/simulation', {
      headers,
      data: {
        idAlgoTop: (await getJson<ApiAlgoTop[]>(page, '/api/algotop/tenant')).find((entry) => entry.name === PREFIX)!
          .idAlgoAssetclassSecurity,
        tenantName: `${PREFIX} run`,
        initializationMode: 'MANUAL_CASH',
        simulationStartDate: openingDate,
        cashBalances: { [cashaccount.idSecuritycashAccount]: 100000 }
      }
    });
    expect(created.ok(), await created.text()).toBeTruthy();
    simulation = (await created.json()) as ApiSimulation;

    // The tree is built once at load, so it has to be rebuilt before the new nodes exist.
    await page.reload();
    const algoRoot = page.getByRole('treeitem', { name: RX.algoRoot }).first();
    await expect(algoRoot).toBeVisible({ timeout: 30_000 });
    const strategy = algoRoot.getByRole('treeitem', { name: PREFIX, exact: true }).first();
    await expect(strategy).toBeVisible({ timeout: 15_000 });
    const run = strategy.getByRole('treeitem', { name: new RegExp(`^${PREFIX} run\\b`) }).first();
    await expect(async () => {
      await expandTreeNode(strategy);
      await expect(run).toBeVisible({ timeout: 2_000 });
    }).toPass({ timeout: 30_000 });
  });

  test.afterEach(async ({ page }) => {
    await cleanup(page);
  });

  test('a cash-only environment replays to a completed result with an audit trail', async ({ page }) => {
    const node = page
      .getByRole('treeitem', { name: new RegExp(`^${PREFIX} run\\b`) })
      .first()
      .locator(':scope > .p-tree-node-content');
    await openTreeContextMenu(page, node, RX.startRun);
    await confirmReplay(page, true);

    const dialog = page.locator('.p-dialog:visible').last();
    await dialog.waitFor({ state: 'visible', timeout: 15_000 });
    await typeDate(dialog, '#endDate', endDate);

    const accepted = page.waitForResponse(
      (response) =>
        response.url().endsWith(`/api/tenant/simulation/${simulation.idTenant}/run`) &&
        response.request().method() === 'POST'
    );
    await dialog.getByRole('button', { name: RX.startRun }).click();
    const response = await accepted;
    expect(response.ok(), await response.text()).toBeTruthy();

    const run = await awaitTerminalRun(page, simulation.idTenant);
    expect(run.status, `failure was: ${run.failureMessage ?? ''}`).toBe('COMPLETED');
    expect(run.openingDate).toBe(openingDate);
    expect(run.endDate).toBe(endDate);
    expect(run.tradingDaysDone).toBe(run.tradingDaysTotal);
    expect(run.tradingDaysTotal, 'the window has to contain trading days at all').toBeGreaterThan(0);
    expect(run.totalTrades, 'cash alone produces no round trip').toBe(0);
    expect(run.maxDrawdown, 'cash alone cannot fall below its own peak').toBe(0);
    // The conventions are recorded with the run rather than assumed by the reader. Whether trades cost anything
    // depends on the fee model of the security accounts, so only a cost-free environment such as this one states
    // NO_TRANSACTION_COST.
    expect(run.conventions).toContain('NEXT_CLOSE_FILL');
    expect(run.conventions).toContain('NO_TRANSACTION_COST');

    const trail = await getJson<{ content: ApiEvent[] }>(
      page,
      `/api/tenant/simulation/${simulation.idTenant}/run/events?page=0&size=50`
    );
    expect(trail.content.map((entry) => entry.eventType)).toEqual(expect.arrayContaining(['RUN_START', 'RUN_END']));

    // The dialog closes once the run is accepted and the replay panel of the environment renders what the endpoint
    // reported, including the two optional estimates the run was started with.
    await expect(dialog).toBeHidden({ timeout: 15_000 });
    const panel = page.locator('algo-simulation-run');
    await expect(panel.getByText(RX.completed).first()).toBeVisible({ timeout: 15_000 });
    await expect(panel.getByText(RX.applyTaxModels).first()).toBeVisible();
    await expect(panel.getByText(RX.generateBondCoupons).first()).toBeVisible();
  });

  test('repeating a replay replaces the previous result rather than appending to it', async ({ page }) => {
    const headers = await authHeaders(page);
    const start = async (): Promise<ApiRun> => {
      const response = await page.request.post(`/api/tenant/simulation/${simulation.idTenant}/run`, {
        headers,
        data: { endDate }
      });
      expect(response.ok(), await response.text()).toBeTruthy();
      return awaitTerminalRun(page, simulation.idTenant);
    };

    const first = await start();
    const second = await start();

    expect(second.idSimulationResult, 'one run record per environment').toBe(first.idSimulationResult);
    expect(second.status).toBe('COMPLETED');
    expect(second.tradingDaysDone).toBe(second.tradingDaysTotal);
  });

  for (const entry of ['tree', 'panel'] as const) {
    test(`the ${entry} entry opens the start form only after confirmation`, async ({ page }) => {
      const node = page
        .getByRole('treeitem', { name: new RegExp(`^${PREFIX} run\\b`) })
        .first()
        .locator(':scope > .p-tree-node-content');
      let target = node;
      if (entry === 'panel') {
        await node.click();
        target = page.locator('algo-simulation-run .data-container').first();
        await expect(target).toBeVisible();
        await target.click();
      }
      const submitted: string[] = [];
      page.on('request', (request) => {
        if (request.method() === 'POST' && request.url().endsWith(`/simulation/${simulation.idTenant}/run`)) {
          submitted.push(request.url());
        }
      });
      await openStartMenu(page, target);
      await expect(page.locator('#endDate input')).toBeHidden();
      await confirmReplay(page, false);
      await expect(page.locator('#endDate input')).toBeHidden();
      const run = await page.request.get(`/api/tenant/simulation/${simulation.idTenant}/run`, {
        headers: await authHeaders(page)
      });
      expect(run.status(), 'cancelling confirmation must not create a run').toBe(204);
      expect(submitted).toEqual([]);

      await openStartMenu(page, target);
      await confirmReplay(page, true);
      await expect(page.locator('#endDate input')).toBeVisible();
      expect(submitted).toEqual([]);
      await page.keyboard.press('Escape');
      await expect(page.locator('#endDate input')).toBeHidden();
    });
  }

  test('an end date on or before the opening date is refused', async ({ page }) => {
    const headers = await authHeaders(page);
    const response = await page.request.post(`/api/tenant/simulation/${simulation.idTenant}/run`, {
      headers,
      data: { endDate: openingDate }
    });
    expect(response.ok(), 'the opening date itself is not a day a decision may be taken on').toBeFalsy();
  });
});
