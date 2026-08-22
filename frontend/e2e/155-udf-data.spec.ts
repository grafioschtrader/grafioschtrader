import { expect, Locator, Page, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

import { fillText } from './generic-connector.helpers';
import { loginAsFixtureUser } from './helpers';

type UdfValue = string | number | boolean | null;

interface UdfDataBaseFixture {
  loginNickname: string;
  watchlistName: string;
  values: Record<string, UdfValue>;
  e2e: 'd' | 'i' | 'e';
}

interface SecurityUdfDataFixture extends UdfDataBaseFixture {
  entity: 'Security';
  securityName: string;
  isin: string;
  currency: string;
}

interface CurrencypairUdfDataFixture extends UdfDataBaseFixture {
  entity: 'Currencypair';
  fromCurrency: string;
  toCurrency: string;
}

type UdfDataFixture = SecurityUdfDataFixture | CurrencypairUdfDataFixture;

interface UdfDataFixtureFile {
  udfData: UdfDataFixture[];
}

interface ApiUdfDataWrite {
  uDFDataKey: {
    idUser: number;
    entity: string;
    idEntity: number;
  };
  jsonValues: Record<string, UdfValue>;
}

interface ApiUdfDataResponse {
  jsonValues: Record<string, UdfValue>;
}

const FIXTURE_PATH = path.resolve(
  __dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/udf_data.json'
);

const RX = {
  editUdfItem: /^(Edit additional field|Bearbeite Zusatzfeld)/i
};

function loadRows(): UdfDataFixture[] {
  if (!fs.existsSync(FIXTURE_PATH)) {
    console.warn(`Fixture ${FIXTURE_PATH} not found - skipping the UDF data e2e spec.`);
    return [];
  }
  return (JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf8')) as UdfDataFixtureFile).udfData.filter(
    (row) => row.e2e === 'e'
  );
}

function exactText(value: string): RegExp {
  return new RegExp(`^\\s*${value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`);
}

function fixtureName(row: UdfDataFixture): string {
  return row.entity === 'Security' ? `${row.securityName}/${row.currency}` : `${row.fromCurrency}/${row.toCurrency}`;
}

async function openWatchlist(page: Page, name: string): Promise<void> {
  const node = page.getByRole('treeitem', { name, exact: true }).first();
  await node.waitFor({ state: 'visible', timeout: 15_000 });
  await node.click();
  await page.locator('.data-container').first().waitFor({ state: 'visible', timeout: 15_000 });
  await page.waitForTimeout(1500);
}

function instrumentRow(page: Page, row: UdfDataFixture): Locator {
  const rows = page.locator('.data-container p-table tbody tr');
  if (row.entity === 'Security') {
    return rows
      .filter({ has: page.locator('td').filter({ hasText: exactText(row.securityName) }) })
      .filter({ has: page.locator('td').filter({ hasText: exactText(row.currency) }) })
      .first();
  }
  const pairText = new RegExp(`\\b${row.fromCurrency}\\b.*\\b${row.toCurrency}\\b`);
  return rows.filter({ has: page.locator('td').filter({ hasText: pairText }) }).first();
}

async function openUdfDialog(page: Page, row: UdfDataFixture): Promise<Locator> {
  const instrument = instrumentRow(page, row);
  await expect(instrument, `${fixtureName(row)} in ${row.watchlistName}`).toBeVisible({ timeout: 15_000 });

  let editItem: Locator | undefined;
  for (let attempt = 0; attempt < 2; attempt++) {
    await instrument.click();
    await page.waitForTimeout(300);
    await instrument.click({ button: 'right' });
    const menu = page.locator('[role="menu"]:visible');
    await menu.waitFor({ state: 'visible', timeout: 5_000 });
    const candidate = menu.getByRole('menuitem', { name: RX.editUdfItem }).first();
    if (await candidate.isVisible().catch(() => false)) {
      editItem = candidate;
      break;
    }
    await page.keyboard.press('Escape');
  }
  expect(editItem, `additional-field action for ${fixtureName(row)}`).toBeDefined();
  const loadResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new RegExp(`/api/udfdata/${row.entity}/\\d+$`).test(new URL(response.url()).pathname),
    { timeout: 20_000 }
  );
  await editItem!.click();

  const component = row.entity === 'Security' ? page.locator('udf-security-edit') : page.locator('udf-general-edit');
  const dialog = component.locator('.p-dialog').first();
  await dialog.waitFor({ state: 'visible', timeout: 10_000 });
  const loadResponse = await loadResponsePromise;
  expect([200, 204], `loading existing UDF data: ${await loadResponse.text()}`).toContain(loadResponse.status());
  return dialog;
}

async function populateForm(dialog: Locator, row: UdfDataFixture): Promise<Record<string, UdfValue>> {
  const expectedValues: Record<string, UdfValue> = {};
  for (const [description, value] of Object.entries(row.values)) {
    const label = dialog
      .locator('label')
      .filter({ hasText: exactText(description) })
      .first();
    await expect(label, `UDF field '${description}'`).toBeVisible({ timeout: 10_000 });
    const fieldName = await label.getAttribute('for');
    expect(fieldName, `field name for '${description}'`).toMatch(/^f\d+$/);

    const field = dialog.locator(`#${fieldName}`);
    if (typeof value === 'boolean') {
      await field.setChecked(value);
    } else if (typeof value === 'string') {
      await fillText(dialog, `#${fieldName}`, value);
    } else {
      throw new Error(`Unsupported UDF fixture value for '${description}': ${String(value)}`);
    }
    expectedValues[fieldName!] = value;
  }
  return expectedValues;
}

async function saveAndVerify(
  page: Page,
  dialog: Locator,
  row: UdfDataFixture,
  expectedValues: Record<string, UdfValue>
): Promise<void> {
  const responsePromise = page.waitForResponse(
    (response) =>
      ['POST', 'PUT'].includes(response.request().method()) && /\/api\/udfdata$/.test(new URL(response.url()).pathname),
    { timeout: 20_000 }
  );
  await dialog.locator('button[type="submit"]').click();
  const response = await responsePromise;
  const responseText = await response.text();
  expect(response.ok(), `saving UDF data: ${responseText}`).toBeTruthy();
  const request = response.request().postDataJSON() as ApiUdfDataWrite;
  expect(request.uDFDataKey.entity).toBe(row.entity);
  expect(request.jsonValues).toEqual(expectedValues);
  const saved = JSON.parse(responseText) as ApiUdfDataResponse;
  expect(saved.jsonValues).toEqual(expectedValues);
  await dialog.waitFor({ state: 'hidden', timeout: 10_000 });

  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  const persistedResponse = await page.request.get(`/api/udfdata/${row.entity}/${request.uDFDataKey.idEntity}`, {
    headers: { 'x-auth-token': token! }
  });
  const persistedText = await persistedResponse.text();
  expect(persistedResponse.ok(), `loading persisted UDF data: ${persistedText}`).toBeTruthy();
  const persisted = JSON.parse(persistedText) as ApiUdfDataResponse;
  expect(persisted.jsonValues).toEqual(expectedValues);
}

test.describe.serial('Populate instrument UDF data from the exported fixture', () => {
  for (const row of loadRows()) {
    test(`${row.loginNickname} populates ${fixtureName(row)} additional fields`, async ({ page }) => {
      await loginAsFixtureUser(page, row.loginNickname);
      await openWatchlist(page, row.watchlistName);
      const dialog = await openUdfDialog(page, row);
      const expectedValues = await populateForm(dialog, row);
      await saveAndVerify(page, dialog, row, expectedValues);
    });
  }
});
