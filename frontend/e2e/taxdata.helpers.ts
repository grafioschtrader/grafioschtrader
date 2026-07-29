import {expect, Locator, Page} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

/**
 * Helpers for the tax data administration view (Administrative data -> Tax data,
 * route /mainview/taxdata, TaxDataTreetableComponent).
 *
 * The view is a three level tree table: tax country -> tax year -> uploaded file. Every action is
 * reached through the context menu, whose entries depend on the level of the selected node.
 */

/**
 * The ICTax Kursliste zip files. They live next to the backend test resources but are git-ignored
 * (`*.zip`), so a fresh checkout does not have them and the spec skips.
 */
const ICTAX_DIR = path.resolve(__dirname, '../../backend/grafioschtrader-server/src/test/resources/ictax');
export const FULL_ZIP = path.join(ICTAX_DIR, 'kursliste_2025.zip');
export const DIFF_ZIP = path.join(ICTAX_DIR, 'kursliste_2025_diff.zip');

export const TAX_YEAR = 2025;
export const COUNTRY_CODE = 'CH';

export function fixturesPresent(): boolean {
  return fs.existsSync(FULL_ZIP) && fs.existsSync(DIFF_ZIP);
}

/**
 * The admin user runs locale de-CH, so every text selector matches the German and the English label.
 * The raw NLS key is accepted as well: after a hard reload the menu bar can still show unresolved
 * keys, and a missing translation should not turn into a confusing selector timeout.
 */
export const RX = {
  taxDataNode: /^\s*(Tax data|Steuerdaten|TAX_DATA)\s*$/i,
  country: /(Switzerland|Schweiz)/i,
  createCountry: /(Create tax country|Steuerland erstellen|CREATE_TAX_COUNTRY)/i,
  createYear: /(Create tax year|Steuerjahr erstellen|CREATE_TAX_YEAR)/i,
  uploadTaxData: /(Upload tax data|Steuerdaten hochladen|UPLOAD_TAX_DATA)/i,
  deleteItem: /(Delete|L.schen|DELETE)/i
};

/** File name of an upload as it is shown in the `name` column of the tree table. */
export function zipName(zipPath: string): string {
  return path.basename(zipPath);
}

/** Escapes a file name so it can be used inside a `hasText` regex without `.` matching anything. */
function exactText(text: string): RegExp {
  return new RegExp(`^\\s*${text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`);
}

/**
 * Opens Administrative data -> Tax data. The "Administrative data" root node is expanded by default
 * (admindata-main-tree.contributor.ts), so the leaf can be clicked directly.
 */
export async function openTaxDataView(page: Page): Promise<Locator> {
  const taxDataNode = page.getByRole('treeitem', {name: RX.taxDataNode}).first();
  await taxDataNode.waitFor({state: 'visible', timeout: 15_000});
  await taxDataNode.click();
  await page.waitForURL(/\/mainview\/taxdata(?:$|[/?#])/, {timeout: 15_000});

  const container = page.locator('.data-container', {has: page.locator('configurable-tree-table')}).first();
  await container.locator('p-treetable').waitFor({state: 'visible', timeout: 15_000});
  return container;
}

/**
 * The tree table row whose `name` cell matches the given text. The match is done on the first cell
 * and not on the whole row: a file row also carries the upload date and the record count, and the
 * year row would otherwise collide with the `2025` inside a file name and inside the upload date.
 */
export function nodeRow(container: Locator, text: string | RegExp): Locator {
  const nameRx = typeof text === 'string' ? exactText(text) : text;
  return container.locator('.p-treetable-tbody tr')
    .filter({has: container.page().locator('td:nth-child(1)').filter({hasText: nameRx})}).first();
}

/**
 * Opens the context menu of a row with a plain right-click and no preceding left-click - exactly how
 * a user reaches it. This is deliberate: ConfigurableTreeTableComponent.onRowContextMenu used to
 * update only the selection without emitting nodeSelect, so the host built the menu for the
 * previously left-clicked node. Should that regress, this helper picks the wrong menu entries and
 * the spec fails.
 */
export async function openNodeMenu(page: Page, row: Locator): Promise<Locator> {
  await row.waitFor({state: 'visible', timeout: 15_000});
  await row.click({button: 'right'});
  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({state: 'visible', timeout: 5_000});
  return menu;
}

/**
 * Opens the context menu with no node selected, which is the only state that offers
 * "Create tax country". Requires an empty tree, the state after the cleanup and after step 6.
 */
export async function openEmptyTreeMenu(page: Page, container: Locator): Promise<Locator> {
  await expect(container.locator('.p-treetable-tbody tr')).toHaveCount(0, {timeout: 10_000});
  await container.locator('configurable-tree-table').click({button: 'right'});
  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({state: 'visible', timeout: 5_000});
  return menu;
}

/** Clicks a context menu entry and waits until the menu is gone again. */
async function clickMenuItem(page: Page, menu: Locator, itemRx: RegExp): Promise<void> {
  await menu.getByText(itemRx).first().click();
  await page.locator('[role="menu"]:visible').waitFor({state: 'hidden', timeout: 5_000}).catch(() => undefined);
}

/**
 * Creates the tax country through the create dialog. The option value of the select is the ISO
 * country code, the visible label is the localised country name.
 */
export async function createTaxCountry(page: Page, container: Locator, countryCode: string): Promise<void> {
  const menu = await openEmptyTreeMenu(page, container);
  await clickMenuItem(page, menu, RX.createCountry);

  const dialog = page.locator('.p-dialog');
  await dialog.waitFor({state: 'visible', timeout: 10_000});
  const countrySelect = dialog.locator('select#countryCode');
  await countrySelect.selectOption({value: countryCode});
  await countrySelect.dispatchEvent('change');
  await dialog.locator('button[type="submit"]').click();
  await dialog.waitFor({state: 'hidden', timeout: 10_000});

  await expect(nodeRow(container, RX.country)).toBeVisible({timeout: 10_000});
}

/** Creates a tax year below the given country row. */
export async function createTaxYear(page: Page, container: Locator, countryRow: Locator, taxYear: number): Promise<void> {
  const menu = await openNodeMenu(page, countryRow);
  await clickMenuItem(page, menu, RX.createYear);

  const dialog = page.locator('.p-dialog');
  await dialog.waitFor({state: 'visible', timeout: 10_000});
  const yearSelect = dialog.locator('select#taxYear');
  await yearSelect.selectOption({value: String(taxYear)});
  await yearSelect.dispatchEvent('change');
  await dialog.locator('button[type="submit"]').click();
  await dialog.waitFor({state: 'hidden', timeout: 10_000});

  await expect(nodeRow(container, String(taxYear))).toBeVisible({timeout: 10_000});
}

/**
 * Uploads one zip through the upload dialog of the given tax year row and returns the imported
 * record count shown in the new file row.
 *
 * The backend stores the zip, extracts the XML and parses it completely in memory before filtering
 * by the ISINs held in GT, so the full Kursliste needs minutes rather than seconds.
 */
export async function uploadTaxData(page: Page, container: Locator, yearRow: Locator,
  zipPath: string): Promise<number> {
  const menu = await openNodeMenu(page, yearRow);
  await clickMenuItem(page, menu, RX.uploadTaxData);

  const dialog = page.locator('.p-dialog');
  await dialog.waitFor({state: 'visible', timeout: 10_000});
  const fileInput = dialog.locator('input#fileToUpload');
  await fileInput.setInputFiles(zipPath);
  await fileInput.dispatchEvent('change');

  const submit = dialog.locator('button[type="submit"]');
  await expect(submit).toBeEnabled({timeout: 10_000});
  await submit.click();
  await dialog.waitFor({state: 'hidden', timeout: 570_000});

  const fileRow = nodeRow(container, zipName(zipPath));
  await expect(fileRow).toBeVisible({timeout: 30_000});
  return recordCountOfRow(fileRow);
}

/**
 * Reads the `recordCount` cell of a file row. The value is rendered with the de-CH group separator,
 * so every non-digit is stripped before parsing.
 */
export async function recordCountOfRow(fileRow: Locator): Promise<number> {
  const text = await fileRow.locator('td').nth(2).innerText();
  const digits = text.replace(/\D/g, '');
  return digits.length === 0 ? 0 : Number(digits);
}

/** Deletes the node of the given row through its context menu and waits until it is gone. */
export async function deleteNode(page: Page, row: Locator): Promise<void> {
  const menu = await openNodeMenu(page, row);
  // No confirmation dialog: TaxDataTreetableComponent calls the delete endpoint directly.
  await clickMenuItem(page, menu, RX.deleteItem);
  await expect(row).toHaveCount(0, {timeout: 30_000});
}

/**
 * Removes a leftover tax country including its years and uploads, so the spec can be re-run against
 * a live grafioschtrader_t without a database reset. Deleting the country alone would cascade in the
 * database, but the file rows are deleted first so their stored zips are removed as well.
 */
export async function deleteTaxCountryIfPresent(page: Page, container: Locator): Promise<void> {
  const countryRow = nodeRow(container, RX.country);
  if (await countryRow.count() === 0) {
    return;
  }
  for (const zipPath of [FULL_ZIP, DIFF_ZIP]) {
    const fileRow = nodeRow(container, zipName(zipPath));
    if (await fileRow.count() > 0) {
      await deleteNode(page, fileRow);
    }
  }
  const yearRow = nodeRow(container, String(TAX_YEAR));
  if (await yearRow.count() > 0) {
    await deleteNode(page, yearRow);
  }
  if (await countryRow.count() > 0) {
    await deleteNode(page, countryRow);
  }
}
