import {expect, Locator, Page, test} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

import {
  enumLabelRx,
  enumLabelWithQualifiersRx,
  fillNumber,
  openContextMenu,
  selectByValue
} from './generic-connector.helpers';
import {loginAsFixtureUser, parseCsvRow} from './helpers';

interface EntityLimitFixtureRow {
  limitType: 'MAX' | 'DAY_CUD' | 'DAY_READ';
  entityName: string;
  relationEntityName: string | null;
  countScope: 'ALL' | 'SINGLE' | null;
  ownerScope: 'TENANT' | 'GLOBAL' | 'CREATOR' | null;
  roleName: string;
  limitValue: number;
  validUntil: string | null;
  e2e: 'i' | 'e2e';
}

interface ApiEntityLimit {
  idEntityLimit: number;
  keyId: string;
  limitType: string;
  entityName: string;
  idRole: number | null;
  idUser: number | null;
  limitValue: number;
  validUntil: string | null;
}

interface ApiSelectOption {
  key: string;
  value: string;
}

const FIXTURE_PATH = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/limit_entity.csv');
const EXPECTED_HEADER = ['limitType', 'entityName', 'relationEntityName', 'countScope', 'ownerScope', 'roleName',
  'limitValue', 'validUntil', 'e2e'];

const RX = {
  adminDataNode: /(Administrative data|Administrative Daten|ADMIN_DATA)/i,
  entityLimitNode: /(Limit information class|Limite Informationsklasse|ENTITY_LIMIT_INFO_CLASS)/i,
  createItem: /^(Create|Erstellen)\b/i,
};

function keyId(row: EntityLimitFixtureRow): string {
  return [row.limitType, row.entityName, row.relationEntityName ?? '', row.countScope ?? '', row.ownerScope ?? '']
    .join('|');
}

function toLabelKey(entityName: string): string {
  return entityName.replace(/([A-Z]+)([A-Z][a-z])/g, '$1_$2')
    .replace(/([a-z])([A-Z])/g, '$1_$2').toUpperCase();
}

/** Loads and validates both routing groups so a customization cannot silently break the other test layer. */
function loadFixture(): EntityLimitFixtureRow[] {
  const lines = fs.readFileSync(FIXTURE_PATH, 'utf-8').split(/\r?\n/)
    .filter(line => line.trim().length > 0);
  const header = parseCsvRow(lines.shift() ?? '');
  if (header.join('|') !== EXPECTED_HEADER.join('|')) {
    throw new Error(`Unexpected header in ${FIXTURE_PATH}: ${header.join('|')}`);
  }

  const naturalKeys = new Set<string>();
  const rows = lines.map((line, index) => {
    const columns = parseCsvRow(line);
    if (columns.length !== EXPECTED_HEADER.length) {
      throw new Error(`Invalid column count in ${FIXTURE_PATH} line ${index + 2}: ${line}`);
    }
    const [limitType, entityName, relationEntityNameText, countScopeText, ownerScopeText, roleName, limitValueText,
      validUntilText, routing] = columns;
    const limitValue = Number(limitValueText);
    if (!['MAX', 'DAY_CUD', 'DAY_READ'].includes(limitType) || !entityName || roleName !== 'ROLE_LIMITEDIT'
      || !Number.isInteger(limitValue) || limitValue < 1 || !['i', 'e2e'].includes(routing)) {
      throw new Error(`Invalid entity-limit fixture row in ${FIXTURE_PATH} line ${index + 2}: ${line}`);
    }
    if (countScopeText && !['ALL', 'SINGLE'].includes(countScopeText)) {
      throw new Error(`Invalid countScope in ${FIXTURE_PATH} line ${index + 2}: ${countScopeText}`);
    }
    if (ownerScopeText && !['TENANT', 'GLOBAL', 'CREATOR'].includes(ownerScopeText)) {
      throw new Error(`Invalid ownerScope in ${FIXTURE_PATH} line ${index + 2}: ${ownerScopeText}`);
    }
    if ((limitType === 'MAX') !== !!ownerScopeText
      || (limitType !== 'MAX' && (!!relationEntityNameText || !!countScopeText))) {
      throw new Error(`Invalid key shape in ${FIXTURE_PATH} line ${index + 2}: ${line}`);
    }
    if (validUntilText && !/^\d{4}-\d{2}-\d{2}$/.test(validUntilText)) {
      throw new Error(`Invalid validUntil in ${FIXTURE_PATH} line ${index + 2}: ${validUntilText}`);
    }
    const row: EntityLimitFixtureRow = {
      limitType: limitType as EntityLimitFixtureRow['limitType'],
      entityName,
      relationEntityName: relationEntityNameText || null,
      countScope: countScopeText ? countScopeText as NonNullable<EntityLimitFixtureRow['countScope']> : null,
      ownerScope: ownerScopeText ? ownerScopeText as NonNullable<EntityLimitFixtureRow['ownerScope']> : null,
      roleName,
      limitValue,
      validUntil: validUntilText || null,
      e2e: routing as EntityLimitFixtureRow['e2e'],
    };
    const naturalKey = `${keyId(row)}|${roleName}`;
    if (naturalKeys.has(naturalKey)) {
      throw new Error(`Duplicate natural key in ${FIXTURE_PATH}: ${naturalKey}`);
    }
    naturalKeys.add(naturalKey);
    return row;
  });

  if (rows.length !== 32 || rows.filter(row => row.e2e === 'i').length !== 26
    || rows.filter(row => row.e2e === 'e2e').length !== 6) {
    throw new Error(`Expected a 26/6 integration/E2E split across 32 rows in ${FIXTURE_PATH}`);
  }
  return rows;
}

const LIMITS = loadFixture().filter(row => row.e2e === 'e2e');

async function authHeaders(page: Page): Promise<{[key: string]: string}> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  return {'x-auth-token': token!};
}

async function readLimits(page: Page): Promise<ApiEntityLimit[]> {
  const response = await page.request.get('/api/entitylimit', {headers: await authHeaders(page)});
  expect(response.ok(), `loading entity limits: ${await response.text()}`).toBeTruthy();
  return response.json() as Promise<ApiEntityLimit[]>;
}

async function resolveRoleId(page: Page, roleName: string): Promise<number> {
  const response = await page.request.get('/api/entitylimit/roles', {headers: await authHeaders(page)});
  expect(response.ok(), `loading entity-limit roles: ${await response.text()}`).toBeTruthy();
  const roles = await response.json() as ApiSelectOption[];
  const role = roles.find(option => option.value === roleName);
  expect(role, `role ${roleName} returned by /api/entitylimit/roles`).toBeTruthy();
  return Number(role!.key);
}

/** Deletes only the four rows owned by this spec, making retries safe after a partial run. */
async function deleteE2ELimits(page: Page, idRole: number): Promise<void> {
  const headers = await authHeaders(page);
  const ownedKeys = new Set(LIMITS.map(keyId));
  for (const limit of await readLimits(page)) {
    if (limit.idRole === idRole && limit.idUser == null && ownedKeys.has(limit.keyId)) {
      const response = await page.request.delete(`/api/entitylimit/${limit.idEntityLimit}`, {headers});
      expect(response.ok(), `deleting entity limit ${limit.idEntityLimit}: ${await response.text()}`).toBeTruthy();
    }
  }
}

async function openEntityLimitView(page: Page): Promise<Locator> {
  const node = page.locator('.p-tree-node-content', {hasText: RX.entityLimitNode}).first();
  if (!await node.isVisible()) {
    const root = page.locator('.p-tree-node-content', {hasText: RX.adminDataNode}).first();
    await root.waitFor({state: 'visible', timeout: 15_000});
    await root.dblclick();
  }
  await node.waitFor({state: 'visible', timeout: 15_000});
  await node.click();
  await page.waitForURL(/\/mainview\/entitylimit/, {timeout: 10_000});

  const table = page.locator('configurable-table').first();
  await table.waitFor({state: 'visible', timeout: 15_000});
  await page.waitForTimeout(500);
  return table;
}

async function selectLimitKey(page: Page, dialog: Locator, row: EntityLimitFixtureRow): Promise<void> {
  const dropdown = dialog.locator('#keyId');
  await expect(dropdown).toBeVisible({timeout: 10_000});
  await dropdown.click();

  const overlay = page.locator('.p-select-overlay').first();
  await overlay.waitFor({state: 'visible', timeout: 10_000});
  const labelKeys = [toLabelKey(row.entityName),
    ...(row.relationEntityName ? [toLabelKey(row.relationEntityName)] : [])];
  const qualifierKeys = [`LIMIT_TYPE_${row.limitType}`,
    ...(row.countScope ? [`COUNT_SCOPE_${row.countScope}`] : []),
    ...(row.ownerScope ? [`OWNER_SCOPE_${row.ownerScope}`] : [])];
  const options = overlay.locator('[role="option"]')
    .filter({hasText: enumLabelWithQualifiersRx(labelKeys, qualifierKeys)});
  await expect(options, `one key option for ${keyId(row)}`).toHaveCount(1);
  await options.first().click();
  await overlay.waitFor({state: 'hidden', timeout: 10_000});
}

async function createLimit(page: Page, table: Locator, row: EntityLimitFixtureRow,
    idRole: number): Promise<ApiEntityLimit> {
  const menu = await openContextMenu(page);
  await menu.getByRole('menuitem', {name: RX.createItem}).first().click();

  const dialog = page.locator('.p-dialog:visible').first();
  await dialog.waitFor({state: 'visible', timeout: 10_000});
  await selectLimitKey(page, dialog, row);
  await selectByValue(dialog, 'idRole', String(idRole));
  await fillNumber(dialog, 'limitValue', row.limitValue);

  const saved = page.waitForResponse(response => response.url().includes('/api/entitylimit')
    && response.request().method() === 'POST', {timeout: 20_000});
  await dialog.locator('button[type="submit"]').click();
  const response = await saved;
  expect(response.ok(), `creating ${row.limitType}/${row.entityName}: ${await response.text()}`).toBeTruthy();
  await dialog.waitFor({state: 'hidden', timeout: 15_000});
  const created = await response.json() as ApiEntityLimit;

  expect(created).toMatchObject({
    keyId: keyId(row),
    limitType: row.limitType,
    entityName: row.entityName,
    idRole,
    idUser: null,
    limitValue: row.limitValue,
    validUntil: row.validUntil,
  });

  const emptyCell = /^\s*$/;
  const tableRow = table.locator('tbody tr')
    .filter({has: page.locator('td:nth-child(1)', {hasText: enumLabelRx(toLabelKey(row.entityName))})})
    .filter({has: page.locator('td:nth-child(3)', {hasText: enumLabelRx(`LIMIT_TYPE_${row.limitType}`)})})
    .filter({has: page.locator('td:nth-child(4)', {
      hasText: row.relationEntityName ? enumLabelRx(toLabelKey(row.relationEntityName)) : emptyCell
    })})
    .filter({has: page.locator('td:nth-child(5)', {
      hasText: row.countScope ? enumLabelRx(`COUNT_SCOPE_${row.countScope}`) : emptyCell
    })})
    .filter({has: page.locator('td:nth-child(6)', {
      hasText: row.ownerScope ? enumLabelRx(`OWNER_SCOPE_${row.ownerScope}`) : emptyCell
    })})
    .filter({has: page.locator('td:nth-child(7)', {hasText: enumLabelRx(row.roleName)})});
  await expect(tableRow, `table row for ${keyId(row)}/${row.roleName}`).toHaveCount(1, {timeout: 10_000});
  await expect(tableRow.locator('td:nth-child(9)')).toContainText(String(row.limitValue));
  return created;
}

test('admin creates the E2E-owned ROLE_LIMITEDIT limits from CSV', async ({page}) => {
  await loginAsFixtureUser(page, 'admin');
  const idRole = await resolveRoleId(page, LIMITS[0].roleName);
  await deleteE2ELimits(page, idRole);
  const table = await openEntityLimitView(page);

  for (const row of LIMITS) {
    await createLimit(page, table, row, idRole);
  }

  const persisted = await readLimits(page);
  for (const row of LIMITS) {
    const matches = persisted.filter(limit => limit.idRole === idRole && limit.idUser == null
      && limit.keyId === keyId(row));
    expect(matches, `one persisted row for ${row.entityName}/${row.roleName}`).toHaveLength(1);
    expect(matches[0]).toMatchObject({
      limitType: row.limitType,
      entityName: row.entityName,
      limitValue: row.limitValue,
      validUntil: row.validUntil,
    });
  }
});
