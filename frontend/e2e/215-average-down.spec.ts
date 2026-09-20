import { expect, Page, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { dump } from 'js-yaml';
import { loginAsFixtureUser } from './helpers';

/**
 * Activation of the averaging variant — the downside rule that answers a falling price by buying more instead of
 * selling out, in a bounded number of steps, recomputing the average cost after each one.
 *
 * A strategy has exactly one downside behaviour. Averaging is therefore not an addition to the stop-loss variant
 * but its alternative, and most of what has to be refused follows from that and from the bound: an averaging rule
 * that never stops adding, or that leaves the average cost stale, is a rule whose later gain and loss triggers would
 * be measured against a price nobody paid. The spec walks that validation from the outside, one defect at a time:
 *
 * - *Custom steps* — the step rule accepts the two forms the engine can evaluate, a fixed percentage drop per step
 *   and an indicator-driven one. `custom` is a schema placeholder with no implementation behind it.
 * - *Disabled cost reconciliation* — after an addition the average cost has to be recomputed, otherwise every
 *   subsequent trigger refers to the first purchase alone.
 * - *Zero maximum additions* and *a zero step* — an averaging rule that can never add, and one where every price is
 *   a step.
 * - *Two enabled downside variants* — selling at a loss and buying more at a loss are contradictory instructions
 *   for the same trigger.
 * - *An indicator step without indicator confirmation* — an indicator-driven step needs a trigger whose decision
 *   basis actually consults indicators, otherwise the step rule has nothing to read.
 *
 * The counterpart is the accepted draft: the very same rejected configuration is storable with `activatable: false`.
 * A draft is allowed to describe something the engine cannot run yet; only activation is gated.
 *
 * The second half leaves REST and goes through the UI, because this is the spec that covers the YAML editor. Two
 * things about it are worth knowing before changing anything here.
 *
 * 1. The editor is Monaco, and its value cannot be typed or filled — the text is set on the underlying model
 *    through Monaco's own API. The last model in the list is the one the open dialog owns; earlier ones are left
 *    over from dialogs that have already been closed.
 * 2. The stored configuration is JSON while the editor renders YAML, so the edited object is dumped to YAML on the
 *    way in and read back from the rendered YAML afterwards.
 *
 * The tree node is addressed with `.last()`: the name belongs to both the watchlist and the AlgoTop, so it appears
 * twice, and the hierarchy branch — the one carrying the strategy rows — is the second occurrence.
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
const name = 'Average-down e2e';

const configuration = JSON.parse(
  readFileSync(
    resolve(__dirname, '../../backend/grafioschtrader-server/src/test/resources/testdata/average-down-strategy.json'),
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

test('averaging down activates, refuses unsupported additions and survives an edit in the YAML editor', async ({
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

  // The fixture combines averaging on the downside with a tranche exit on the upside; both are executable, so the
  // strategy is activatable straight away and comes back out of the backend unchanged.
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

  await rejects('custom steps', (c) => (c.downside_management.variant_B_average_down.add_step_rule.type = 'custom'));
  await rejects(
    'disabled cost reconciliation',
    (c) => (c.downside_management.variant_B_average_down.recalculate_avg_cost = false)
  );
  await rejects('zero maximum additions', (c) => (c.downside_management.variant_B_average_down.max_adds = 0));
  await rejects('zero step', (c) => (c.downside_management.variant_B_average_down.add_step_rule.drop_pct_step = 0));
  await rejects('two enabled downside variants', (c) => (c.downside_management.variant_A_sell_loss.enabled = true));
  await rejects(
    'indicator step without confirmation',
    (c) => (c.downside_management.variant_B_average_down.add_step_rule.type = 'indicator_based')
  );

  // What may not be activated may still be stored: a draft is allowed to outrun the engine.
  const draft = await update(
    (c) => (c.downside_management.variant_B_average_down.add_step_rule.type = 'custom'),
    false
  );
  expect(draft.ok(), await draft.text()).toBeTruthy();
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

  // The tree is built once at load, so it has to be rebuilt before the new nodes exist. The name occurs twice —
  // watchlist and AlgoTop — and the hierarchy branch carrying the strategy rows is the second one.
  await page.reload();
  await expect(page.getByRole('treeitem', { name, exact: true }).first()).toBeVisible();
  await page.getByRole('treeitem', { name, exact: true }).last().click();
  const strategyRow = page
    .locator('p-treetable tbody tr')
    .filter({ hasText: /Mean.Reversion/i })
    .first();
  await strategyRow.click();
  await expect(page.locator('.strategy-yaml-display'), 'the selected strategy renders its configuration').toContainText(
    'B_average_down'
  );

  // Editing goes through Monaco, whose value cannot be typed: the text is set on the model the open dialog owns.
  await strategyRow.click({ button: 'right' });
  await page
    .locator('[role="menu"]:visible')
    .getByText(/^(Edit|Bearbeiten)$/)
    .click();
  const dialog = page.locator('algo-strategy-edit .p-dialog:visible');
  await dialog.locator('.monaco-editor').waitFor({ state: 'attached' });
  const edited = structuredClone(configuration);
  edited.downside_management.variant_B_average_down.max_adds = 4;
  await page.evaluate((yaml) => {
    // The editor renders YAML while the backend stores JSON; the last model belongs to the dialog just opened.
    const models = (window as any).monaco.editor.getModels();
    models[models.length - 1].setValue(yaml);
  }, dump(edited));

  // The click and the PUT are awaited together, so the assertion cannot run before the save has been answered.
  const [save] = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/algostrategy') && r.request().method() === 'PUT', {
      timeout: 15000
    }),
    dialog.getByRole('button', { name: /(Apply|Übernehmen)$/ }).click({ timeout: 15000 })
  ]);
  expect(save.ok(), await save.text()).toBeTruthy();
  await expect(dialog).not.toBeVisible();

  // The edit is only proven once it survives a reload: it has to come back out of the database, not out of the form.
  await page.reload();
  await page.getByRole('treeitem', { name, exact: true }).last().click();
  await strategyRow.click();
  await expect(page.locator('.strategy-yaml-display')).toContainText('max_adds: 4');

  // The diagnostics endpoint has to answer for a hierarchy that has never traded; an empty list is a valid answer.
  const diagnostics = await page.request.get('/api/algoalerts/trading', { headers: auth });
  expect(diagnostics.ok(), await diagnostics.text()).toBeTruthy();

  await cleanup(page);
});
