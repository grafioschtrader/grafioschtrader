import { expect, Locator, Page, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

import { loginAsFixtureUser } from './helpers';

interface SecurityChangeFixture {
  field: 'idConnectorIntra' | 'urlIntraExtend';
  before: string;
  after: string;
  afterLabel?: string;
}

interface StockexchangeChangeFixture {
  field: 'idTradingCalendarRuleSet';
  before: null;
  afterRuleSetName: string;
  afterRuleSetMic: string;
  afterLabel: string;
}

interface CommonFixture {
  proposerNickname: string;
  reviewerNickname: string;
  noteRequest: string;
  noteAcceptReject: string;
  e2e: 'e';
}

interface SecurityFixture extends CommonFixture {
  entity: 'Security';
  securityName: string;
  isin: string;
  currency: string;
  watchlistName: string;
  changes: SecurityChangeFixture[];
}

interface StockexchangeFixture extends CommonFixture {
  entity: 'Stockexchange';
  mic: string;
  alternateProposerNickname: string;
  changes: StockexchangeChangeFixture[];
}

type ProposeDataChangeFixture = SecurityFixture | StockexchangeFixture;

interface ProposeDataChangeFile {
  _comment?: string[];
  requests: ProposeDataChangeFixture[];
}

interface ApiChangeField {
  field: string;
  valueDesarialized: unknown;
}

interface ApiProposal {
  idProposeRequest: number;
  entity: string;
  dataChangeState: 'OPEN' | 'REJECT' | 'ACCEPTED';
  noteRequest: string;
  noteAcceptReject: string | null;
  idEntity: number;
  proposeChangeFieldList: ApiChangeField[];
}

interface ApiSecurity {
  idSecuritycurrency: number;
  name: string;
  isin: string | null;
  currency: string;
  idConnectorIntra: string | null;
  urlIntraExtend: string | null;
  idProposeRequest: number | null;
  noteRequestOrReject: string | null;
  [key: string]: unknown;
}

interface ApiSecurityPosition {
  securitycurrency: ApiSecurity;
}

interface ApiWatchlist {
  idWatchlist: number;
  name: string;
}

interface ApiWatchlistGroup {
  securityPositionList: ApiSecurityPosition[];
}

interface ApiStockexchange {
  idStockexchange: number;
  createdBy: number;
  mic: string | null;
  name: string;
  idTradingCalendarRuleSet: number | null;
  idIndexUpdCalendar: number | null;
  idProposeRequest: number | null;
  noteRequestOrReject: string | null;
  [key: string]: unknown;
}

interface ApiRuleSetOption {
  key: number | string;
  value: string;
}

interface ApiTaskDataChange {
  idTaskDataChange: number;
  idTask: number | string;
  entity: string;
  idEntity: number;
  progressStateType: number;
}

interface ResolvedScenario {
  idEntity: number;
  proposerNickname: string;
  ownerId?: number;
  ruleSetId?: number;
}

const FIXTURE_PATH = path.resolve(
  __dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/propose_data_change.json'
);
const SECURITY_CONNECTOR_REFRESH_TASK = 35;
const STOCKEXCHANGE_RULE_SET_REFRESH_TASK = 53;
const RUNNING_TASK_STATE = 4;

const RX = {
  baseData: /(Base Data|Basisdaten)/i,
  stockexchange: /^\s*(Stock\s*exchange|Handelsplatz)\s*$/i,
  edit: /^(Bearbeiten|Edit)\b/i,
  editInstrument: /^(Bearbeiten Instrument|Edit instrument)\b/i,
  save: /^(Speichern|Save)$/i,
  saveChangeRequest: /^(Daten.nderungsantrag speichern|Save data change request)$/i,
  accepted: /^\s*(Akzeptiert|Accepted)\s*$/i,
  yourRequests: /^(Ihre Daten.nderungsw.nsche|Your data change requests)$/i
};

function loadScenarios(): ProposeDataChangeFixture[] {
  const fixture = JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf-8')) as ProposeDataChangeFile;
  if (!Array.isArray(fixture.requests) || fixture.requests.length === 0) {
    throw new Error(`Expected at least one request in ${FIXTURE_PATH}`);
  }

  const naturalKeys = new Set<string>();
  return fixture.requests
    .filter((scenario) => scenario.e2e === 'e')
    .map((scenario, index) => {
      const commonInvalid =
        !scenario.proposerNickname ||
        !scenario.reviewerNickname ||
        !scenario.noteRequest ||
        !scenario.noteAcceptReject ||
        !Array.isArray(scenario.changes);
      let naturalKey: string;
      let entityInvalid: boolean;

      if (scenario.entity === 'Security') {
        const fields = scenario.changes.map((candidate) => candidate.field);
        naturalKey = [
          scenario.entity,
          scenario.isin,
          scenario.currency,
          scenario.proposerNickname,
          fields.join(',')
        ].join('|');
        entityInvalid =
          !scenario.securityName ||
          !scenario.isin ||
          !scenario.currency ||
          !scenario.watchlistName ||
          scenario.changes.length !== 2 ||
          !fields.includes('idConnectorIntra') ||
          !fields.includes('urlIntraExtend') ||
          scenario.changes.some((candidate) => !candidate.before || !candidate.after);
      } else if (scenario.entity === 'Stockexchange') {
        const ruleSet = scenario.changes[0];
        naturalKey = [
          scenario.entity,
          scenario.mic,
          scenario.proposerNickname,
          scenario.alternateProposerNickname,
          ruleSet?.field
        ].join('|');
        entityInvalid =
          !scenario.mic ||
          !scenario.alternateProposerNickname ||
          scenario.alternateProposerNickname === scenario.proposerNickname ||
          scenario.changes.length !== 1 ||
          ruleSet?.field !== 'idTradingCalendarRuleSet' ||
          ruleSet?.before !== null ||
          !ruleSet?.afterRuleSetName ||
          !ruleSet?.afterRuleSetMic ||
          !ruleSet?.afterLabel;
      } else {
        throw new Error(`Unsupported proposal entity in fixture row ${index + 1}: ${(scenario as any).entity}`);
      }

      if (commonInvalid || entityInvalid) {
        throw new Error(`Invalid proposal fixture row ${index + 1} in ${FIXTURE_PATH}`);
      }
      if (naturalKeys.has(naturalKey)) {
        throw new Error(`Duplicate proposal natural key in ${FIXTURE_PATH}: ${naturalKey}`);
      }
      naturalKeys.add(naturalKey);
      return scenario;
    });
}

function exactText(value: string): RegExp {
  return new RegExp(`^\\s*${value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`);
}

function securityChange(scenario: SecurityFixture, field: SecurityChangeFixture['field']): SecurityChangeFixture {
  const match = scenario.changes.find((candidate) => candidate.field === field);
  if (!match) {
    throw new Error(`Missing ${field} in proposal fixture for ${scenario.isin}/${scenario.currency}`);
  }
  return match;
}

function ruleSetChange(scenario: StockexchangeFixture): StockexchangeChangeFixture {
  return scenario.changes[0];
}

function scenarioName(scenario: ProposeDataChangeFixture): string {
  return scenario.entity === 'Security' ? scenario.securityName : scenario.mic;
}

async function loginClean(page: Page, nickname: string): Promise<void> {
  await page.goto('/login');
  await page.evaluate(() => sessionStorage.clear());
  await loginAsFixtureUser(page, nickname);
}

async function authHeaders(page: Page): Promise<Record<string, string>> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  return { 'x-auth-token': token! };
}

async function currentUserId(page: Page): Promise<number> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  const payload = JSON.parse(Buffer.from(token!.split('.')[1], 'base64url').toString('utf-8')) as { idUser?: number };
  expect(Number.isInteger(payload.idUser), 'numeric idUser in JWT').toBeTruthy();
  return payload.idUser!;
}

async function expectOk(response: Awaited<ReturnType<Page['request']['get']>>, operation: string): Promise<void> {
  expect(response.ok(), `${operation}: ${response.status()} ${await response.text()}`).toBeTruthy();
}

async function resolveSecurityFromWatchlist(page: Page, scenario: SecurityFixture): Promise<ApiSecurity> {
  const headers = await authHeaders(page);
  const watchlistsResponse = await page.request.get('/api/watchlist/tenant', { headers });
  await expectOk(watchlistsResponse, `loading watchlists of ${scenario.proposerNickname}`);
  const watchlists = (await watchlistsResponse.json()) as ApiWatchlist[];
  const matchingWatchlists = watchlists.filter((watchlist) => watchlist.name === scenario.watchlistName);
  expect(matchingWatchlists, `watchlist ${scenario.watchlistName}`).toHaveLength(1);

  const groupResponse = await page.request.get(`/api/watchlist/${matchingWatchlists[0].idWatchlist}`, { headers });
  await expectOk(groupResponse, `loading watchlist ${scenario.watchlistName}`);
  const group = (await groupResponse.json()) as ApiWatchlistGroup;
  const securities = group.securityPositionList
    .map((position) => position.securitycurrency)
    .filter((security) => security.isin === scenario.isin && security.currency === scenario.currency);
  expect(securities, `security ${scenario.isin}/${scenario.currency} in ${scenario.watchlistName}`).toHaveLength(1);
  return securities[0];
}

async function readSecurity(page: Page, idSecuritycurrency: number): Promise<ApiSecurity> {
  const response = await page.request.get(`/api/security/${idSecuritycurrency}`, { headers: await authHeaders(page) });
  await expectOk(response, `loading Security ${idSecuritycurrency}`);
  return response.json() as Promise<ApiSecurity>;
}

async function readStockexchange(page: Page, mic: string): Promise<ApiStockexchange> {
  const response = await page.request.get('/api/stockexchange?includeNameOfCalendarIndex=false', {
    headers: await authHeaders(page)
  });
  await expectOk(response, `loading Stockexchange ${mic}`);
  const matches = ((await response.json()) as ApiStockexchange[]).filter((candidate) => candidate.mic === mic);
  expect(matches, `Stockexchange ${mic}`).toHaveLength(1);
  return matches[0];
}

async function resolveRuleSetId(page: Page, scenario: StockexchangeFixture): Promise<number> {
  const change = ruleSetChange(scenario);
  const expectedNaturalLabel = `${change.afterRuleSetName} (${change.afterRuleSetMic})`;
  expect(change.afterLabel).toBe(expectedNaturalLabel);
  const response = await page.request.get('/api/tradingcalendarruleset/options', {
    headers: await authHeaders(page)
  });
  await expectOk(response, `loading trading calendar rule set ${expectedNaturalLabel}`);
  const matches = ((await response.json()) as ApiRuleSetOption[]).filter(
    (candidate) => candidate.value === expectedNaturalLabel
  );
  expect(matches, `trading calendar rule set ${expectedNaturalLabel}`).toHaveLength(1);
  const id = Number(matches[0].key);
  expect(Number.isInteger(id) && id > 0, `numeric rule-set id for ${expectedNaturalLabel}`).toBeTruthy();
  return id;
}

async function resolveScenario(page: Page, scenario: ProposeDataChangeFixture): Promise<ResolvedScenario> {
  if (scenario.entity === 'Security') {
    const security = await resolveSecurityFromWatchlist(page, scenario);
    return { idEntity: security.idSecuritycurrency, proposerNickname: scenario.proposerNickname };
  }
  const stockexchange = await readStockexchange(page, scenario.mic);
  expect(Number.isInteger(stockexchange.createdBy), `owner id of Stockexchange ${scenario.mic}`).toBeTruthy();
  return {
    idEntity: stockexchange.idStockexchange,
    ownerId: stockexchange.createdBy,
    proposerNickname: scenario.proposerNickname,
    ruleSetId: await resolveRuleSetId(page, scenario)
  };
}

async function readOwnProposals(page: Page): Promise<ApiProposal[]> {
  const response = await page.request.get('/api/proposechangeentity', { headers: await authHeaders(page) });
  await expectOk(response, 'loading own data change requests');
  return response.json() as Promise<ApiProposal[]>;
}

function expectedProposalValues(
  scenario: ProposeDataChangeFixture,
  resolved: ResolvedScenario
): Record<string, unknown> {
  if (scenario.entity === 'Security') {
    return Object.fromEntries(scenario.changes.map((candidate) => [candidate.field, candidate.after]));
  }
  expect(resolved.ruleSetId, `resolved rule-set id for ${scenario.mic}`).toBeTruthy();
  return { idTradingCalendarRuleSet: String(resolved.ruleSetId) };
}

function proposalMatchesScenario(
  proposal: ApiProposal,
  scenario: ProposeDataChangeFixture,
  resolved: ResolvedScenario
): boolean {
  if (proposal.entity !== scenario.entity || proposal.idEntity !== resolved.idEntity) {
    return false;
  }
  return Object.entries(expectedProposalValues(scenario, resolved)).every(([field, value]) =>
    proposal.proposeChangeFieldList.some(
      (actualChange) => actualChange.field === field && actualChange.valueDesarialized === value
    )
  );
}

async function deleteMatchingProposals(
  page: Page,
  scenario: ProposeDataChangeFixture,
  resolved: ResolvedScenario
): Promise<void> {
  const headers = await authHeaders(page);
  const matches = (await readOwnProposals(page)).filter((proposal) =>
    proposalMatchesScenario(proposal, scenario, resolved)
  );
  for (const proposal of matches) {
    const response = await page.request.delete(`/api/proposechangeentity/${proposal.idProposeRequest}`, { headers });
    await expectOk(response, `deleting proposal ${proposal.idProposeRequest}`);
  }
  expect(
    (await readOwnProposals(page)).filter((proposal) => proposalMatchesScenario(proposal, scenario, resolved)),
    `matching proposals remaining for ${resolved.proposerNickname}`
  ).toHaveLength(0);
}

async function restoreBaseline(
  page: Page,
  scenario: ProposeDataChangeFixture,
  resolved: ResolvedScenario
): Promise<void> {
  if (scenario.entity === 'Security') {
    const security = await readSecurity(page, resolved.idEntity);
    const connector = securityChange(scenario, 'idConnectorIntra');
    const url = securityChange(scenario, 'urlIntraExtend');
    if (security.idConnectorIntra === connector.before && security.urlIntraExtend === url.before) {
      return;
    }
    security.idConnectorIntra = connector.before;
    security.urlIntraExtend = url.before;
    security.idProposeRequest = null;
    security.noteRequestOrReject = null;
    const response = await page.request.put('/api/security', { headers: await authHeaders(page), data: security });
    await expectOk(response, `restoring ${scenario.isin}/${scenario.currency}`);
    const restored = await readSecurity(page, resolved.idEntity);
    expect(restored.idConnectorIntra).toBe(connector.before);
    expect(restored.urlIntraExtend).toBe(url.before);
    return;
  }

  const stockexchange = await readStockexchange(page, scenario.mic);
  if (stockexchange.idTradingCalendarRuleSet === ruleSetChange(scenario).before) {
    return;
  }
  stockexchange.idTradingCalendarRuleSet = null;
  stockexchange.idProposeRequest = null;
  stockexchange.noteRequestOrReject = null;
  const response = await page.request.put('/api/stockexchange', {
    headers: await authHeaders(page),
    data: stockexchange
  });
  await expectOk(response, `restoring Stockexchange ${scenario.mic}`);
  expect((await readStockexchange(page, scenario.mic)).idTradingCalendarRuleSet).toBeNull();
}

async function deleteRefreshTasks(
  page: Page,
  scenario: ProposeDataChangeFixture,
  resolved: ResolvedScenario
): Promise<void> {
  const idTask = scenario.entity === 'Security' ? SECURITY_CONNECTOR_REFRESH_TASK : STOCKEXCHANGE_RULE_SET_REFRESH_TASK;
  const headers = await authHeaders(page);
  const response = await page.request.get(`/api/taskdatachange?idTasks=${idTask}`, { headers });
  await expectOk(response, `loading ${scenario.entity} refresh tasks`);
  const tasks = (await response.json()) as ApiTaskDataChange[];
  for (const task of tasks.filter(
    (candidate) =>
      candidate.entity === scenario.entity &&
      candidate.idEntity === resolved.idEntity &&
      candidate.progressStateType !== RUNNING_TASK_STATE
  )) {
    const deleteResponse = await page.request.delete(`/api/taskdatachange/${task.idTaskDataChange}`, { headers });
    await expectOk(deleteResponse, `deleting refresh task ${task.idTaskDataChange}`);
  }
  const verifyResponse = await page.request.get(`/api/taskdatachange?idTasks=${idTask}`, { headers });
  await expectOk(verifyResponse, `verifying ${scenario.entity} refresh-task cleanup`);
  expect(
    ((await verifyResponse.json()) as ApiTaskDataChange[]).filter(
      (candidate) =>
        candidate.entity === scenario.entity &&
        candidate.idEntity === resolved.idEntity &&
        candidate.progressStateType !== RUNNING_TASK_STATE
    ),
    `non-running ${scenario.entity} refresh tasks remaining after cleanup`
  ).toHaveLength(0);
}

async function resetScenario(page: Page, scenario: ProposeDataChangeFixture): Promise<ResolvedScenario> {
  await loginClean(page, scenario.proposerNickname);
  const resolved = await resolveScenario(page, scenario);
  if (scenario.entity === 'Stockexchange') {
    const candidates = [scenario.proposerNickname, scenario.alternateProposerNickname];
    let nonOwnerNickname: string | undefined;
    for (const nickname of candidates) {
      await loginClean(page, nickname);
      const candidateId = await currentUserId(page);
      await deleteMatchingProposals(page, scenario, resolved);
      if (candidateId !== resolved.ownerId && !nonOwnerNickname) {
        nonOwnerNickname = nickname;
      }
    }
    expect(
      nonOwnerNickname,
      `${scenario.mic} must have a non-owner among proposal candidates ${candidates.join(', ')}`
    ).toBeTruthy();
    resolved.proposerNickname = nonOwnerNickname!;
  } else {
    await deleteMatchingProposals(page, scenario, resolved);
  }
  await loginClean(page, scenario.reviewerNickname);
  await restoreBaseline(page, scenario, resolved);
  await loginClean(page, 'admin');
  await deleteRefreshTasks(page, scenario, resolved);
  return resolved;
}

async function cleanupScenario(
  page: Page,
  scenario: ProposeDataChangeFixture,
  resolved: ResolvedScenario
): Promise<void> {
  await loginClean(page, resolved.proposerNickname);
  await deleteMatchingProposals(page, scenario, resolved);
  await loginClean(page, scenario.reviewerNickname);
  await restoreBaseline(page, scenario, resolved);
  await expectBaseline(page, scenario, resolved);
  await loginClean(page, 'admin');
  await deleteRefreshTasks(page, scenario, resolved);
}

function securityRow(page: Page, scenario: SecurityFixture): Locator {
  return page
    .locator('.data-container p-table tbody tr')
    .filter({ has: page.locator('td').filter({ hasText: exactText(scenario.securityName) }) })
    .filter({ has: page.locator('td').filter({ hasText: exactText(scenario.currency) }) })
    .first();
}

async function openWatchlist(page: Page, scenario: SecurityFixture): Promise<void> {
  const node = page.getByRole('treeitem', { name: scenario.watchlistName, exact: true }).first();
  await node.waitFor({ state: 'visible', timeout: 15_000 });
  await node.click();
  await page.locator('.data-container').first().waitFor({ state: 'visible', timeout: 15_000 });
  await page.waitForTimeout(1500);
}

async function openSecurityDialog(page: Page, scenario: SecurityFixture): Promise<Locator> {
  const row = securityRow(page, scenario);
  await expect(row, `${scenario.securityName}/${scenario.currency} in ${scenario.watchlistName}`).toBeVisible({
    timeout: 15_000
  });
  await row.click();
  await page.waitForTimeout(300);
  await row.click({ button: 'right' });
  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({ state: 'visible', timeout: 5_000 });
  await menu.getByText(RX.editInstrument).first().click();
  const dialog = page.locator('security-edit .p-dialog').first();
  await dialog.waitFor({ state: 'visible', timeout: 10_000 });
  await expect(dialog.locator('select#assetClass option')).not.toHaveCount(0, { timeout: 15_000 });
  await expect(dialog.locator('input#isin')).toHaveValue(scenario.isin);
  return dialog;
}

async function openStockexchangeView(page: Page): Promise<void> {
  const baseDataNode = page.locator('.p-tree-node-content', { hasText: RX.baseData }).first();
  await baseDataNode.waitFor({ state: 'visible', timeout: 15_000 });
  await baseDataNode.dblclick();
  const stockexchangeNode = page.locator('.p-tree-node-content', { hasText: RX.stockexchange }).first();
  await stockexchangeNode.waitFor({ state: 'visible', timeout: 15_000 });
  await stockexchangeNode.click();
  await page.locator('configurable-table tbody tr').first().waitFor({ state: 'visible', timeout: 15_000 });
}

async function openStockexchangeDialog(page: Page, scenario: StockexchangeFixture): Promise<Locator> {
  const row = page
    .locator('configurable-table tbody tr')
    .filter({ has: page.locator('td').filter({ hasText: exactText(scenario.mic) }) })
    .first();
  await expect(row, `Stockexchange ${scenario.mic}`).toBeVisible({ timeout: 15_000 });
  await row.click();
  await page.waitForTimeout(300);
  await row.click({ button: 'right' });
  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({ state: 'visible', timeout: 5_000 });
  await menu.getByText(RX.edit).first().click();
  const dialog = page.locator('stockexchange-edit .p-dialog').first();
  await dialog.waitFor({ state: 'visible', timeout: 10_000 });
  await expect(dialog.locator('select#idTradingCalendarRuleSet option')).not.toHaveCount(0, { timeout: 15_000 });
  return dialog;
}

async function proposeChange(
  page: Page,
  scenario: ProposeDataChangeFixture,
  resolved: ResolvedScenario
): Promise<void> {
  let dialog: Locator;
  if (scenario.entity === 'Security') {
    await openWatchlist(page, scenario);
    dialog = await openSecurityDialog(page, scenario);
    const connector = securityChange(scenario, 'idConnectorIntra');
    const connectorSelect = dialog.locator('select#idConnectorIntra');
    const connectorOption = connectorSelect.locator(`option[value="${connector.after}"]`);
    await expect(connectorOption).toHaveCount(1);
    if (connector.afterLabel) {
      await expect(connectorOption).toHaveText(connector.afterLabel);
    }
    await connectorSelect.selectOption(connector.after);
    await connectorSelect.dispatchEvent('change');
    const urlInput = dialog.locator('input#urlIntraExtend');
    await expect(urlInput).toBeVisible({ timeout: 5_000 });
    await urlInput.fill(securityChange(scenario, 'urlIntraExtend').after);
  } else {
    await openStockexchangeView(page);
    dialog = await openStockexchangeDialog(page, scenario);
    const select = dialog.locator('select#idTradingCalendarRuleSet');
    const option = select.locator(`option[value="${resolved.ruleSetId}"]`);
    await expect(option).toHaveText(ruleSetChange(scenario).afterLabel);
    await select.selectOption(String(resolved.ruleSetId));
    await select.dispatchEvent('change');
  }

  const noteRequest = dialog.locator('textarea#noteRequest');
  await expect(
    noteRequest,
    `${resolved.proposerNickname} must not own ${scenarioName(scenario)}; otherwise the editor opens in direct-save mode`
  ).toBeVisible({ timeout: 5_000 });
  await expect(dialog.getByRole('button', { name: RX.saveChangeRequest })).toBeVisible({ timeout: 5_000 });
  await noteRequest.fill(scenario.noteRequest);
  const saveResponsePromise = page.waitForResponse(
    (response) =>
      response.url().endsWith(`/api/${scenario.entity.toLowerCase()}`) && response.request().method() === 'PUT'
  );
  await dialog.getByRole('button', { name: RX.saveChangeRequest }).click();
  const response = await saveResponsePromise;
  expect(response.ok(), `saving proposal: ${response.status()} ${await response.text()}`).toBeTruthy();
  await dialog.waitFor({ state: 'hidden', timeout: 15_000 });
}

function proposalRow(page: Page, noteRequest: string): Locator {
  return page
    .locator('configurable-table tbody tr')
    .filter({ has: page.locator('td').filter({ hasText: exactText(noteRequest) }) })
    .first();
}

async function openProposalForReview(page: Page, scenario: ProposeDataChangeFixture): Promise<Locator> {
  const responsePromise = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/proposechangeentity/withentity') && response.request().method() === 'GET'
  );
  await page.goto('/mainview/proposeChangeTabMenu/proposerequestforyou');
  const response = await responsePromise;
  expect(response.ok(), `loading requests for reviewer: ${response.status()} ${await response.text()}`).toBeTruthy();
  const row = proposalRow(page, scenario.noteRequest);
  await expect(row, `open proposal '${scenario.noteRequest}'`).toBeVisible({ timeout: 15_000 });
  await row.click();
  await page.waitForTimeout(300);
  await row.click({ button: 'right' });
  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({ state: 'visible', timeout: 5_000 });
  await menu.getByRole('menuitem', { name: RX.edit }).click();
  const selector = scenario.entity === 'Security' ? 'security-edit .p-dialog' : 'stockexchange-edit .p-dialog';
  const dialog = page.locator(selector).first();
  await dialog.waitFor({ state: 'visible', timeout: 10_000 });
  if (scenario.entity === 'Security') {
    await expect(dialog.locator('select#assetClass option')).not.toHaveCount(0, { timeout: 15_000 });
  } else {
    await expect(dialog.locator('select#idTradingCalendarRuleSet option')).not.toHaveCount(0, { timeout: 15_000 });
  }
  return dialog;
}

async function expectBaseline(
  page: Page,
  scenario: ProposeDataChangeFixture,
  resolved: ResolvedScenario
): Promise<void> {
  if (scenario.entity === 'Security') {
    const unchanged = await readSecurity(page, resolved.idEntity);
    expect(unchanged.idConnectorIntra).toBe(securityChange(scenario, 'idConnectorIntra').before);
    expect(unchanged.urlIntraExtend).toBe(securityChange(scenario, 'urlIntraExtend').before);
  } else {
    expect((await readStockexchange(page, scenario.mic)).idTradingCalendarRuleSet).toBeNull();
  }
}

async function expectAccepted(
  page: Page,
  scenario: ProposeDataChangeFixture,
  resolved: ResolvedScenario
): Promise<void> {
  if (scenario.entity === 'Security') {
    const accepted = await readSecurity(page, resolved.idEntity);
    expect(accepted.idConnectorIntra).toBe(securityChange(scenario, 'idConnectorIntra').after);
    expect(accepted.urlIntraExtend).toBe(securityChange(scenario, 'urlIntraExtend').after);
  } else {
    expect((await readStockexchange(page, scenario.mic)).idTradingCalendarRuleSet).toBe(resolved.ruleSetId);
  }
}

for (const scenario of loadScenarios()) {
  test.describe.serial(`propose data change for ${scenarioName(scenario)}`, () => {
    test.use({ viewport: { width: 1600, height: 1200 } });
    let resolved: ResolvedScenario;
    const proposerLabel = scenario.entity === 'Stockexchange' ? 'non-owning limited editor' : scenario.proposerNickname;

    test(`${proposerLabel} proposes the ${scenario.entity} change`, async ({ page }) => {
      resolved = await resetScenario(page, scenario);
      await loginClean(page, resolved.proposerNickname);
      await proposeChange(page, scenario, resolved);
      const matches = (await readOwnProposals(page)).filter((proposal) =>
        proposalMatchesScenario(proposal, scenario, resolved)
      );
      expect(matches, 'one matching proposal after saving').toHaveLength(1);
      expect(matches[0]).toMatchObject({
        entity: scenario.entity,
        dataChangeState: 'OPEN',
        noteRequest: scenario.noteRequest,
        noteAcceptReject: null,
        idEntity: resolved.idEntity
      });
      expect(matches[0].proposeChangeFieldList).toHaveLength(scenario.changes.length);
      await expectBaseline(page, scenario, resolved);
    });

    test(`${scenario.reviewerNickname} accepts the data change request`, async ({ page }) => {
      await loginClean(page, scenario.reviewerNickname);
      const dialog = await openProposalForReview(page, scenario);
      if (scenario.entity === 'Security') {
        await expect(dialog.locator('select#idConnectorIntra')).toHaveValue(
          securityChange(scenario, 'idConnectorIntra').after
        );
        await expect(dialog.locator('input#urlIntraExtend')).toHaveValue(
          securityChange(scenario, 'urlIntraExtend').after
        );
      } else {
        await expect(dialog.locator('select#idTradingCalendarRuleSet')).toHaveValue(String(resolved.ruleSetId));
      }
      await expect(dialog.locator('textarea#noteRequest')).toHaveValue(scenario.noteRequest);
      await expect(dialog.locator('textarea#noteRequest')).toBeDisabled();
      await dialog.locator('textarea#noteAcceptReject').fill(scenario.noteAcceptReject);
      const saveResponsePromise = page.waitForResponse(
        (response) =>
          response.url().endsWith(`/api/${scenario.entity.toLowerCase()}`) && response.request().method() === 'PUT'
      );
      await dialog.getByRole('button', { name: RX.save }).first().click();
      const response = await saveResponsePromise;
      expect(response.ok(), `accepting proposal: ${response.status()} ${await response.text()}`).toBeTruthy();
      await dialog.waitFor({ state: 'hidden', timeout: 15_000 });
      await expect(proposalRow(page, scenario.noteRequest)).toHaveCount(0, { timeout: 15_000 });
      await expectAccepted(page, scenario, resolved);
    });

    test(`${proposerLabel} sees the accepted request`, async ({ page }) => {
      await loginClean(page, resolved.proposerNickname);
      await page.goto('/mainview/proposeChangeTabMenu');
      const responsePromise = page.waitForResponse(
        (response) => response.url().endsWith('/api/proposechangeentity') && response.request().method() === 'GET'
      );
      await page.getByRole('tab', { name: RX.yourRequests }).click();
      const response = await responsePromise;
      expect(response.ok(), `loading own proposals: ${response.status()} ${await response.text()}`).toBeTruthy();
      const row = proposalRow(page, scenario.noteRequest);
      await expect(row, `accepted proposal '${scenario.noteRequest}'`).toBeVisible({ timeout: 15_000 });
      await expect(row).toContainText(scenario.noteAcceptReject);
      await expect(row.locator('td').nth(2)).toHaveText(RX.accepted);
      const matches = (await readOwnProposals(page)).filter((proposal) =>
        proposalMatchesScenario(proposal, scenario, resolved)
      );
      expect(matches).toHaveLength(1);
      expect(matches[0]).toMatchObject({
        dataChangeState: 'ACCEPTED',
        noteRequest: scenario.noteRequest,
        noteAcceptReject: scenario.noteAcceptReject
      });
      await cleanupScenario(page, scenario, resolved);
    });
  });
}
