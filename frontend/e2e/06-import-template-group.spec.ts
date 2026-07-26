import {expect, Page, Response, test} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import {loginAsCsvUser} from './helpers';
import {fillText, openContextMenu, selectByValue} from './generic-connector.helpers';

/**
 * Creates the import template group 'Grafioschtrader' as user 'alledit' and adds one import
 * template per .tmpl file in backend testdata/import_template through the
 * ImportTransactionEditTemplateComponent dialog (NOT the drag-and-drop upload zone).
 *
 * The .tmpl filename encodes the dialog metadata as {category}-{format}-{yyyyMMdd}-{language}.tmpl
 * (e.g. buy_sell_instrument-pdf-20000101-de.tmpl) — the same convention the backend upload endpoint
 * parses. The file body goes verbatim into the templateAsTxt textarea; its trailing
 * 'templatePurpose=...' configuration line provides the purpose field.
 *
 * 'alledit' runs a German UI (locale de-CH in users.csv), so every text selector matches DE + EN +
 * the raw NLS key, and dates are typed in the de-CH calendar input format (dd.mm.y → '01.01.00').
 */

const CREATOR = 'alledit';
const GROUP_NAME = 'Grafioschtrader';

const TEMPLATE_DIR = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/import_template');

// Menu labels are built as CREATE|<entity key> and translated to 'Create <entity>...' /
// 'Erstellen <entity>...'. The template regex is anchored so it cannot match the group item
// ('Create Import template set...' contains 'Create Import template' as a prefix).
const CREATE_GROUP_RX =
  /(Create|Erstellen)\s*(Import template set|Import Vorlagengruppe)|CREATE\|IMPORT_TRANSACTION_PLATFORM/i;
const CREATE_TEMPLATE_RX =
  /^\s*((Create|Erstellen)\s+(Import template|Importvorlage)|CREATE\|IMPORT_TRANSACTION_TEMPLATE)\s*(\.\.\.)?\s*$/i;

interface TemplateFileData {
  fileName: string;
  templateCategory: string;    // BUY_SELL_INSTRUMENT | PAID_DIVIDEND_INTEREST | FINANCE_COST
  templateFormatType: string;  // PDF
  validSinceInput: string;     // typed into the de-CH p-datepicker (format dd.mm.y): '01.01.00'
  validSinceIso: string;       // expected round-trip in the REST payload: '2000-01-01'
  templateLanguage: string;    // de | en (language select option values are ISO codes)
  templatePurpose: string;     // from the file's 'templatePurpose=' line; unique per file
  templateAsTxt: string;       // full file content
}

/** Builds one dialog descriptor per .tmpl file from the filename metadata and file body. */
function loadTemplateFiles(): TemplateFileData[] {
  if (!fs.existsSync(TEMPLATE_DIR)) {
    return [];
  }
  return fs.readdirSync(TEMPLATE_DIR).filter(f => f.endsWith('.tmpl')).sort().map(fileName => {
    const [category, format, validSince, language] = fileName.replace(/\.tmpl$/, '').split('-');
    const templateAsTxt = fs.readFileSync(path.join(TEMPLATE_DIR, fileName), 'utf-8');
    const purposeMatch = /^templatePurpose=(.+)$/m.exec(templateAsTxt);
    if (!purposeMatch) {
      throw new Error(`${fileName}: missing templatePurpose= line`);
    }
    const [y, m, d] = [validSince.slice(0, 4), validSince.slice(4, 6), validSince.slice(6, 8)];
    return {
      fileName,
      templateCategory: category.toUpperCase(),
      templateFormatType: format.toUpperCase(),
      validSinceInput: `${d}.${m}.${y.slice(2)}`,
      validSinceIso: `${y}-${m}-${d}`,
      templateLanguage: language,
      templatePurpose: purposeMatch[1].trim(),
      templateAsTxt,
    };
  });
}

/** Navigates to the 'Import Vorlagengruppe' base-data view after login and waits for the master view. */
async function openImportTemplateView(page: Page): Promise<void> {
  // The node lives under the collapsed 'Base Data - ...' root — expand it first (fresh login ⇒ collapsed).
  const baseDataNode = page.locator('.p-tree-node-content', {hasText: /(Base Data|Basisdaten)/i}).first();
  await baseDataNode.waitFor({state: 'visible', timeout: 15_000});
  await baseDataNode.dblclick();

  const treeNode = page.locator('.p-tree-node-content', {
    hasText: /(Import template set|Import Vorlagengruppe|IMPORT_TRANSACTION_PLATFORM)/i
  }).first();
  await treeNode.waitFor({state: 'visible', timeout: 15_000});
  await treeNode.click();

  await page.locator('.data-container').first().waitFor({state: 'visible', timeout: 15_000});
  // Let readData() populate the template group dropdown before the caller inspects it.
  await page.waitForTimeout(800);
}

/** Selects the template group in the master dropdown; the child template table reloads on change. */
async function selectGroup(page: Page, name: string): Promise<void> {
  const select = page.locator('select#idTransactionImportPlatform');
  const option = select.locator('option', {hasText: name}).first();
  await option.waitFor({state: 'attached', timeout: 10_000});
  await select.selectOption({value: await option.getAttribute('value')});
  await select.dispatchEvent('change');
  await page.waitForTimeout(500);
}

/** Waits for a save response and reports a rejected request immediately with its response body. */
async function submitAndWaitForPost(page: Page, endpoint: string, submit: () => Promise<void>): Promise<Response> {
  const responsePromise = page.waitForResponse(r => r.url().includes(endpoint)
    && r.request().method() === 'POST', {timeout: 20_000});
  await submit();
  const response = await responsePromise;
  if (!response.ok()) {
    throw new Error(`POST ${endpoint} returned ${response.status()}: ${await response.text()}`);
  }
  return response;
}

/** Fills and submits the template edit dialog; asserts validSince survived the calendar round-trip. */
async function createTemplate(page: Page, t: TemplateFileData): Promise<void> {
  const menu = await openContextMenu(page);
  await menu.getByText(CREATE_TEMPLATE_RX).first().click();

  const dialog = page.locator('.p-dialog');
  await dialog.waitFor({state: 'visible', timeout: 10_000});

  await fillText(dialog, 'input#templatePurpose', t.templatePurpose);
  // Select option values are the enum names / ISO language codes; the language options are loaded
  // asynchronously by the dialog's initialize() — selectOption waits for them.
  await selectByValue(dialog, 'templateCategory', t.templateCategory);
  await selectByValue(dialog, 'templateFormatType', t.templateFormatType);

  // p-datepicker exposes an inner <input>; parsing follows formConfig.dateFormat (de-CH: dd.mm.y).
  // The date MUST be typed with real key events: PrimeNG's onUserInput ignores input events that
  // were not preceded by a keydown (isKeydown guard), so fill()/dispatchEvent('input') never
  // reaches the model and the text is wiped again on blur.
  const dateInput = dialog.locator('#validSince input').first();
  await dateInput.click();
  await dateInput.pressSequentially(t.validSinceInput, {delay: 20});
  await dateInput.blur();

  await selectByValue(dialog, 'templateLanguage', t.templateLanguage);
  await fillText(dialog, 'textarea#templateAsTxt, #templateAsTxt', t.templateAsTxt);

  const response = await submitAndWaitForPost(page, '/importtransactiontemplate', () =>
    dialog.locator('button[type="submit"]').click());
  const saved = await response.json();
  // The two-digit-year calendar input is the most fragile field — verify the persisted date.
  expect(saved.validSince).toBe(t.validSinceIso);
  expect(saved.templateCategory).toBe(t.templateCategory);
  expect(saved.templateLanguage).toBe(t.templateLanguage);
  await dialog.waitFor({state: 'hidden', timeout: 15_000});
  // handleCloseDialog re-reads the template table.
  await page.waitForTimeout(500);
}

test.describe.serial('02: import template group — create group and templates as alledit', () => {
  // The template dialog holds a 30-row textarea — keep the submit button inside the viewport.
  test.use({viewport: {width: 1400, height: 1800}});

  const templateFiles = loadTemplateFiles();

  test(`creates import template group '${GROUP_NAME}'`, async ({page}) => {
    await loginAsCsvUser(page, CREATOR);
    await openImportTemplateView(page);

    // Idempotency: skip when the group already exists (dropdown option label = group name).
    const existing = page.locator('select#idTransactionImportPlatform option', {hasText: GROUP_NAME});
    if (await existing.count() > 0) {
      return;
    }

    const menu = await openContextMenu(page);
    await menu.getByText(CREATE_GROUP_RX).first().click();

    const dialog = page.locator('.p-dialog');
    await dialog.waitFor({state: 'visible', timeout: 10_000});
    await fillText(dialog, 'input#name', GROUP_NAME);
    // idCsvImportImplementation stays empty — the group holds PDF templates only.

    await submitAndWaitForPost(page, '/importtransactionplatform', () =>
      dialog.locator('button[type="submit"]').click());
    await dialog.waitFor({state: 'hidden', timeout: 15_000});

    // handleCloseEditDialog re-reads all groups into the master dropdown.
    await expect(page.locator('select#idTransactionImportPlatform option', {hasText: GROUP_NAME}))
      .toHaveCount(1, {timeout: 10_000});
  });

  test('creates one import template per testdata .tmpl file via the edit dialog', async ({page}) => {
    expect(templateFiles.length, `no .tmpl files found in ${TEMPLATE_DIR}`).toBeGreaterThan(0);
    test.setTimeout(240_000); // six dialog round-trips
    await loginAsCsvUser(page, CREATOR);
    await openImportTemplateView(page);
    await selectGroup(page, GROUP_NAME);

    for (const t of templateFiles) {
      // Idempotency: the dialog path has no server-side dedup (unlike the file upload), so skip
      // files whose purpose (unique per file) already has a row in the template table.
      const existingRow = page.locator('import-transaction-template-table tbody tr',
        {hasText: t.templatePurpose});
      if (await existingRow.count() > 0) {
        continue;
      }
      await createTemplate(page, t);
    }

    await expect(page.locator('import-transaction-template-table tbody tr'))
      .toHaveCount(templateFiles.length, {timeout: 10_000});
  });

  test('persisted templates survive a fresh login', async ({page}) => {
    expect(templateFiles.length, `no .tmpl files found in ${TEMPLATE_DIR}`).toBeGreaterThan(0);
    await loginAsCsvUser(page, CREATOR);
    await openImportTemplateView(page);
    await selectGroup(page, GROUP_NAME);

    const rows = page.locator('import-transaction-template-table tbody tr');
    await expect(rows).toHaveCount(templateFiles.length, {timeout: 10_000});
    for (const t of templateFiles) {
      const row = rows.filter({hasText: t.templatePurpose});
      await expect(row, `row for ${t.fileName}`).toHaveCount(1);
      // validSince column renders 2000-01-01 in the user's short date format (de-CH: 01.01.00).
      await expect(row.first()).toContainText(/01\.01\.(20)?00|2000-01-01/);
    }
  });
});
