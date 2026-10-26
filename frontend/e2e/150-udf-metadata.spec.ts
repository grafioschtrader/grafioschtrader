import { expect, Locator, Page, Route, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { loginAsFixtureUser } from './helpers';
import { fillText, pickMultiSelect, selectByValue } from './generic-connector.helpers';

interface UdfMetadataBaseRow {
  kind: 'general' | 'security';
  nickname: string;
  description: string;
  descriptionHelp: string | null;
  udfDataType: string;
  fieldSize: string | null;
  uiOrder: number;
  e2e: string;
}

interface UdfMetadataGeneralRow extends UdfMetadataBaseRow {
  kind: 'general';
  entity: string;
}

interface UdfMetadataSecurityRow extends UdfMetadataBaseRow {
  kind: 'security';
  categoryTypeEnums: string[];
  specialInvestmentInstrumentEnums: string[];
}

type UdfMetadataRow = UdfMetadataGeneralRow | UdfMetadataSecurityRow;

interface ApiUdfMetadata {
  idUDFMetadata: number;
  idUser: number;
  description: string;
  descriptionHelp: string | null;
  udfDataType: string;
  fieldSize: string | null;
  uiOrder: number;
  entity?: string;
  categoryTypeEnums?: string[];
  specialInvestmentInstrumentEnums?: string[];
}

const FIXTURE_PATH = path.resolve(
  __dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/udf-metadata.json'
);

const RX = {
  baseDataNode: /(Base Data|Basisdaten)/i,
  generalNode: /^\s*(General definition of additional field|Definition Zusatzfeld allgemein|UDF_METADATA_GENERAL)\s*$/i,
  securityNode: /^\s*(Custom field Instrument|Zusatzfeld Instrument|UDF_METADATA_SECURITY)\s*$/i,
  createItem: /^(Create|Erstellen)\b/i
};

function loadRows(): UdfMetadataRow[] {
  return (JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf-8')) as UdfMetadataRow[]).filter((row) => row.e2e === 'e');
}

function endpoint(row: UdfMetadataRow): string {
  return `/api/${row.kind === 'general' ? 'udfmetadatageneral' : 'udfmetadatasecurity'}`;
}

function matchesNaturalKey(actual: ApiUdfMetadata, expected: UdfMetadataRow): boolean {
  return (
    actual.description === expected.description && (expected.kind === 'security' || actual.entity === expected.entity)
  );
}

async function readMetadata(page: Page, row: UdfMetadataRow): Promise<ApiUdfMetadata[]> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  const response = await page.request.get(endpoint(row), { headers: { 'x-auth-token': token! } });
  expect(response.ok(), `loading ${row.kind} UDF metadata: ${await response.text()}`).toBeTruthy();
  return response.json() as Promise<ApiUdfMetadata[]>;
}

/** Deletes only a matching user-owned leftover from an earlier interrupted run. System rows are immutable here. */
async function deleteOwnLeftovers(page: Page, row: UdfMetadataRow): Promise<void> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  const matches = (await readMetadata(page, row)).filter(
    (actual) => actual.idUser !== 0 && matchesNaturalKey(actual, row)
  );
  for (const match of matches) {
    const response = await page.request.delete(`${endpoint(row)}/${match.idUDFMetadata}`, {
      headers: { 'x-auth-token': token! }
    });
    expect(
      response.ok(),
      `deleting leftover UDF metadata ${match.idUDFMetadata}: ${await response.text()}`
    ).toBeTruthy();
  }
}

async function openMetadataView(page: Page, row: UdfMetadataRow): Promise<Locator> {
  const baseDataNode = page.locator('.p-tree-node-content', { hasText: RX.baseDataNode }).first();
  await baseDataNode.waitFor({ state: 'visible', timeout: 15_000 });
  const nodeRx = row.kind === 'general' ? RX.generalNode : RX.securityNode;
  const treeNode = page.locator('.p-tree-node-content', { hasText: nodeRx }).first();
  if (!(await treeNode.isVisible())) await baseDataNode.dblclick();
  await treeNode.waitFor({ state: 'visible', timeout: 15_000 });
  await treeNode.click();
  await page.waitForURL(new RegExp(`/mainview/udfmetadata${row.kind}`), { timeout: 10_000 });

  const table = page.locator('configurable-table').first();
  await table.waitFor({ state: 'visible', timeout: 10_000 });
  return table;
}

async function openCreateDialog(page: Page, table: Locator): Promise<Locator> {
  const caption = table.locator('h4').first();
  await caption.click();
  await page.waitForTimeout(300);
  await caption.click({ button: 'right' });

  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({ state: 'visible', timeout: 5_000 });
  await menu.getByRole('menuitem', { name: RX.createItem }).first().click();

  const dialog = page.locator('.p-dialog:visible').first();
  await dialog.waitFor({ state: 'visible', timeout: 10_000 });
  return dialog;
}

async function fillBaseFields(dialog: Locator, row: UdfMetadataRow): Promise<void> {
  await selectByValue(dialog, 'uiOrder', String(row.uiOrder));
  await fillText(dialog, 'input#description', row.description);
  if (row.descriptionHelp !== null) {
    await fillText(dialog, 'textarea#descriptionHelp', row.descriptionHelp);
  }
  await selectByValue(dialog, 'udfDataType', row.udfDataType);
  if (row.fieldSize !== null) {
    await fillText(dialog, 'input#fieldSize', row.fieldSize);
  }
}

async function createMetadata(page: Page, row: UdfMetadataRow): Promise<void> {
  const table = await openMetadataView(page, row);
  const dialog = await openCreateDialog(page, table);

  if (row.kind === 'general') {
    const entitySelect = dialog.locator('select#entity');
    await entitySelect.locator(`option[value="${row.entity}"]`).waitFor({ state: 'attached', timeout: 10_000 });
    if (await entitySelect.isDisabled()) {
      await expect(entitySelect).toHaveValue(row.entity);
    } else {
      await selectByValue(dialog, 'entity', row.entity);
    }
  }
  await fillBaseFields(dialog, row);
  if (row.kind === 'security') {
    await pickMultiSelect(page, dialog, 'categoryTypeEnums', row.categoryTypeEnums);
    await pickMultiSelect(page, dialog, 'specialInvestmentInstrumentEnums', row.specialInvestmentInstrumentEnums);
  }

  await dialog.locator('button[type="submit"]').click();
  await dialog.waitFor({ state: 'hidden', timeout: 10_000 });
  await expect(table.locator('tr[data-p-selectable-row]', { hasText: row.description }).first()).toBeVisible({
    timeout: 10_000
  });
}

async function verifyPersisted(page: Page, row: UdfMetadataRow): Promise<void> {
  const matches = (await readMetadata(page, row)).filter(
    (actual) => actual.idUser !== 0 && matchesNaturalKey(actual, row)
  );
  expect(matches, `one user-owned persisted row for ${row.nickname}/${row.description}`).toHaveLength(1);
  const actual = matches[0];
  expect(actual).toMatchObject({
    description: row.description,
    descriptionHelp: row.descriptionHelp,
    udfDataType: row.udfDataType,
    fieldSize: row.fieldSize,
    uiOrder: row.uiOrder
  });
  if (row.kind === 'general') {
    expect(actual.entity).toBe(row.entity);
  } else {
    expect(actual.categoryTypeEnums).toEqual(expect.arrayContaining(row.categoryTypeEnums));
    expect(actual.categoryTypeEnums).toHaveLength(row.categoryTypeEnums.length);
    expect(actual.specialInvestmentInstrumentEnums).toEqual(
      expect.arrayContaining(row.specialInvestmentInstrumentEnums)
    );
    expect(actual.specialInvestmentInstrumentEnums).toHaveLength(row.specialInvestmentInstrumentEnums.length);
  }
}

test.describe.serial('Create user-owned general and security UDF metadata from shared fixture', () => {
  test.use({ viewport: { width: 1600, height: 1400 } });
  for (const row of loadRows()) {
    test(`${row.nickname} creates ${row.kind} UDF metadata: ${row.description}`, async ({ page }) => {
      await loginAsFixtureUser(page, row.nickname);
      await deleteOwnLeftovers(page, row);
      await createMetadata(page, row);
      await verifyPersisted(page, row);
    });
  }
});

/** Holds one real HTTP response so no extra click is needed after the asynchronous answer arrives. */
async function holdResponse(page: Page, url: string, method = 'GET') {
  let release!: () => void;
  let arrived!: () => void;
  let done!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const received = new Promise<void>((resolve) => (arrived = resolve));
  const completed = new Promise<void>((resolve) => (done = resolve));
  let holding = false;
  const handler = async (route: Route): Promise<void> => {
    if (holding || route.request().method() !== method) return route.fallback();
    holding = true;
    try {
      const response = await route.fetch();
      expect(response.ok(), await response.text()).toBeTruthy();
      arrived();
      await gate;
      try {
        await route.fulfill({ response });
      } catch (error) {
        if (route.request().failure()?.errorText !== 'net::ERR_ABORTED') throw error;
      }
    } finally {
      await page.unroute(url, handler);
      done();
    }
  };
  await page.route(url, handler);
  return { release, arrived: received, done: completed };
}

async function rowMenu(page: Page, target: Locator): Promise<Locator> {
  if (!(await target.getAttribute('class'))?.includes('p-datatable-row-selected')) await target.click();
  await target.click({ button: 'right' });
  const menu = page.locator('[role="menu"]:visible');
  await expect(menu).toBeVisible();
  return menu;
}

async function openEdit(page: Page, target: Locator, kind: string): Promise<Locator> {
  const menu = await rowMenu(page, target);
  await menu.getByRole('menuitem', { name: /^(Edit|Bearbeiten)\b/ }).click();
  const dialog = page.locator(`udf-metadata-${kind}-edit .p-dialog`);
  await expect(dialog.locator('#description')).not.toHaveValue('');
  return dialog;
}

async function closeMetadataDialog(dialog: Locator): Promise<void> {
  await dialog.locator('.p-dialog-close-button').click();
  await expect(dialog).toHaveCount(0);
}

test.describe('UDF metadata notification', () => {
  test.use({ viewport: { width: 1600, height: 1400 } });
  test.beforeEach(({ page }) => page.setDefaultTimeout(15_000));
  const rows = loadRows().filter((r) => r.nickname === 'admin');
  for (const kind of ['general', 'security'] as const) {
    const row = rows.find((r) => r.kind === kind)!;
    test(`${kind} metadata survives cancelled edits and publishes a delayed reload`, async ({ page }) => {
      await loginAsFixtureUser(page, row.nickname);
      await deleteOwnLeftovers(page, row);
      await createMetadata(page, row);
      const target = page
        .locator('configurable-table tbody tr')
        .filter({ has: page.getByRole('cell', { name: row.description, exact: true }) });
      let dialog = await openEdit(page, target, kind);
      await expect(dialog.locator('#udfDataType')).toBeDisabled();
      await dialog.locator('#descriptionHelp').fill('Unsaved notification draft');
      await closeMetadataDialog(dialog);
      await expect(target).not.toContainText('Unsaved notification draft');
      dialog = await openEdit(page, target, kind);
      await expect(dialog.locator('#descriptionHelp')).toHaveValue(row.descriptionHelp ?? '');
      await dialog.locator('#descriptionHelp').fill('Saved notification draft');
      const pending = await holdResponse(page, `**${endpoint(row)}`);
      try {
        await dialog.locator('button[type="submit"]').click();
        await pending.arrived;
        await expect(target).not.toContainText('Saved notification draft');
        pending.release();
        await pending.done;
        await expect(target).toContainText('Saved notification draft');
      } finally {
        pending.release();
      }
      // Restore fixture values for spec 155, which consumes these definitions by natural key.
      dialog = await openEdit(page, target, kind);
      await dialog.locator('#descriptionHelp').fill(row.descriptionHelp ?? '');
      await dialog.locator('button[type="submit"]').click();
      await expect(dialog).toHaveCount(0);
      await verifyPersisted(page, row);
    });
  }

  test('data type dependencies and a reopened security draft survive an abandoned fill offer', async ({ page }) => {
    const row = rows.find((r) => r.kind === 'security')!;
    await loginAsFixtureUser(page, 'admin');
    const table = await openMetadataView(page, row);
    const pending = await holdResponse(page, '**/api/udfmetadatasecurity/fillsources');
    try {
      let dialog = await openCreateDialog(page, table);
      await pending.arrived;
      await closeMetadataDialog(dialog);
      dialog = await openCreateDialog(page, table);
      await selectByValue(dialog, 'udfDataType', 'UDF_String');
      await expect(dialog.locator('#fillSource option[value="TEMPLATE"]')).toHaveCount(1);
      await expect(dialog.locator('#fieldSize')).toBeEnabled();
      await dialog.locator('#fieldSize').fill('64');
      await selectByValue(dialog, 'udfDataType', 'UDF_Boolean');
      await expect(dialog.locator('#fieldSize')).toBeDisabled();
      await expect(dialog.locator('#fieldSize')).toHaveValue('');
      await selectByValue(dialog, 'udfDataType', 'UDF_URLString');
      await selectByValue(dialog, 'fillSource', 'TEMPLATE');
      await expect(dialog.locator('#fillRule')).toBeVisible();
      await expect(dialog).toHaveCSS('width', '850px');
      await dialog.locator('#description').fill('Unsaved UDF draft');
      pending.release();
      await pending.done;
      await expect(dialog.locator('#description')).toHaveValue('Unsaved UDF draft');
      await expect(dialog.locator('#fillSource')).toHaveValue('TEMPLATE');
      await selectByValue(dialog, 'fillSource', 'MANUAL');
      await expect(dialog.locator('#fillRule')).toBeHidden();
      await expect(dialog).toHaveCSS('width', '500px');
      await closeMetadataDialog(dialog);
    } finally {
      pending.release();
    }
  });

  test('template results arrive asynchronously and an obsolete test cannot replace a newer draft', async ({ page }) => {
    const row = rows.find((r) => r.kind === 'security')!;
    await loginAsFixtureUser(page, 'admin');
    const table = await openMetadataView(page, row);
    const dialog = await openCreateDialog(page, table);
    await selectByValue(dialog, 'udfDataType', 'UDF_URLString');
    await expect(dialog.locator('#fillSource option[value="TEMPLATE"]')).toHaveCount(1);
    await selectByValue(dialog, 'fillSource', 'TEMPLATE');
    await pickMultiSelect(page, dialog, 'categoryTypeEnums', ['EQUITIES']);
    await pickMultiSelect(page, dialog, 'specialInvestmentInstrumentEnums', ['DIRECT_INVESTMENT']);
    await dialog
      .locator('#fillRule')
      .fill(
        '[mic=XNAS,XNYS] https://www.marketbeat.com/stocks/{mic|map(XNAS=NASDAQ,XNYS=NYSE)}/{ticker|urlencode}/short-interest/'
      );
    const pending = await holdResponse(page, '**/api/udfmetadatasecurity/testfillrule', 'POST');
    try {
      await dialog.getByRole('button', { name: /^(Test rule|Regel testen)$/ }).click();
      await pending.arrived;
      await expect(dialog.locator('udf-fill-test-result')).toHaveCount(0);
      pending.release();
      await pending.done;
      await expect(dialog.locator('udf-fill-test-result')).toBeVisible();
    } finally {
      pending.release();
    }
    const obsolete = await holdResponse(page, '**/api/udfmetadatasecurity/testfillrule', 'POST');
    try {
      await dialog.getByRole('button', { name: /^(Test rule|Regel testen)$/ }).click();
      await obsolete.arrived;
      await dialog.locator('#fillRule').fill('[mic=XNAS] https://example.com/{ticker|urlencode}');
      obsolete.release();
      await obsolete.done;
      await expect(dialog.locator('udf-fill-test-result')).toHaveCount(0);
    } finally {
      obsolete.release();
    }
    const abandoned = await holdResponse(page, '**/api/udfmetadatasecurity/testfillrule', 'POST');
    try {
      await dialog.getByRole('button', { name: /^(Test rule|Regel testen)$/ }).click();
      await abandoned.arrived;
      const cancelled = page.waitForEvent('requestfailed', {
        predicate: (request) =>
          request.url().endsWith('/api/udfmetadatasecurity/testfillrule') &&
          request.failure()?.errorText === 'net::ERR_ABORTED',
        timeout: 10000
      });
      await closeMetadataDialog(dialog);
      await cancelled;
      abandoned.release();
      await abandoned.done;
      const reopened = await openCreateDialog(page, table);
      await expect(reopened.locator('udf-fill-test-result')).toHaveCount(0);
      await expect(reopened.locator('#description')).toHaveValue('');
      await closeMetadataDialog(reopened);
    } finally {
      abandoned.release();
    }
  });

  test('special-type toggles notify the table and keep system metadata immutable', async ({ page }) => {
    const row = rows.find((r) => r.kind === 'security')!;
    await loginAsFixtureUser(page, 'admin');
    const headers = { 'x-auth-token': (await page.evaluate(() => sessionStorage.getItem('jwt')))! };
    const preferenceUrl = '/api/udfspecialtypedisableuser';
    const name = 'UDF_SPEC_INTERNAL_YAHOO_EARNING_LINK';
    const disabledResponse = await page.request.get(preferenceUrl, { headers });
    expect(disabledResponse.ok()).toBeTruthy();
    const wasDisabled = ((await disabledResponse.json()) as (number | string)[]).some((v) => v === 2 || v === name);
    // Repair a failed run and restore the original preference in finally, without changing system definitions.
    if (wasDisabled) expect((await page.request.delete(`${preferenceUrl}/2`, { headers })).ok()).toBeTruthy();
    try {
      const table = await openMetadataView(page, row);
      const system = (await readMetadata(page, row)).find((r: any) => r.idUser === 0 && r.udfSpecialType === name)!;
      expect(system).toBeTruthy();
      const target = table.locator('tbody tr').filter({
        has: page.locator('td:first-child').filter({ hasText: new RegExp(`^\\s*${system.uiOrder}\\s*$`) })
      });
      await expect(target).toHaveCount(1);
      const check = target.locator('td').nth(2).locator('.fa-check');
      let menu = await rowMenu(page, target);
      await expect(menu.getByRole('menuitem', { name: /^(Edit|Bearbeiten)\b/ })).toBeDisabled();
      await expect(menu.getByRole('menuitem', { name: /^(Delete|Löschen)\b/ })).toBeDisabled();
      const toggle = /Switching on or off|Ein- bzw\. Ausschalten|UDF_TURN_ON_OFF_FIELD_USER0/i;
      const pending = await holdResponse(page, `**${preferenceUrl}`);
      try {
        await menu.getByRole('menuitem', { name: toggle }).click();
        await pending.arrived;
        await expect(check).toHaveCount(0);
        menu = await rowMenu(page, target);
        await expect(menu.getByRole('menuitem', { name: toggle })).toBeDisabled();
        await page.keyboard.press('Escape');
        pending.release();
        await pending.done;
        await expect(check).toHaveCount(1);
      } finally {
        pending.release();
      }
      menu = await rowMenu(page, target);
      await menu.getByRole('menuitem', { name: toggle }).click();
      await expect(check).toHaveCount(0);
    } finally {
      const current = await page.request.get(preferenceUrl, { headers });
      expect(current.ok()).toBeTruthy();
      const isDisabled = ((await current.json()) as (number | string)[]).some((v) => v === 2 || v === name);
      if (isDisabled !== wasDisabled) {
        const restored = wasDisabled
          ? await page.request.post(preferenceUrl, { headers, data: 2 })
          : await page.request.delete(`${preferenceUrl}/2`, { headers });
        expect(restored.ok()).toBeTruthy();
      }
    }
  });
});
