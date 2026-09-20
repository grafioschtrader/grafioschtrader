import { expect, Locator, Page, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { confirmDelete, expandTreeNode, openTreeContextMenu } from './portfolio.helpers';
import { expectToast } from './manage-client.helpers';

/**
 * The three ways a simulation environment establishes its opening ledger, driven through the strategy dialog.
 *
 * The spec owns everything it touches: one watchlist, one AlgoTop and the simulation environments below it, all
 * carrying {@link PREFIX}. It removes them at the start of the run rather than only at the end, so a previous run
 * that died halfway does not break the retry.
 *
 * Three traps are worth knowing before changing anything here.
 *
 * 1. Neither the date picker nor p-inputNumber commits a value that arrives without key events. Optimus'
 *    `DatePicker.onUserInput` returns early unless `isKeydown` was set by a preceding keydown, and
 *    `InputNumber.onUserInput` never calls `updateModel` at all — so `fill()` leaves both reactive controls at null.
 *    The date is mandatory, so the submit button stays disabled and the run dies on actionability; the cash balance
 *    is optional, so a `fill()` there fails silently and creates an all-zero opening ledger. Both are typed with
 *    `pressSequentially` and read back.
 * 2. Both components put their id on the host element, not on the inner input. `#simulationStartDate input` and
 *    `[id^="cashBalance_"] input` are therefore the real controls; `input#simulationStartDate` matches nothing, and
 *    an assertion against it would pass on absence.
 * 3. The dialog assigns `tenantName` and `initializationMode` from a `setTimeout` in ngAfterViewInit and only then
 *    subscribes to valueChanges. Typing before that lands is silently overwritten, so every interaction waits for
 *    the form to settle first.
 *
 * Prerequisites from earlier specs: the ALGO feature must be enabled for the instance (otherwise the whole tree
 * branch is missing) and 025-create-portfolio must have left portfolios with cash accounts, because manual mode
 * needs at least one cash-balance field to exist.
 */

const PREFIX = 'Opening e2e';
const OWNER = 'alledit';

/** Each regex also matches the raw NLS key: after a hard reload the chrome can render before the texts arrive. */
const RX = {
  algoRoot: /^(Rule-based Trading|Regelbasierter Handel|ALGO_OVERVIEW)/,
  createSimulation: /(Create simulation environment|Simulationsumgebung erstellen|CREATE_SIMULATION)/i,
  switchToSimulation: /(Switch to simulation|Zur Simulation wechseln|SWITCH_TO_SIMULATION)/i,
  deleteSimulation: /(Delete simulation|Simulation l.schen|DELETE_SIMULATION)/i,
  save: /^(Save|Speichern)$/,
  savedToast: /(has been saved|wurde gespeichert)/i,
  deletedToast: /(has been deleted|wurde gel.scht)/i,
  copyPortfolio: /(Take over source portfolio|Quellportfolio übernehmen|COPY_PORTFOLIO)/i
};

function algoRootNode(page: Page): Locator {
  return page.getByRole('treeitem', { name: RX.algoRoot }).first();
}

function strategyNode(page: Page, name: string): Locator {
  return algoRootNode(page).getByRole('treeitem', { name, exact: true }).first();
}

/**
 * Both simulation endpoints in one shape: the listing returns `initializationMode`, while the create endpoint returns
 * the tenant entity, which names the same value `simulationInitializationMode`.
 */
interface ApiSimulation {
  idTenant: number;
  tenantName: string;
  simulationStartDate?: string;
  initializationMode?: string;
  simulationInitializationMode?: string;
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

/** `transactionDate` is not serialized (`@JsonIgnore`); the date is the leading `yyyy-MM-dd` of `transactionTime`. */
interface ApiTransaction {
  transactionTime: string;
  transactionType: string;
  cashaccountAmount: number;
}

test.use({ viewport: { width: 1600, height: 1400 }, actionTimeout: 15000 });

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

/**
 * Removes the spec's own leftovers, tolerating an object that cannot be deleted. A delete that fails here says
 * nothing about the run that is about to start, and turning the cleanup red would hide the real failure.
 */
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

/** Instruments on a watchlist, as `GET /api/watchlist/{id}` returns them. */
async function watchlistSecurities(page: Page, idWatchlist: number): Promise<Record<string, unknown>[]> {
  const group = await getJson<{ securityPositionList?: { securitycurrency: Record<string, unknown> }[] }>(
    page,
    `/api/watchlist/${idWatchlist}`
  );
  return (group.securityPositionList ?? []).map((position) => position.securitycurrency);
}

/** '2026-07-24' → '24.07.26', the de-CH short form the picker renders and parses. */
function toDeChDate(iso: string): string {
  const [year, month, day] = iso.split('-');
  return `${day}.${month}.${year.slice(2)}`;
}

function toIso(date: Date): string {
  const pad = (value: number): string => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/**
 * An opening date the backend accepts for every mode: yesterday, which is a completed closing day and lies after the
 * first transaction of the fixture tenant. Deriving it rather than hard-coding a date also keeps copy mode meaningful,
 * because every transaction the earlier specs created is then inside the copied window.
 */
function openingDateIso(daysBack = 1): string {
  const date = new Date();
  date.setDate(date.getDate() - daysBack);
  return toIso(date);
}

/** Opens the create dialog from the strategy node and waits until its own initialization has settled. */
async function openCreateDialog(page: Page, strategyName: string): Promise<Locator> {
  const node = strategyNode(page, strategyName).locator(':scope > .p-tree-node-content');
  const dialog = page.locator('.p-dialog:visible').last();
  // The menu command loads the portfolios and the form definition before it opens the dialog. Against the dev server
  // one of those requests is occasionally aborted with status 0, which shows an error toast and no dialog, so the
  // command is repeated until the dialog appears.
  await expect(async () => {
    if (!(await dialog.isVisible())) {
      await openTreeContextMenu(page, node, RX.createSimulation);
    }
    await dialog.waitFor({ state: 'visible', timeout: 5_000 });
  }).toPass({ timeout: 45_000 });
  // ngAfterViewInit assigns both values in a setTimeout; typing before that lands would be overwritten.
  await expect(dialog.locator('#tenantName')).toHaveValue(strategyName, { timeout: 10_000 });
  await expect(dialog.locator('select#initializationMode')).toHaveValue('COPY_PORTFOLIO', { timeout: 10_000 });
  return dialog;
}

/** Types into a p-datepicker, which ignores a value that arrives without a preceding keydown. */
async function typeOpeningDate(dialog: Locator, iso: string): Promise<void> {
  const deCh = toDeChDate(iso);
  const input = dialog.locator('#simulationStartDate input').first();
  // A dialog opened right after another one is still settling its focus, so keystrokes can miss the picker. The whole
  // entry is therefore repeated until the value sticks; stray keys are harmless because the name is filled afterwards.
  await expect(async () => {
    await input.click();
    await expect(input).toBeFocused({ timeout: 1_000 });
    await input.press('Control+a');
    await input.press('Backspace');
    await input.pressSequentially(deCh, { delay: 20 });
    await input.blur();
    await expect(input, `opening date after typing "${deCh}"`).toHaveValue(deCh, { timeout: 2_000 });
  }).toPass({ timeout: 30_000 });
}

/**
 * Enters the name and the opening date of an environment, date first. The picker's clearing keystrokes can reach the
 * field that was focused before it - a trace showed the Backspace meant for the date deleting the last letter of the
 * name - whereas `fill()` replaces the whole value at once, so filling the name last keeps it intact.
 */
async function enterNameAndDate(dialog: Locator, name: string, iso: string): Promise<void> {
  await typeOpeningDate(dialog, iso);
  const nameInput = dialog.locator('#tenantName');
  await nameInput.fill(name);
  await expect(nameInput).toHaveValue(name);
  await expect(dialog.locator('#simulationStartDate input').first()).toHaveValue(toDeChDate(iso));
}

/** Types into a p-inputNumber, which updates its model from key events only. */
async function typeCashBalance(dialog: Locator, value: number): Promise<Locator> {
  const host = dialog.locator('[id^="cashBalance_"]').first();
  await expect(host).toBeVisible();
  const input = host.locator('input').first();
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(String(value), { delay: 20 });
  await input.press('Tab');
  await expect(input, 'manual cash balance after typing').not.toHaveValue('');
  return host;
}

async function selectMode(dialog: Locator, mode: string): Promise<void> {
  const select = dialog.locator('select#initializationMode');
  await select.selectOption(mode);
  await select.dispatchEvent('change');
  await expect(select).toHaveValue(mode);
}

/** Submits and returns the parsed creation response. */
async function submitAndExpectCreation(page: Page, dialog: Locator): Promise<ApiSimulation> {
  const created = page.waitForResponse(
    (response) => response.url().endsWith('/api/tenant/simulation') && response.request().method() === 'POST'
  );
  await dialog.getByRole('button', { name: RX.save }).click();
  const response = await created;
  expect(response.ok(), await response.text()).toBeTruthy();
  await expect(dialog).toBeHidden({ timeout: 15_000 });
  return response.json() as Promise<ApiSimulation>;
}

/** The tree node of one environment, labelled "<name> — <date> / <mode>". */
function simulationNode(page: Page, name: string): Locator {
  return strategyNode(page, PREFIX)
    .getByRole('treeitem', { name: new RegExp(`^${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`) })
    .first()
    .locator(':scope > .p-tree-node-content');
}

/**
 * Returns the node of one environment once it is on screen. Every tree refresh rebuilds the strategy node collapsed,
 * and the refresh that follows a creation can replace a node that was just expanded, so expanding and finding are
 * retried together until both hold.
 */
async function revealSimulationNode(page: Page, name: string): Promise<Locator> {
  const node = simulationNode(page, name);
  await expect(async () => {
    await expandTreeNode(strategyNode(page, PREFIX));
    await expect(node).toBeVisible({ timeout: 2_000 });
  }).toPass({ timeout: 30_000 });
  return node;
}

/**
 * Waits until the client operates under the token of the environment. The switch writes the new JWT and tenant id to
 * sessionStorage only after the backend has authorized it and then reloads the application, so the wait has two parts:
 * the navigation has to settle before anything may be evaluated in the page, and the stored tenant id is what proves
 * the takeover. `page.evaluate` throws while the reload is in flight, which the poll swallows and retries.
 */
async function awaitSwitchInto(page: Page, idTenant: number): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await expect
    .poll(
      async () => {
        try {
          return await page.evaluate(() => sessionStorage.getItem('idTenant'));
        } catch {
          return null;
        }
      },
      { timeout: 30_000 }
    )
    .toBe(String(idTenant));
}

/**
 * Returns the session to the user's own tenant. The teardown deletes through REST with whatever token the session
 * holds, and the account- and hierarchy-level endpoints it uses are refused from inside an environment, so a test that
 * switched has to come back before it cleans up.
 */
async function returnHome(page: Page): Promise<void> {
  const mainIdTenant = await page.evaluate(() => sessionStorage.getItem('mainIdTenant'));
  if (mainIdTenant === null) {
    return;
  }
  await loginAsFixtureUser(page, OWNER);
}

test.describe('simulation opening', () => {
  let strategyName: string;
  let openingDate: string;

  test.beforeEach(async ({ page }) => {
    test.setTimeout(180_000);
    await loginAsFixtureUser(page, OWNER);
    await cleanup(page);

    const headers = await authHeaders(page);
    openingDate = openingDateIso();
    strategyName = PREFIX;

    // The copied watchlist must carry a member, otherwise a broken watchlist copy would still look correct.
    const existing = await getJson<ApiWatchlist[]>(page, '/api/watchlist/tenant');
    let donor: ApiWatchlist | undefined;
    let donated: Record<string, unknown> | undefined;
    for (const candidate of existing.filter((list) => list.name !== PREFIX)) {
      const securities = await watchlistSecurities(page, candidate.idWatchlist);
      if (securities.length > 0) {
        donor = candidate;
        donated = securities[0];
        break;
      }
    }
    expect(donor, 'an existing non-empty watchlist to take a member from').toBeTruthy();
    expect(donated, `a security on watchlist "${donor!.name}"`).toBeTruthy();

    const watchlist = await page.request.post('/api/watchlist', { headers, data: { name: PREFIX } });
    expect(watchlist.ok(), await watchlist.text()).toBeTruthy();
    const idWatchlist = ((await watchlist.json()) as ApiWatchlist).idWatchlist;
    // addSecuritycurrency takes whole instruments, not ids, so the donor's own record is handed straight back.
    const added = await page.request.put(`/api/watchlist/${idWatchlist}/addSecuritycurrency`, {
      headers,
      data: { securityList: [donated!], currencypairList: [] }
    });
    expect(added.ok(), await added.text()).toBeTruthy();

    const top = await page.request.post('/api/algotop/create', {
      headers,
      data: { name: PREFIX, idWatchlist, percentage: 100, assetclassPercentageList: [] }
    });
    expect(top.ok(), await top.text()).toBeTruthy();

    // The tree is built once at load, so it has to be rebuilt before the new strategy node exists. The rule-based
    // trading branch loads strategies and environments together, and one aborted request leaves it empty, so the
    // reload is repeated until the strategy shows up.
    await expect(async () => {
      await page.reload();
      await expect(algoRootNode(page)).toBeVisible({ timeout: 30_000 });
      await expect(strategyNode(page, strategyName)).toBeVisible({ timeout: 10_000 });
    }).toPass({ timeout: 90_000 });
  });

  test.afterEach(async ({ page }) => {
    await returnHome(page);
    await cleanup(page);
  });

  test('copy-portfolio opening copies the dated portfolio and can be entered', async ({ page }) => {
    const dialog = await openCreateDialog(page, strategyName);
    await enterNameAndDate(dialog, `${PREFIX} copy`, openingDate);
    // The balance fields stay in the DOM for every mode; the form layout only hides them outside manual mode.
    await expect(dialog.locator('[id^="cashBalance_"]:visible')).toHaveCount(0);

    const simulation = await submitAndExpectCreation(page, dialog);
    expect(simulation.simulationStartDate).toBe(openingDate);
    expect(simulation.simulationInitializationMode).toBe('COPY_PORTFOLIO');
    await expectToast(page, RX.savedToast);

    // The node states the opening definition, which is what distinguishes two environments of one strategy.
    const node = await revealSimulationNode(page, `${PREFIX} copy`);
    await expect(node).toContainText(openingDate);
    await expect(node).toContainText(RX.copyPortfolio);

    // Entering the environment proves the ownership check of the switch and that the copy produced a real ledger.
    // The simulation listing is not checked in here: it is scoped to the user's home tenant (actualIdTenant) and names
    // the same environments from inside one. The copied watchlist below only exists in the environment.
    await openTreeContextMenu(page, node, RX.switchToSimulation);
    await awaitSwitchInto(page, simulation.idTenant);
    const copied = await getJson<ApiWatchlist[]>(page, '/api/watchlist/tenant');
    expect(copied.map((list) => list.name)).toEqual([PREFIX]);
    expect(await watchlistSecurities(page, copied[0].idWatchlist)).toHaveLength(1);
  });

  test('manual-cash opening books exactly the entered balance on the opening date', async ({ page }) => {
    const dialog = await openCreateDialog(page, strategyName);
    await enterNameAndDate(dialog, `${PREFIX} manual`, openingDate);
    await selectMode(dialog, 'MANUAL_CASH');
    await typeCashBalance(dialog, 1234);

    const simulation = await submitAndExpectCreation(page, dialog);
    expect(simulation.simulationStartDate).toBe(openingDate);
    expect(simulation.simulationInitializationMode).toBe('MANUAL_CASH');

    await openTreeContextMenu(page, await revealSimulationNode(page, `${PREFIX} manual`), RX.switchToSimulation);
    await awaitSwitchInto(page, simulation.idTenant);
    const transactions = await getJson<ApiTransaction[]>(page, '/api/transaction');
    expect(transactions).toHaveLength(1);
    expect(transactions[0].transactionType).toBe('DEPOSIT');
    expect(transactions[0].cashaccountAmount).toBe(1234);
    expect(transactions[0].transactionTime.slice(0, 10)).toBe(openingDate);
  });

  test('liquidation shows the opening balances before anything is created', async ({ page }) => {
    const dialog = await openCreateDialog(page, strategyName);
    await enterNameAndDate(dialog, `${PREFIX} liquidation`, openingDate);
    await selectMode(dialog, 'LIQUIDATE_TO_CASH');

    // The first submit only asks for the preview; creation needs a second one on a resolved, error-free preview.
    const preview = page.waitForResponse(
      (response) => response.url().endsWith('/api/tenant/simulation/preview') && response.request().method() === 'POST'
    );
    let created = false;
    page.on('response', (response) => {
      if (response.url().endsWith('/api/tenant/simulation') && response.request().method() === 'POST') {
        created = true;
      }
    });
    await dialog.getByRole('button', { name: RX.save }).click();
    expect((await preview).ok()).toBeTruthy();

    // Either the per-account table or an explanation of what is missing — never a silent success.
    const balances = dialog.locator('table.table tbody tr');
    const problems = dialog.locator('[role="alert"]');
    await expect
      .poll(async () => (await balances.count()) + (await problems.count()), { timeout: 15_000 })
      .toBeGreaterThan(0);
    await expect(dialog).toBeVisible();
    expect(created, 'the first submit must not create an environment').toBe(false);
  });

  test('two environments of one strategy keep different immutable dates', async ({ page }) => {
    const first = await openCreateDialog(page, strategyName);
    await enterNameAndDate(first, `${PREFIX} first`, openingDate);
    const earlier = await submitAndExpectCreation(page, first);

    const second = await openCreateDialog(page, strategyName);
    const otherDate = openingDateIso(2);
    await enterNameAndDate(second, `${PREFIX} second`, otherDate);
    const later = await submitAndExpectCreation(page, second);

    expect(earlier.simulationStartDate).toBe(openingDate);
    expect(later.simulationStartDate).toBe(otherDate);
    await expect(await revealSimulationNode(page, `${PREFIX} first`)).toContainText(openingDate);
    await expect(await revealSimulationNode(page, `${PREFIX} second`)).toContainText(otherDate);

    // The date is not editable through the ordinary tenant write path either.
    const rejected = await page.request.put('/api/tenant', {
      headers: await authHeaders(page),
      data: { idTenant: earlier.idTenant, tenantName: earlier.tenantName, currency: 'CHF', simulationStartDate: null }
    });
    expect(rejected.ok(), 'changing the opening date must be rejected').toBeFalsy();
  });

  test('deleting an environment leaves the shared strategy in place', async ({ page }) => {
    const dialog = await openCreateDialog(page, strategyName);
    await enterNameAndDate(dialog, `${PREFIX} disposable`, openingDate);
    await submitAndExpectCreation(page, dialog);

    const node = await revealSimulationNode(page, `${PREFIX} disposable`);
    await openTreeContextMenu(page, node, RX.deleteSimulation);
    await confirmDelete(page);
    await expectToast(page, RX.deletedToast);

    await expect(simulationNode(page, `${PREFIX} disposable`)).toHaveCount(0, { timeout: 15_000 });
    await expect(strategyNode(page, strategyName)).toBeVisible();
    const remaining = await getJson<ApiAlgoTop[]>(page, '/api/algotop/tenant');
    expect(remaining.filter((top) => top.name === PREFIX)).toHaveLength(1);
  });
});
