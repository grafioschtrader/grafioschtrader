import { expect, Page, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { loginAsFixtureUser } from './helpers';

/**
 * Activation of a staged exit — a strategy that does not sell its position in one go but in tranches, each with its
 * own gain trigger, the last one closing whatever is left.
 *
 * A tranche plan is a sequence, and most of the ways it can be wrong are ways in which the sequence stops meaning
 * what it says. The plan is therefore validated as a whole before it may be activated, and this spec walks that
 * validation from the outside, one defect at a time:
 *
 * - *Duplicate tranche identifier* — the identifier is how the engine remembers which tranche has already fired, so
 *   two tranches sharing one would share that memory.
 * - *A tranche closing the remainder before the last one* — everything behind it could never execute.
 * - *A tranche that neither sells a fraction nor the remainder* — it would be a trigger without an action.
 * - *Fractions adding up beyond the whole position* — with the basis `initial_position` every fraction is measured
 *   against the position as it was bought, so the sum may not exceed it.
 * - *An enabled plan without tranches* and *without a sizing basis* — an exit that names no steps, or steps whose
 *   size cannot be resolved.
 * - *A gain trigger carrying indicator rules* and *an indicator trigger without them* — the trigger type decides
 *   which fields are read, so the unread ones would be settings the user believes are in effect and are not.
 *
 * The counterpart is the accepted case in the middle: the same fractions that overflow the initial position are
 * legitimate once the basis is `current_position`, because each one is then measured against what is still held at
 * the time it fires. That contrast is the point of the whole plan model and the reason both cases are here.
 *
 * How it talks to the backend: the configuration is written through REST rather than through the YAML editor,
 * because a rejected activation has to be observed as an HTTP status rather than as a toast; the editor path is
 * covered by the average-down spec. The requests carry the JWT taken out of the logged-in page's `sessionStorage`,
 * so they act as the same user and tenant as the browser session.
 *
 * The spec owns exactly one watchlist and one AlgoTop, both named {@link name}, and deletes them at the start of
 * the run as well as at the end, so a previous run that died halfway does not break the retry. It creates no
 * instrument: it borrows an existing one identified by ISIN from any other watchlist of the tenant. No trade is
 * booked and no message is sent.
 *
 * Prerequisites from earlier specs: the ALGO feature must be enabled, and a watchlist holding at least one
 * instrument with an ISIN must exist — 040-create-watchlist together with 050-create-security provide it.
 */

/** Owner marker of everything this spec creates; the watchlist name column allows 25 characters. */
const name = 'Scale-out e2e';

const configuration = JSON.parse(
  readFileSync(
    resolve(__dirname, '../../backend/grafioschtrader-server/src/test/resources/testdata/scale-out-strategy.json'),
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

test('a tranche exit activates and a plan the engine could not execute is refused', async ({ page, request }) => {
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

  // A consistent plan of tranches is executable, so it is activatable rather than merely storable as a draft, and
  // it comes back out of the backend unchanged.
  const saved = await page.request.post('/api/algostrategy', {
    headers: auth,
    data: {
      idAlgoAssetclassSecurity: top.idAlgoAssetclassSecurity,
      algoStrategyImplementations: 'AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP',
      activatable: true,
      strategyConfig: JSON.stringify(configuration)
    }
  });
  expect(saved.ok(), await saved.text()).toBeTruthy();
  const active = await saved.json();
  expect(active.activatable).toBe(true);
  expect(JSON.parse(active.strategyConfig).profit_management.scale_out_enabled).toBe(true);

  /** Sends a copy of the fixture with one defect applied, so each case starts from the known-good configuration. */
  const update = (mutate: (config: any) => void, activatable = true) => {
    const config = structuredClone(configuration);
    mutate(config);
    return page.request.put('/api/algostrategy', {
      headers: auth,
      data: { ...active, activatable, strategyConfig: JSON.stringify(config) }
    });
  };
  /** A defect is a client error: the configuration is wrong, the server is not. */
  const rejects = async (what: string, mutate: (config: any) => void) => {
    const response = await update(mutate);
    expect(response.status(), what).toBeGreaterThanOrEqual(400);
    expect(response.status(), what).toBeLessThan(500);
  };

  await rejects('duplicate tranche identifier', (c) => (c.profit_management.scale_out_plan[1].id = 't1'));
  await rejects('a tranche closing the remainder before the last one', (c) => {
    delete c.profit_management.scale_out_plan[0].sell_fraction;
    c.profit_management.scale_out_plan[0].sell_remainder = true;
  });
  await rejects('a tranche that neither sells a fraction nor the remainder', (c) => {
    delete c.profit_management.scale_out_plan[0].sell_fraction;
  });
  await rejects('fractions of the initial position adding up beyond the whole position', (c) => {
    c.profit_management.scale_out_plan[1].sell_fraction = 0.8;
  });
  await rejects('an enabled plan without tranches', (c) => (c.profit_management.scale_out_plan = []));
  await rejects('an enabled plan without a sizing basis', (c) => delete c.profit_management.sell_fraction_basis);
  await rejects('a gain trigger carrying indicator rules', (c) => {
    c.profit_management.scale_out_plan[0].trigger.indicator_rules = [
      { type: 'rsi', params: { length: 14, condition: '>', value: 70 } }
    ];
  });
  await rejects('an indicator trigger without rules', (c) => {
    c.profit_management.scale_out_plan[0].trigger.type = 'indicator';
  });

  // The same fractions are fine once each one is measured against what is left at the time.
  const rebased = await update((c) => {
    c.profit_management.sell_fraction_basis = 'current_position';
    c.profit_management.scale_out_plan[1].sell_fraction = 0.8;
  });
  expect(rebased.ok(), await rebased.text()).toBeTruthy();
  const restored = await update(() => {});
  expect(restored.ok(), await restored.text()).toBeTruthy();

  // The activated strategy is the one the instrument offers for a manual fill assignment.
  const assignments = await page.request.get(`/api/algoalerts/strategies/${instrument.idSecuritycurrency}`, {
    headers: auth
  });
  expect(assignments.ok(), await assignments.text()).toBeTruthy();
  expect(await assignments.json()).toContainEqual({
    idAlgoRuleStrategy: active.idAlgoRuleStrategy,
    name: configuration.strategy_name
  });

  // The tree is built once at load, so it has to be rebuilt before the new node exists.
  await page.reload();
  await expect(page.getByRole('treeitem', { name, exact: true }).first()).toBeVisible();

  // The diagnostics endpoint has to answer for a hierarchy that has never traded; an empty list is a valid answer.
  const diagnostics = await page.request.get('/api/algoalerts/trading', { headers: auth });
  expect(diagnostics.ok(), await diagnostics.text()).toBeTruthy();

  await cleanup(page);
});
