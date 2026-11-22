import { expect, Locator, Page, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

import { parseCsvRow } from './helpers';
import { toShortDate } from './portfolio.helpers';

const FIXTURE_PATH = path.resolve(
  __dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/isin_change.csv'
);

const SPIN_OFF_FIXTURE_PATH = path.resolve(
  __dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/spin_off.json'
);

const SPIN_OFF_APPLY_FIXTURE_PATH = path.resolve(
  __dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/spin_off_apply.json'
);

const RX = {
  apply: /^(Apply ISIN change|ISIN-Änderung anwenden)$/,
  // The menu entry opens a dialog and therefore carries the dialog suffix
  applySpinOff: /^(Apply spin-off|Abspaltung anwenden)(\.\.\.|…)?$/,
  childCloseLater: /(first closing price of the child is from a later day|erste Schlusskurs des abgespaltenen)/,
  assignSelected: /^(Assign selected|Selektierte zuweisen)$/,
  create: /^(Create ISIN change|ISIN-Änderung erstellen)/,
  createSpinOff: /^(Record spin-off|Abspaltung erfassen)/,
  securityAction: /^(Security Action|Wertpapieraktion)$/,
  systemActions: /\b(System actions|Systemaktionen)\b/
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

/**
 * One spin-off of testdata/spin_off.json. The parent and an EXISTING child come from the JUnit suite
 * (testdata/spin-off-securities.json); a LOOKUP child is found by ISIN and trading currency in the dialog and created
 * together with the action.
 */
export interface SpinOffFixture {
  createNickname: string;
  parentIsin: string;
  parentCurrency: string;
  childIsin: string;
  childCurrency: string;
  childMode: 'EXISTING' | 'LOOKUP';
  actionDate: string;
  fromFactor: number;
  toFactor: number;
  costAllocationPercent: number;
  costAllocationSource: string;
  taxTreatmentDe: 'UNDETERMINED' | 'COST_SPLIT' | 'TAXABLE_DISTRIBUTION';
  e2e: 'd' | 'i' | 'e';
}

/** The fields that identify a spin-off: parent, child and ex-date. */
export type SpinOffKey = Pick<
  SpinOffFixture,
  'parentIsin' | 'parentCurrency' | 'childIsin' | 'childCurrency' | 'actionDate'
>;

/** One spin-off a tenant applies in testdata/spin_off_apply.json, with the units the preview must show. */
export interface SpinOffApplyExpectation {
  childIsin: string;
  childCurrency: string;
  actionDate: string;
  parentUnits: number;
  childUnits: number;
  /** Set only when the test data fixes the first available child close. */
  childCloseLater?: boolean;
}

/**
 * The entitlement purchase of testdata/spin_off_apply.json and the spin-offs the tenant applies on it, oldest first.
 * The note identifies the purchase, so a repeated run can delete it again.
 */
export interface SpinOffApplyFixture {
  nickname: string;
  portfolio: string;
  securityAccount: string;
  cashAccount: string;
  parentIsin: string;
  parentCurrency: string;
  purchaseDate: string;
  units: number;
  note: string;
  spinOffs: SpinOffApplyExpectation[];
  e2e: 'd' | 'i' | 'e';
}

/** The REST representation of a spin-off preview, reduced to what the spec checks. */
export interface ApiSpinOffPreview {
  actionDate: string;
  childPriceDate: string;
  accounts: {
    idSecurityaccount: number;
    accountName: string;
    parentUnits: number;
    childUnits: number;
    decision: 'BOOK' | 'SKIP';
  }[];
}

interface ApiSecurityRef {
  idSecuritycurrency: number;
  isin: string;
  currency: string;
}

export interface ApiSecurityAction {
  idSecurityAction: number;
  actionType?: 'ISIN_CHANGE' | 'SPIN_OFF';
  securityOld?: ApiSecurityRef | null;
  securityNew?: ApiSecurityRef | null;
  isinOld: string;
  isinNew: string;
  actionDate: string;
  note: string;
  fromFactor: number | null;
  toFactor: number | null;
  costAllocation?: number | null;
  costAllocationSource?: string | null;
  taxTreatmentDe?: string | null;
  historyBasis?: 'UNDETERMINED' | 'UNADJUSTED' | 'ADJUSTED' | null;
  affectedCount: number;
  appliedCount: number;
}

export interface ApiSecurityActionApplication {
  idSecurityActionApp: number;
  securityAction: ApiSecurityAction;
  reversed: boolean;
  bookedAccounts?: number;
  skippedAccounts?: number;
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

/** Loads the spin-offs owned by Playwright. */
export function loadE2ESpinOffs(): SpinOffFixture[] {
  if (!fs.existsSync(SPIN_OFF_FIXTURE_PATH)) {
    console.warn(`Fixture ${SPIN_OFF_FIXTURE_PATH} not found - skipping the spin-off E2E spec.`);
    return [];
  }
  const rows = JSON.parse(fs.readFileSync(SPIN_OFF_FIXTURE_PATH, 'utf8')) as SpinOffFixture[];
  return rows.filter((row) => row.e2e === 'e');
}

/** Loads the spin-off applications owned by Playwright. */
export function loadE2ESpinOffApplications(): SpinOffApplyFixture[] {
  if (!fs.existsSync(SPIN_OFF_APPLY_FIXTURE_PATH)) {
    console.warn(`Fixture ${SPIN_OFF_APPLY_FIXTURE_PATH} not found - skipping the spin-off apply E2E spec.`);
    return [];
  }
  const rows = JSON.parse(fs.readFileSync(SPIN_OFF_APPLY_FIXTURE_PATH, 'utf8')) as SpinOffApplyFixture[];
  return rows.filter((row) => row.e2e === 'e');
}

/** HTTP status of the tenant's spin-off preview; an error status while the parent refresh is outstanding. */
export async function spinOffPreviewStatus(page: Page, action: ApiSecurityAction): Promise<number> {
  const response = await page.request.get(`/api/securityaction/${action.idSecurityAction}/spinoff/preview`, {
    headers: await authHeaders(page),
    failOnStatusCode: false
  });
  return response.status();
}

/**
 * Applies a spin-off through the tree context menu and SpinOffApplyDialogComponent. The preview the dialog loads must
 * show the expected units for the securities account, which keeps the proposed decision BOOK.
 */
export async function applySpinOff(
  page: Page,
  action: ApiSecurityAction,
  securityAccount: string,
  expected: SpinOffApplyExpectation
): Promise<ApiSecurityActionApplication> {
  return test.step(`apply spin-off ${action.isinOld} -> ${action.isinNew}`, async () => {
    const menu = await openContextMenu(page, securityActionRow(page, action));
    const applyItem = menu.getByRole('menuitem', { name: RX.applySpinOff });
    await expect(applyItem, 'spin-off context menu command').toBeEnabled();
    const [previewResponse] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === `/api/securityaction/${action.idSecurityAction}/spinoff/preview` &&
          response.request().method() === 'GET',
        { timeout: 30_000 }
      ),
      applyItem.click()
    ]);
    expect(previewResponse.ok(), `spin-off preview: ${await previewResponse.text()}`).toBeTruthy();
    const preview = (await previewResponse.json()) as ApiSpinOffPreview;
    const account = preview.accounts.find((row) => row.accountName === securityAccount);
    expect(account, `preview row of ${securityAccount}`).toBeDefined();
    expect(account!.parentUnits, 'parent units N').toBeCloseTo(expected.parentUnits, 8);
    expect(account!.childUnits, 'child units M').toBeCloseTo(expected.childUnits, 8);
    expect(account!.decision).toBe('BOOK');
    expect(preview.actionDate, 'preview action date').toBe(expected.actionDate);
    expect(preview.childPriceDate, 'child closing price date').toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(preview.childPriceDate < preview.actionDate, 'child close before the action date').toBe(false);
    const childCloseLater = preview.childPriceDate > preview.actionDate;
    if (expected.childCloseLater !== undefined) {
      expect(childCloseLater, `child close on ${preview.childPriceDate} for action on ${preview.actionDate}`).toBe(
        expected.childCloseLater
      );
    }

    const dialog = page.locator('spin-off-apply-dialog .p-dialog');
    await dialog.waitFor({ state: 'visible', timeout: 10_000 });
    const row = dialog.locator('spin-off-preview-table tbody tr').filter({ hasText: securityAccount });
    await expect(row).toHaveCount(1, { timeout: 15_000 });
    await expect(row.locator('select')).toHaveValue('BOOK');
    const laterPriceNotice = dialog.getByText(RX.childCloseLater);
    if (childCloseLater) {
      await expect(laterPriceNotice).toBeVisible();
    } else {
      await expect(laterPriceNotice).toBeHidden();
    }
    const submit = dialog.locator('button[type="submit"]');
    await expect(submit).toBeEnabled({ timeout: 15_000 });
    const [applied] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === `/api/securityaction/${action.idSecurityAction}/apply` &&
          response.request().method() === 'POST',
        { timeout: 30_000 }
      ),
      submit.click()
    ]);
    expect(applied.ok(), `applying spin-off: ${await applied.text()}`).toBeTruthy();
    await dialog.waitFor({ state: 'hidden', timeout: 15_000 });
    return (await applied.json()) as ApiSecurityActionApplication;
  });
}

/** Finds the spin-off identified by the fixture's parent, child and ex-date. */
export function findSpinOff(tree: ApiSecurityActionTreeData, fixture: SpinOffKey): ApiSecurityAction | undefined {
  return (tree.systemActions ?? []).find(
    (action) =>
      action.actionType === 'SPIN_OFF' &&
      action.securityOld?.isin === fixture.parentIsin &&
      action.securityOld?.currency === fixture.parentCurrency &&
      action.securityNew?.isin === fixture.childIsin &&
      action.securityNew?.currency === fixture.childCurrency &&
      action.actionDate === fixture.actionDate
  );
}

/** Deletes a system action through REST; the server refuses it once a tenant has applied it. */
export async function deleteSecurityAction(page: Page, action: ApiSecurityAction): Promise<void> {
  const response = await page.request.delete(`/api/securityaction/${action.idSecurityAction}`, {
    headers: await authHeaders(page),
    failOnStatusCode: false
  });
  expect(response.ok(), `deleting Security Action ${action.idSecurityAction}: ${await response.text()}`).toBeTruthy();
}

/**
 * Records a spin-off through SecurityActionCreateDialogComponent. The parent is always chosen in the search dialog.
 * An EXISTING child is chosen there too; a LOOKUP child is given only by ISIN and trading currency, so saving first
 * looks it up at the data providers and then creates it with the action.
 */
export async function createSpinOff(page: Page, fixture: SpinOffFixture, locale: string): Promise<void> {
  await test.step(`record spin-off ${fixture.parentIsin} -> ${fixture.childIsin}`, async () => {
    const menu = await openContextMenu(page, systemActionsRow(page));
    const createItem = menu.getByText(RX.createSpinOff).first();
    await expect(createItem, 'admin spin-off command').toBeVisible();
    await createItem.click();

    const createDialog = page.locator('security-action-create .p-dialog');
    await createDialog.waitFor({ state: 'visible', timeout: 10_000 });
    await expect(createDialog.locator('select#actionType')).toHaveValue('SPIN_OFF', { timeout: 10_000 });

    await chooseSecurity(page, createDialog, 'securityOld', fixture.parentIsin, fixture.parentCurrency);
    if (fixture.childMode === 'EXISTING') {
      await chooseSecurity(page, createDialog, 'securityNew', fixture.childIsin, fixture.childCurrency);
    } else {
      await typeText(createDialog.locator('#childIsin'), fixture.childIsin);
      const currency = createDialog.locator('select#childCurrency');
      await expect(currency.locator(`option[value="${fixture.childCurrency}"]`)).toHaveCount(1, { timeout: 15_000 });
      await currency.selectOption(fixture.childCurrency);
    }
    await typeDate(createDialog, fixture.actionDate, locale);
    await typeNumber(createDialog, 'fromFactor', fixture.fromFactor);
    await typeNumber(createDialog, 'toFactor', fixture.toFactor);
    await typeNumber(createDialog, 'costAllocation', fixture.costAllocationPercent);
    await typeText(createDialog.locator('#costAllocationSource'), fixture.costAllocationSource);
    const taxTreatment = createDialog.locator('select#taxTreatmentDe');
    await expect(taxTreatment.locator(`option[value="${fixture.taxTreatmentDe}"]`)).toHaveCount(1, {
      timeout: 15_000
    });
    await taxTreatment.selectOption(fixture.taxTreatmentDe);

    // A LOOKUP child asks the data providers twice (lookup and save), which can take a while
    const responsePromise = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === '/api/securityaction' && response.request().method() === 'POST',
      { timeout: 90_000 }
    );
    await createDialog.locator('button[type="submit"]').click();
    const response = await responsePromise;
    expect(response.ok(), `creating spin-off: ${await response.text()}`).toBeTruthy();
    await createDialog.waitFor({ state: 'hidden', timeout: 15_000 });
  });
}

/** Checks the recorded spin-off against its fixture. */
export function expectSpinOff(action: ApiSecurityAction | undefined, fixture: SpinOffFixture): void {
  expect(action, `spin-off ${fixture.parentIsin} -> ${fixture.childIsin}`).toBeDefined();
  expect(action).toMatchObject({
    actionType: 'SPIN_OFF',
    isinOld: fixture.parentIsin,
    isinNew: fixture.childIsin,
    actionDate: fixture.actionDate,
    fromFactor: fixture.fromFactor,
    toFactor: fixture.toFactor,
    costAllocationSource: fixture.costAllocationSource,
    taxTreatmentDe: fixture.taxTreatmentDe
  });
  expect(action!.costAllocation, 'cost allocation as fraction').toBeCloseTo(fixture.costAllocationPercent / 100, 8);
  expect(action!.securityNew?.idSecuritycurrency, 'persisted child instrument').toBeGreaterThan(0);
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

/** Creates an ISIN change through SecurityActionCreateDialogComponent. */
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

/** Reverses a prior successful application through REST so 310 can exercise Apply again. */
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

/** Chooses an instrument of an input-button field through the security search dialog. */
async function chooseSecurity(
  page: Page,
  dialog: Locator,
  field: string,
  isin: string,
  currency: string
): Promise<void> {
  await dialog
    .locator('form-input-button')
    .filter({ has: page.locator(`#${field}`) })
    .getByRole('button')
    .click();
  const searchDialog = page.locator('.p-dialog').filter({ has: page.locator('securitycurrency-search-and-set') });
  await searchDialog.waitFor({ state: 'visible', timeout: 10_000 });
  await expect(searchDialog.locator('select#idStockexchange option')).not.toHaveCount(0, { timeout: 15_000 });
  await typeText(searchDialog.locator('#isin'), isin);
  await searchDialog.locator('button[type="submit"]').click();
  const searchRow = searchDialog
    .locator('securitycurrency-search-and-set-table tbody tr')
    .filter({ hasText: isin })
    .filter({ hasText: currency });
  await expect(searchRow, `search result for ${isin} ${currency}`).toHaveCount(1, { timeout: 15_000 });
  await searchRow.click();
  await searchDialog.getByRole('button', { name: RX.assignSelected }).click();
  await searchDialog.waitFor({ state: 'hidden', timeout: 10_000 });
  await expect(dialog.locator(`#${field}`)).toHaveValue(new RegExp(isin));
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

/** Headers with the JWT of the logged-in user, for the REST set-up and verification of the specs. */
export async function authHeaders(page: Page): Promise<Record<string, string>> {
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
