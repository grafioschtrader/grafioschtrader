import { expect, Page, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { loginAsFixtureUser } from './helpers';

/**
 * Activation of the baseline dip-buy strategy — the simplest complete rule set the engine supports: buy after a
 * price drop, sell the whole position at a gain target, and leave it through a hard stop if it falls instead.
 *
 * A strategy is only allowed to become `activatable` when the engine can actually execute every part of it. This
 * spec is about that gate, not about the trading result. It saves the configuration, then tries to activate four
 * variations of it and expects three of them to be refused:
 *
 * - Both downside variants enabled at once — selling at a loss and buying more at a loss are contradictory
 *   instructions for the same trigger, so the engine cannot decide which one to follow.
 * - An intraday timeframe — evaluation runs on end-of-day prices, so anything below `1d` has no data behind it.
 * - An unknown configuration key — the schema is closed on purpose, so that a typo in a field name fails loudly
 *   instead of being silently ignored while the user believes the setting is in effect.
 *
 * The fourth variation is the same ambiguous configuration saved with `activatable: false`. It is accepted, because
 * a draft is allowed to describe something the engine cannot run yet; only activation is gated.
 *
 * Beyond the gate, two things are checked because they are what the user sees of an active strategy: the instrument
 * offers it as an assignment for a manual fill, and the buy dialog renders that assignment as a selectable option.
 * The dialog is closed again — this spec books no transaction.
 *
 * How it talks to the backend: the configuration is written through REST rather than through the YAML editor,
 * because the editor is covered by the average-down spec and a rejected activation has to be observed as an HTTP
 * status, not as a toast. The requests carry the JWT taken out of the logged-in page's `sessionStorage`, so they
 * act as the same user and tenant as the browser session.
 *
 * The spec owns exactly one watchlist and one AlgoTop, both named {@link name}, and deletes them at the start of
 * the run as well as at the end, so a previous run that died halfway does not break the retry. It creates no
 * instrument: it borrows an existing one identified by ISIN from any other watchlist of the tenant.
 *
 * Prerequisites from earlier specs: the ALGO feature must be enabled, and a watchlist holding at least one
 * instrument with an ISIN must exist — 040-create-watchlist together with 050-create-security provide it.
 */

/** Owner marker of everything this spec creates; the watchlist name column allows 25 characters. */
const name = 'Dip strategy e2e';

const configuration = JSON.parse(
  readFileSync(
    resolve(__dirname, '../../backend/grafioschtrader-server/src/test/resources/testdata/mean-reversion-strategy.json'),
    'utf8'
  )
);

/** The REST calls act as the logged-in browser session, so they take that session's token. */
async function headers(page: Page) {
  return { 'x-auth-token': (await page.evaluate(() => sessionStorage.getItem('jwt')))! };
}

/** Removes the spec's own hierarchy and watchlist; the AlgoTop has to go first, it references the watchlist. */
async function cleanup(page: Page) {
  const auth = await headers(page);
  const response = await page.request.get('/api/algotop/tenant', { headers: auth });
  expect(response.ok(), await response.text()).toBeTruthy();
  for (const top of await response.json())
    if (top.name === name) {
      const deleted = await page.request.delete(`/api/algotop/${top.idAlgoAssetclassSecurity}`, { headers: auth });
      expect(deleted.ok(), await deleted.text()).toBeTruthy();
    }
  const lists = await page.request.get('/api/watchlist/tenant', { headers: auth });
  for (const list of await lists.json())
    if (list.name === name) {
      const deleted = await page.request.delete(`/api/watchlist/${list.idWatchlist}`, { headers: auth });
      expect(deleted.ok(), await deleted.text()).toBeTruthy();
    }
}

test('a dip strategy activates on daily data, stays a draft when ambiguous and refuses unsafe activation', async ({
  page,
  request
}) => {
  const info = await request.get(`${process.env.E2E_BACKEND_URL ?? 'http://localhost:8080'}/api/gtinfo`);
  expect((await info.json()).databaseName, 'Never log into the production application in an E2E test').toBe(
    'grafioschtrader_t'
  );
  await loginAsFixtureUser(page, 'alledit');
  await cleanup(page);
  const auth = await headers(page);

  const list = await page.request.post('/api/watchlist', { headers: auth, data: { name } });
  expect(list.ok(), await list.text()).toBeTruthy();
  const idWatchlist = (await list.json()).idWatchlist;

  // An instrument is borrowed rather than created: the strategy needs something with prices behind it, and every
  // watchlist of the tenant except this spec's own one is a candidate donor.
  const donors = await page.request.get('/api/watchlist/tenant', { headers: auth });
  let instrument: any;
  for (const donor of await donors.json()) {
    if (donor.name === name) continue;
    const response = await page.request.get(`/api/watchlist/${donor.idWatchlist}`, { headers: auth });
    const rows = (await response.json()).securityPositionList ?? [];
    instrument = rows.map((r: any) => r.securitycurrency).find((s: any) => s?.isin);
    if (instrument) break;
  }
  expect(instrument, 'an existing instrument identified by ISIN').toBeTruthy();
  const added = await page.request.put(`/api/watchlist/${idWatchlist}/addSecuritycurrency`, {
    headers: auth,
    data: { securityList: [instrument], currencypairList: [] }
  });
  expect(added.ok(), await added.text()).toBeTruthy();

  // The AlgoTop is the root of the hierarchy; its percentage is the share of equity the whole strategy may use.
  const created = await page.request.post('/api/algotop/create', {
    headers: auth,
    data: { name, idWatchlist, percentage: 100, assetclassPercentageList: [] }
  });
  expect(created.ok(), await created.text()).toBeTruthy();
  const top = await created.json();

  // The baseline configuration is executable as it stands, so it is accepted as activatable straight away.
  const strategy = {
    idAlgoAssetclassSecurity: top.idAlgoAssetclassSecurity,
    algoStrategyImplementations: 'AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP',
    activatable: true,
    strategyConfig: JSON.stringify(configuration)
  };
  const saved = await page.request.post('/api/algostrategy', { headers: auth, data: strategy });
  expect(saved.ok(), await saved.text()).toBeTruthy();
  const active = await saved.json();
  expect(active.activatable).toBe(true);

  const update = (config: unknown, activatable: boolean) =>
    page.request.put('/api/algostrategy', {
      headers: auth,
      data: { ...active, activatable, strategyConfig: JSON.stringify(config) }
    });

  // Selling at a loss and buying more at a loss cannot both apply to the same trigger.
  const ambiguous = structuredClone(configuration);
  ambiguous.downside_management.variant_B_average_down.enabled = true;
  const rejected = await update(ambiguous, true);
  expect(rejected.status(), 'two enabled downside variants').toBeGreaterThanOrEqual(400);
  expect(rejected.status(), 'the configuration is wrong, not the server').toBeLessThan(500);

  // The very same configuration is storable as long as nobody claims the engine could run it.
  const draft = await update(ambiguous, false);
  expect(draft.ok(), await draft.text()).toBeTruthy();
  expect((await draft.json()).activatable).toBe(false);

  // Evaluation works on end-of-day prices; an intraday timeframe has no data source behind it.
  const intraday = structuredClone(configuration);
  intraday.data.timeframe = '1h';
  expect((await update(intraday, true)).status(), 'intraday timeframe').toBe(400);

  // The schema is closed, so a misspelled option is an error rather than a setting that silently does nothing.
  const unknown = { ...configuration, unsupported_option: true };
  expect((await update(unknown, true)).status(), 'unknown configuration key').toBe(400);

  const restored = await update(configuration, true);
  expect(restored.ok(), await restored.text()).toBeTruthy();

  // An active strategy is what an instrument offers when a manual fill has to be attributed to it.
  const assignments = await page.request.get(`/api/algoalerts/strategies/${instrument.idSecuritycurrency}`, {
    headers: auth
  });
  expect(assignments.ok(), await assignments.text()).toBeTruthy();
  expect(await assignments.json()).toContainEqual({
    idAlgoRuleStrategy: active.idAlgoRuleStrategy,
    name: configuration.strategy_name
  });

  // The tree is built once at load, so it has to be rebuilt before the new nodes exist.
  await page.reload();
  await page.getByRole('treeitem', { name, exact: true }).first().click();
  const row = page.locator('p-table tbody tr').filter({ hasText: instrument.name }).first();
  await row.click();
  await row.click({ button: 'right' });
  await page
    .locator('[role="menu"]:visible')
    .getByText(/^(Buy|Kaufen)\.\.\.$/i)
    .click();

  // The buy dialog offers the assignment exactly once and is closed again — no transaction is booked.
  const dialog = page.locator('transaction-security-edit .p-dialog:visible');
  const assignment = dialog.locator('select#idAlgoStrategy');
  await expect(assignment).toBeVisible();
  await expect(assignment.locator('option', { hasText: configuration.strategy_name })).toHaveCount(1);
  await assignment.selectOption(String(active.idAlgoRuleStrategy));
  await expect(assignment).toHaveValue(String(active.idAlgoRuleStrategy));
  await dialog.locator('.p-dialog-header button').click();
  await expect(dialog).not.toBeVisible();

  // The diagnostics endpoint has to answer for a hierarchy that has never traded; an empty list is a valid answer.
  const diagnostics = await page.request.get('/api/algoalerts/trading', { headers: auth });
  expect(diagnostics.ok(), await diagnostics.text()).toBeTruthy();
  expect(Array.isArray(await diagnostics.json())).toBe(true);

  await cleanup(page);
});
