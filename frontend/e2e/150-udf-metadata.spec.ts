import {expect, Locator, Page, test} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import {loginAsFixtureUser} from './helpers';
import {fillText, pickMultiSelect, selectByValue} from './generic-connector.helpers';

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

const FIXTURE_PATH = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/udf-metadata.json');

const RX = {
  baseDataNode: /(Base Data|Basisdaten)/i,
  generalNode: /^\s*(General definition of additional field|Definition Zusatzfeld allgemein|UDF_METADATA_GENERAL)\s*$/i,
  securityNode: /^\s*(Custom field Instrument|Zusatzfeld Instrument|UDF_METADATA_SECURITY)\s*$/i,
  createItem: /^(Create|Erstellen)\b/i,
};

function loadRows(): UdfMetadataRow[] {
  return (JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf-8')) as UdfMetadataRow[])
    .filter(row => row.e2e === 'e');
}

function endpoint(row: UdfMetadataRow): string {
  return `/api/${row.kind === 'general' ? 'udfmetadatageneral' : 'udfmetadatasecurity'}`;
}

function matchesNaturalKey(actual: ApiUdfMetadata, expected: UdfMetadataRow): boolean {
  return actual.description === expected.description
    && (expected.kind === 'security' || actual.entity === expected.entity);
}

async function readMetadata(page: Page, row: UdfMetadataRow): Promise<ApiUdfMetadata[]> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  const response = await page.request.get(endpoint(row), {headers: {'x-auth-token': token!}});
  expect(response.ok(), `loading ${row.kind} UDF metadata: ${await response.text()}`).toBeTruthy();
  return response.json() as Promise<ApiUdfMetadata[]>;
}

/** Deletes only a matching user-owned leftover from an earlier interrupted run. System rows are immutable here. */
async function deleteOwnLeftovers(page: Page, row: UdfMetadataRow): Promise<void> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  const matches = (await readMetadata(page, row)).filter(actual =>
    actual.idUser !== 0 && matchesNaturalKey(actual, row));
  for (const match of matches) {
    const response = await page.request.delete(`${endpoint(row)}/${match.idUDFMetadata}`,
      {headers: {'x-auth-token': token!}});
    expect(response.ok(), `deleting leftover UDF metadata ${match.idUDFMetadata}: ${await response.text()}`)
      .toBeTruthy();
  }
}

async function openMetadataView(page: Page, row: UdfMetadataRow): Promise<Locator> {
  const baseDataNode = page.locator('.p-tree-node-content', {hasText: RX.baseDataNode}).first();
  await baseDataNode.waitFor({state: 'visible', timeout: 15_000});
  await baseDataNode.dblclick();

  const nodeRx = row.kind === 'general' ? RX.generalNode : RX.securityNode;
  const treeNode = page.locator('.p-tree-node-content', {hasText: nodeRx}).first();
  await treeNode.waitFor({state: 'visible', timeout: 15_000});
  await treeNode.click();
  await page.waitForURL(new RegExp(`/mainview/udfmetadata${row.kind}`), {timeout: 10_000});

  const table = page.locator('configurable-table').first();
  await table.waitFor({state: 'visible', timeout: 10_000});
  return table;
}

async function openCreateDialog(page: Page, table: Locator): Promise<Locator> {
  const caption = table.locator('h4').first();
  await caption.click();
  await page.waitForTimeout(300);
  await caption.click({button: 'right'});

  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({state: 'visible', timeout: 5_000});
  await menu.getByRole('menuitem', {name: RX.createItem}).first().click();

  const dialog = page.locator('.p-dialog:visible').first();
  await dialog.waitFor({state: 'visible', timeout: 10_000});
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
    await entitySelect.locator(`option[value="${row.entity}"]`).waitFor({state: 'attached', timeout: 10_000});
    if (await entitySelect.isDisabled()) {
      await expect(entitySelect).toHaveValue(row.entity);
    } else {
      await selectByValue(dialog, 'entity', row.entity);
    }
  }
  await fillBaseFields(dialog, row);
  if (row.kind === 'security') {
    await pickMultiSelect(page, dialog, 'categoryTypeEnums', row.categoryTypeEnums);
    await pickMultiSelect(page, dialog, 'specialInvestmentInstrumentEnums',
      row.specialInvestmentInstrumentEnums);
  }

  await dialog.locator('button[type="submit"]').click();
  await dialog.waitFor({state: 'hidden', timeout: 10_000});
  await expect(table.locator('tr[data-p-selectable-row]', {hasText: row.description}).first())
    .toBeVisible({timeout: 10_000});
}

async function verifyPersisted(page: Page, row: UdfMetadataRow): Promise<void> {
  const matches = (await readMetadata(page, row)).filter(actual =>
    actual.idUser !== 0 && matchesNaturalKey(actual, row));
  expect(matches, `one user-owned persisted row for ${row.nickname}/${row.description}`).toHaveLength(1);
  const actual = matches[0];
  expect(actual).toMatchObject({
    description: row.description,
    descriptionHelp: row.descriptionHelp,
    udfDataType: row.udfDataType,
    fieldSize: row.fieldSize,
    uiOrder: row.uiOrder,
  });
  if (row.kind === 'general') {
    expect(actual.entity).toBe(row.entity);
  } else {
    expect(actual.categoryTypeEnums).toEqual(expect.arrayContaining(row.categoryTypeEnums));
    expect(actual.categoryTypeEnums).toHaveLength(row.categoryTypeEnums.length);
    expect(actual.specialInvestmentInstrumentEnums)
      .toEqual(expect.arrayContaining(row.specialInvestmentInstrumentEnums));
    expect(actual.specialInvestmentInstrumentEnums).toHaveLength(row.specialInvestmentInstrumentEnums.length);
  }
}

test.describe.serial('Create user-owned general and security UDF metadata from shared fixture', () => {
  for (const row of loadRows()) {
    test(`${row.nickname} creates ${row.kind} UDF metadata: ${row.description}`, async ({page}) => {
      await loginAsFixtureUser(page, row.nickname);
      await deleteOwnLeftovers(page, row);
      await createMetadata(page, row);
      await verifyPersisted(page, row);
    });
  }
});
