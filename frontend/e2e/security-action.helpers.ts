import { expect, Locator, Page, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

import { parseCsvRow } from './helpers';
import { toShortDate } from './portfolio.helpers';

const FIXTURE_PATH = path.resolve(
  __dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/isin_change.csv'
);

const RX = {
  apply: /^(Apply ISIN change|ISIN-Änderung anwenden)$/,
  assignSelected: /^(Assign selected|Selektierte zuweisen)$/,
  create: /^(Create ISIN change|ISIN-Änderung erstellen)/,
  securityAction: /^(Security Action|Wertpapieraktion)$/,
  systemActions: /(System Actions \(ISIN Changes\)|Systemaktionen \(ISIN-Änderungen\))/
};

export interface IsinChangeFixture {
  createNickname: string;
  applyNickname: string;
  isinOld: string;
  isinNew: string;
  actionDate: string;
  note: string;
  fromFactor: number;
  toFactor: number;
  expectedAffectedCount: number;
  e2e: 'd' | 'i' | 'e';
}

export interface ApiSecurityAction {
  idSecurityAction: number;
  isinOld: string;
  isinNew: string;
  actionDate: string;
  note: string;
  fromFactor: number | null;
  toFactor: number | null;
  affectedCount: number;
  appliedCount: number;
}

export interface ApiSecurityActionApplication {
  idSecurityActionApp: number;
  securityAction: ApiSecurityAction;
  reversed: boolean;
}

export interface ApiSecurityActionTreeData {
  systemActions: ApiSecurityAction[];
  appliedByCurrentTenant: Record<string, ApiSecurityActionApplication>;
}

interface ApiMail {
  idMailSendRecv: number;
  sendRecv: 'S' | 'R';
  subject: string;
  message: string;
  idReplyToLocal: number | null;
}

interface ApiMailInbox {
  mailSendRecvList: ApiMail[];
}

/** Loads the manually exported ISIN changes owned by Playwright. */
export function loadE2EIsinChanges(): IsinChangeFixture[] {
  if (!fs.existsSync(FIXTURE_PATH)) {
    console.warn(`Fixture ${FIXTURE_PATH} not found - skipping the Security Action E2E specs.`);
    return [];
  }
  return fs
    .readFileSync(FIXTURE_PATH, 'utf8')
    .split(/\r?\n/)
    .filter((line) => line.trim().length > 0)
    .map((line, index) => parseFixtureRow(line, index + 1))
    .filter((fixture) => fixture.e2e === 'e');
}

/** Opens the Security Action TreeTable from the main navigation tree. */
export async function openSecurityActions(page: Page): Promise<void> {
  const node = page.getByRole('treeitem', { name: RX.securityAction }).first();
  await node.waitFor({ state: 'visible', timeout: 15_000 });
  await node.click();
  await securityActionContainer(page).waitFor({ state: 'visible', timeout: 15_000 });
  await systemActionsRow(page).waitFor({ state: 'visible', timeout: 15_000 });
}

/** Returns the current user's REST representation of the Security Action tree. */
export async function getSecurityActionTree(page: Page): Promise<ApiSecurityActionTreeData> {
  const response = await page.request.get('/api/securityaction/tree', { headers: await authHeaders(page) });
  expect(response.ok(), `loading Security Actions: ${await response.text()}`).toBeTruthy();
  return (await response.json()) as ApiSecurityActionTreeData;
}

/** Finds the action identified by the fixture's stable ISIN pair and action date. */
export function findSecurityAction(
  tree: ApiSecurityActionTreeData,
  fixture: IsinChangeFixture
): ApiSecurityAction | undefined {
  return (tree.systemActions ?? []).find(
    (action) =>
      action.isinOld === fixture.isinOld &&
      action.isinNew === fixture.isinNew &&
      action.actionDate === fixture.actionDate
  );
}

/** Creates an ISIN change through SecurityActionCreateComponent. */
export async function createIsinChange(page: Page, fixture: IsinChangeFixture, locale: string): Promise<void> {
  await test.step(`create ${fixture.isinOld} -> ${fixture.isinNew}`, async () => {
    const rootRow = systemActionsRow(page);
    const menu = await openContextMenu(page, rootRow);
    const createItem = menu.getByText(RX.create).first();
    await expect(createItem, 'admin create command').toBeVisible();
    await createItem.click();

    const createDialog = page.locator('security-action-create .p-dialog');
    await createDialog.waitFor({ state: 'visible', timeout: 10_000 });
    await createDialog
      .locator('form-input-button')
      .filter({ has: page.locator('#isinOld') })
      .getByRole('button')
      .click();

    const searchDialog = page.locator('.p-dialog').filter({ has: page.locator('securitycurrency-search-and-set') });
    await searchDialog.waitFor({ state: 'visible', timeout: 10_000 });
    await expect(searchDialog.locator('select#idStockexchange option')).not.toHaveCount(0, { timeout: 15_000 });
    await typeText(searchDialog.locator('#isin'), fixture.isinOld);
    await searchDialog.locator('button[type="submit"]').click();

    const searchRow = searchDialog
      .locator('securitycurrency-search-and-set-table tbody tr')
      .filter({ hasText: fixture.isinOld });
    await expect(searchRow, `search result for ${fixture.isinOld}`).toHaveCount(1, { timeout: 15_000 });
    await searchRow.click();
    await searchDialog.getByRole('button', { name: RX.assignSelected }).click();
    await searchDialog.waitFor({ state: 'hidden', timeout: 10_000 });
    await expect(createDialog.locator('#isinOld')).toHaveValue(fixture.isinOld);

    await typeText(createDialog.locator('#isinNew'), fixture.isinNew);
    await typeDate(createDialog, fixture.actionDate, locale);
    await typeNumber(createDialog, 'fromFactor', fixture.fromFactor);
    await typeNumber(createDialog, 'toFactor', fixture.toFactor);
    await typeText(createDialog.locator('#note'), fixture.note);

    const responsePromise = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === '/api/securityaction' && response.request().method() === 'POST',
      { timeout: 30_000 }
    );
    await createDialog.locator('button[type="submit"]').click();
    const response = await responsePromise;
    expect(response.ok(), `creating Security Action: ${await response.text()}`).toBeTruthy();
    await createDialog.waitFor({ state: 'hidden', timeout: 15_000 });
  });
}

/** Applies an action through the system-action row's context menu. */
export async function applyIsinChange(page: Page, action: ApiSecurityAction): Promise<void> {
  await test.step(`apply ${action.isinOld} -> ${action.isinNew}`, async () => {
    const menu = await openContextMenu(page, securityActionRow(page, action));
    await expect(menu.getByText(RX.create), 'non-admin create command').toHaveCount(0);
    const responsePromise = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === `/api/securityaction/${action.idSecurityAction}/apply` &&
        response.request().method() === 'POST',
      { timeout: 30_000 }
    );
    await menu.getByRole('menuitem', { name: RX.apply }).click();
    const response = await responsePromise;
    expect(response.ok(), `applying Security Action: ${await response.text()}`).toBeTruthy();
  });
}

/** Reverses a prior successful application through REST so 140 can exercise Apply again. */
export async function reverseActiveApplication(
  page: Page,
  action: ApiSecurityAction,
  application: ApiSecurityActionApplication | undefined
): Promise<void> {
  if (!application || application.reversed) {
    return;
  }
  const response = await page.request.post(`/api/securityaction/${action.idSecurityAction}/reverse`, {
    headers: await authHeaders(page),
    failOnStatusCode: false
  });
  expect(response.ok(), `reversing prior Security Action application: ${await response.text()}`).toBeTruthy();
}

/** Verifies the exact sender or receiver record returned from mail_send_recv. */
export async function expectIsinChangeMail(page: Page, fixture: IsinChangeFixture, sendRecv: 'S' | 'R'): Promise<void> {
  const response = await page.request.get('/api/mailsendrecv', { headers: await authHeaders(page) });
  expect(response.ok(), `loading mail_send_recv: ${await response.text()}`).toBeTruthy();
  const inbox = (await response.json()) as ApiMailInbox;
  const subject = `ISIN Change: ${fixture.isinOld} -> ${fixture.isinNew}`;
  const message =
    `Security ISIN ${fixture.isinOld} changed to ${fixture.isinNew} on ${fixture.actionDate}. ` +
    'Apply in Security Actions.';
  const matches = inbox.mailSendRecvList.filter(
    (mail) => mail.sendRecv === sendRecv && mail.subject === subject && mail.message === message
  );
  expect(matches.length, `${sendRecv} mail_send_recv row for '${subject}'`).toBeGreaterThan(0);
  const mail = matches[matches.length - 1];
  if (sendRecv === 'S') {
    expect(mail.idReplyToLocal, 'sender mail is the conversation root').toBeNull();
  } else {
    expect(mail.idReplyToLocal, 'receiver mail points to the sender row').not.toBeNull();
  }
}

/** Checks every stable field exported from security_action.id_security_action=1. */
export function expectSecurityAction(
  action: ApiSecurityAction | undefined,
  fixture: IsinChangeFixture,
  appliedCount: number
): void {
  expect(action, `Security Action ${fixture.isinOld} -> ${fixture.isinNew}`).toBeDefined();
  expect(action).toMatchObject({
    isinOld: fixture.isinOld,
    isinNew: fixture.isinNew,
    actionDate: fixture.actionDate,
    note: fixture.note,
    fromFactor: fixture.fromFactor,
    toFactor: fixture.toFactor,
    affectedCount: fixture.expectedAffectedCount,
    appliedCount
  });
}

export function securityActionRow(page: Page, action: ApiSecurityAction): Locator {
  return securityActionContainer(page)
    .locator('configurable-tree-table tbody tr')
    .filter({ has: page.locator('td', { hasText: exactText(action.isinOld) }) })
    .filter({ has: page.locator('td', { hasText: exactText(action.isinNew) }) })
    .first();
}

function parseFixtureRow(line: string, lineNumber: number): IsinChangeFixture {
  const columns = parseCsvRow(line);
  if (columns.length !== 10) {
    throw new Error(`${FIXTURE_PATH}:${lineNumber}: expected 10 columns, got ${columns.length}`);
  }
  const fromFactor = Number(columns[6]);
  const toFactor = Number(columns[7]);
  const expectedAffectedCount = Number(columns[8]);
  if (![fromFactor, toFactor, expectedAffectedCount].every(Number.isFinite)) {
    throw new Error(`${FIXTURE_PATH}:${lineNumber}: factor and count columns must be numeric`);
  }
  if (!['d', 'i', 'e'].includes(columns[9])) {
    throw new Error(`${FIXTURE_PATH}:${lineNumber}: invalid e2e routing tag '${columns[9]}'`);
  }
  return {
    createNickname: columns[0],
    applyNickname: columns[1],
    isinOld: columns[2],
    isinNew: columns[3],
    actionDate: columns[4],
    note: columns[5],
    fromFactor,
    toFactor,
    expectedAffectedCount,
    e2e: columns[9] as IsinChangeFixture['e2e']
  };
}

function systemActionsRow(page: Page): Locator {
  return securityActionContainer(page)
    .locator('configurable-tree-table tbody tr')
    .filter({ has: page.locator('td').first().filter({ hasText: RX.systemActions }) })
    .first();
}

function securityActionContainer(page: Page): Locator {
  return page
    .locator('.data-container')
    .filter({ has: page.getByRole('heading', { name: RX.securityAction }) })
    .first();
}

async function openContextMenu(page: Page, row: Locator): Promise<Locator> {
  await row.waitFor({ state: 'visible', timeout: 15_000 });
  await row.click();
  await row.click({ button: 'right' });
  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({ state: 'visible', timeout: 10_000 });
  return menu;
}

async function authHeaders(page: Page): Promise<Record<string, string>> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  return { 'x-auth-token': token!, Accept: 'application/json' };
}

async function typeText(input: Locator, value: string): Promise<void> {
  await input.click();
  await input.fill(value);
  await input.dispatchEvent('input');
  await input.blur();
}

async function typeDate(dialog: Locator, isoDate: string, locale: string): Promise<void> {
  const input = dialog.locator('#actionDate input, input#actionDate').first();
  const shortDate = toShortDate(isoDate, locale);
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(shortDate, { delay: 20 });
  await input.press('Tab');
  await expect(input).toHaveValue(shortDate);
}

async function typeNumber(dialog: Locator, field: string, value: number): Promise<void> {
  const input = dialog.locator(`#${field} input, input#${field}`).first();
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(String(value), { delay: 20 });
  await input.press('Tab');
  expect(Number((await input.inputValue()).replace(/[^\d.-]/g, '')), field).toBe(value);
}

function exactText(value: string): RegExp {
  return new RegExp(`^\\s*${value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`);
}
