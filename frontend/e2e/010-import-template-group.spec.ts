import {expect, Page, Response, test} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import {loginAsFixtureUser} from './helpers';
import {fillText, openContextMenu, selectByValue} from './generic-connector.helpers';

/**
 * Creates the import template group 'Grafioschtrader' as user 'alledit' and adds one import
 * template per .tmpl file from the platform-specific directories below backend
 * testdata/import_template. 'Migros Bank' is provided by the test database and is reused. Templates
 * are created through the ImportTransactionEditTemplateComponent dialog (NOT the drag-and-drop
 * upload zone).
 *
 * Files are discovered solely by their .tmpl extension; no concrete filename is expected. The
 * backend filename convention ({category}-{format}-{yyyyMMdd}-{language}.tmpl) supplies the dialog
 * metadata. The file body goes verbatim into the templateAsTxt textarea; its trailing
 * 'templatePurpose=...' configuration line provides the purpose field.
 *
 * 'alledit' runs a German UI (locale de-CH in users.json), so every text selector matches DE + EN +
 * the raw NLS key, and dates are typed in the de-CH calendar input format (dd.mm.y → '01.01.00').
 */

const CREATOR = 'alledit';
const GRAFIOSCHTRADER_GROUP_NAME = 'Grafioschtrader';

const TEMPLATE_ROOT_DIR = path.resolve(__dirname,
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
  templateCategory: string;    // enum name parsed from the discovered file
  templateFormatType: string;  // enum name parsed from the discovered file
  validSinceInput: string;     // date converted to the de-CH p-datepicker format dd.mm.yy
  validSinceIso: string;       // expected round-trip in the REST payload
  templateLanguage: string;    // ISO language code parsed from the discovered file
  templatePurpose: string;     // from the file's templatePurpose= line
  templateAsTxt: string;       // full file content
}

interface TemplateGroupData {
  name: string;
  templateDir: string;
  templateFiles: TemplateFileData[];
}

/** Builds one dialog descriptor per .tmpl file from the filename metadata and file body. */
function loadTemplateFiles(templateDir: string): TemplateFileData[] {
  if (!fs.existsSync(templateDir)) {
    return [];
  }
  return fs.readdirSync(templateDir)
    .filter(fileName => path.extname(fileName).toLowerCase() === '.tmpl')
    .sort()
    .map(fileName => {
      const [category, format, validSince, language] = fileName.replace(/\.tmpl$/i, '').split('-');
      const templateAsTxt = fs.readFileSync(path.join(templateDir, fileName), 'utf-8');
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

const TEMPLATE_GROUPS: TemplateGroupData[] = [
  createTemplateGroupData(GRAFIOSCHTRADER_GROUP_NAME, 'grafioschtrader'),
  createTemplateGroupData('Migros Bank', 'migrosbank')
];

function createTemplateGroupData(name: string, directoryName: string): TemplateGroupData {
  const templateDir = path.join(TEMPLATE_ROOT_DIR, directoryName);
  return {name, templateDir, templateFiles: loadTemplateFiles(templateDir)};
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
  const initializationPromise = page.waitForResponse(response =>
    response.url().includes('/importtransactiontemplate/languages')
    && response.request().method() === 'GET');
  await menu.getByText(CREATE_TEMPLATE_RX).first().click();

  const dialog = page.locator('.p-dialog');
  await dialog.waitFor({state: 'visible', timeout: 10_000});
  const initializationResponse = await initializationPromise;
  expect(initializationResponse.ok(), 'template dialog language initialization failed').toBeTruthy();

  await fillText(dialog, 'input#templatePurpose', t.templatePurpose);
  // Select option values are the enum names / ISO language codes; the language options are loaded
  // asynchronously by the dialog's initialize() — selectOption waits for them.
  await selectByValue(dialog, 'templateCategory', t.templateCategory);
  await selectByValue(dialog, 'templateFormatType', t.templateFormatType);

  // p-datepicker exposes an inner <input>; parsing follows formConfig.dateFormat (de-CH: dd.mm.y).
  // The date MUST be typed with real key events: Optimus UI's onUserInput ignores input events that
  // were not preceded by a keydown (isKeydown guard), so fill()/dispatchEvent('input') never
  // reaches the model and the text is wiped again on blur.
  const dateInput = dialog.locator('#validSince input').first();
  await dateInput.click();
  await dateInput.pressSequentially(t.validSinceInput, {delay: 20});
  await dateInput.blur();

  await selectByValue(dialog, 'templateLanguage', t.templateLanguage);
  await fillText(dialog, 'textarea#templateAsTxt, #templateAsTxt', t.templateAsTxt);

  const submitButton = dialog.locator('button[type="submit"]');
  await expect(dialog.locator('input#templatePurpose')).toHaveValue(t.templatePurpose);
  await expect(submitButton).toBeEnabled();
  const response = await submitAndWaitForPost(page, '/importtransactiontemplate', () =>
    submitButton.click());
  const saved = await response.json();
  // The two-digit-year calendar input is the most fragile field — verify the persisted date.
  expect(saved.validSince).toBe(t.validSinceIso);
  expect(saved.templateCategory).toBe(t.templateCategory);
  expect(saved.templateLanguage).toBe(t.templateLanguage);
  await dialog.waitFor({state: 'hidden', timeout: 15_000});
  // handleCloseDialog re-reads the template table.
  await page.waitForTimeout(500);
}

test.describe.serial('import template group — create group and templates as alledit', () => {
  // The template dialog holds a 30-row textarea — keep the submit button inside the viewport.
  test.use({viewport: {width: 1400, height: 1800}});

  test(`creates import template group '${GRAFIOSCHTRADER_GROUP_NAME}'`, async ({page}) => {
    await loginAsFixtureUser(page, CREATOR);
    await openImportTemplateView(page);

    // Idempotency: skip when the group already exists (dropdown option label = group name).
    const existing = page.locator('select#idTransactionImportPlatform option',
      {hasText: GRAFIOSCHTRADER_GROUP_NAME});
    if (await existing.count() > 0) {
      return;
    }

    const menu = await openContextMenu(page);
    await menu.getByText(CREATE_GROUP_RX).first().click();

    const dialog = page.locator('.p-dialog');
    await dialog.waitFor({state: 'visible', timeout: 10_000});
    await fillText(dialog, 'input#name', GRAFIOSCHTRADER_GROUP_NAME);
    // idCsvImportImplementation stays empty — templates are configured individually.

    await submitAndWaitForPost(page, '/importtransactionplatform', () =>
      dialog.locator('button[type="submit"]').click());
    await dialog.waitFor({state: 'hidden', timeout: 15_000});

    // handleCloseEditDialog re-reads all groups into the master dropdown.
    await expect(page.locator('select#idTransactionImportPlatform option',
      {hasText: GRAFIOSCHTRADER_GROUP_NAME}))
      .toHaveCount(1, {timeout: 10_000});
  });

  test('creates one import template per platform-specific .tmpl file via the edit dialog', async ({page}) => {
    const totalTemplateCount = TEMPLATE_GROUPS.reduce((count, group) => count + group.templateFiles.length, 0);
    test.setTimeout(Math.max(240_000, totalTemplateCount * 30_000));
    await loginAsFixtureUser(page, CREATOR);
    await openImportTemplateView(page);

    for (const group of TEMPLATE_GROUPS) {
      expect(group.templateFiles.length, `no .tmpl files found in ${group.templateDir}`).toBeGreaterThan(0);
      await selectGroup(page, group.name);

      for (const t of group.templateFiles) {
        // Idempotency: the dialog path has no server-side dedup (unlike the file upload), so skip
        // files whose purpose (unique per file and group) already has a row in the template table.
        const existingRow = findTemplateRow(page, t);
        if (await existingRow.count() > 0) {
          continue;
        }
        await createTemplate(page, t);
      }

      // Migros Bank already contains legacy templates, so assert the requested templates rather
      // than the total number of rows in the group.
      await expectTemplatesPresent(page, group.templateFiles);
    }
  });

  test('persisted templates survive a fresh login', async ({page}) => {
    await loginAsFixtureUser(page, CREATOR);
    await openImportTemplateView(page);

    for (const group of TEMPLATE_GROUPS) {
      expect(group.templateFiles.length, `no .tmpl files found in ${group.templateDir}`).toBeGreaterThan(0);
      await selectGroup(page, group.name);
      await expectTemplatesPresent(page, group.templateFiles);
    }
  });
});

function findTemplateRow(page: Page, templateFile: TemplateFileData) {
  return page.locator('import-transaction-template-table tbody tr')
    .filter({hasText: templateFile.templatePurpose})
    .filter({hasText: templateFile.validSinceInput});
}

async function expectTemplatesPresent(page: Page, templateFiles: TemplateFileData[]): Promise<void> {
  for (const t of templateFiles) {
    const row = findTemplateRow(page, t);
    await expect(row, `row for ${t.fileName}`).toHaveCount(1, {timeout: 10_000});
  }
}
