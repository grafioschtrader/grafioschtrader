import {test, expect, Locator} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import {loginAsCsvUser, parseCsvRow} from './helpers';

interface SeRow {
  mic: string;
  name: string;
  countryCode: string;
  noMarketValue: string;
  secondaryMarket: string;
  timeOpen: string;
  timeClose: string;
  timeZone: string;
  website: string;
  e2e: string;
}

const CSV_PATH = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/generated/stockexchanges.csv');

function loadE2ERows(): SeRow[] {
  const csv = fs.readFileSync(CSV_PATH, 'utf-8');
  return csv.split(/\r?\n/)
    .filter(l => l.trim().length > 0)
    .map(line => {
      const [mic, name, countryCode, noMarketValue, secondaryMarket, timeOpen, timeClose, timeZone, website, e2e] =
        parseCsvRow(line);
      return {mic, name, countryCode, noMarketValue, secondaryMarket, timeOpen, timeClose, timeZone, website, e2e};
    })
    .filter(r => r.e2e === 'e');
}

// Toggle a PrimeNG p-checkbox to match the desired boolean state. The native <input> carries the id,
// and the neighbouring .p-checkbox-box receives the visual click.
async function setCheckbox(dialog: Locator, fieldId: string, desired: boolean): Promise<void> {
  const native = dialog.locator(`input#${fieldId}`);
  const isChecked = await native.isChecked().catch(() => false);
  if (isChecked !== desired) {
    await dialog.locator(`input#${fieldId} + .p-checkbox-box, #${fieldId} ~ .p-checkbox-box`).first().click();
  }
}

test.describe.serial('Create stockexchanges (e2e=\'e\' rows from shared CSV)', () => {
  const rows = loadE2ERows();

  for (const row of rows) {
    test(`creates Stockexchange: ${row.name} (${row.countryCode})`, async ({page}) => {
      await loginAsCsvUser(page, 'user');

      // 'Stock exchange' lives under the collapsed 'Base Data - Data change request' root — expand it first.
      const baseDataNode = page.locator('.p-tree-node-content', {hasText: /Base Data/i}).first();
      await baseDataNode.waitFor({state: 'visible', timeout: 15_000});
      await baseDataNode.dblclick();

      const treeNode = page.locator('.p-tree-node-content', {hasText: /^\s*Stock\s*exchange\s*$/i}).first();
      await treeNode.waitFor({state: 'visible', timeout: 15_000});
      await treeNode.click();

      // The Stock exchange view has no .data-container wrapper — right-click on the PrimeNG table itself.
      const contentArea = page.locator('p-table, .p-datatable').first();
      await contentArea.waitFor({state: 'visible', timeout: 10_000});
      await contentArea.click();
      await page.waitForTimeout(300);
      await contentArea.click({button: 'right'});

      const menuList = page.locator('[role="menu"]:visible');
      await menuList.waitFor({state: 'visible', timeout: 5_000});
      await menuList.getByText(/Create.*Stock.*exchange/i).first().click();

      const dialog = page.locator('.p-dialog');
      await dialog.waitFor({state: 'visible', timeout: 10_000});

      // Leave the MIC p-dropdown at its default empty entry — MIC is optional and empty → null on save.
      // The countryCode field stays enabled as long as MIC is empty (see disableEnableCountry()).

      // name — native text input
      const nameInput = dialog.locator('input#name');
      await nameInput.click();
      await nameInput.fill(row.name);
      await nameInput.dispatchEvent('input');
      await nameInput.blur();

      // countryCode — native <select> (createFieldSelectStringHeqF)
      const countrySelect = dialog.locator('select#countryCode');
      await countrySelect.selectOption({value: row.countryCode});
      await countrySelect.dispatchEvent('change');

      // Checkboxes — defaults: secondaryMarket=true, noMarketValue=false. Only click if CSV differs.
      await setCheckbox(dialog, 'secondaryMarket', row.secondaryMarket === 'true');
      await setCheckbox(dialog, 'noMarketValue', row.noMarketValue === 'true');

      // timeOpen / timeClose — DataType.TimeString, maxLength 8 (HH:MM:SS)
      const timeOpenInput = dialog.locator('input#timeOpen');
      await timeOpenInput.click();
      await timeOpenInput.fill(row.timeOpen);
      await timeOpenInput.dispatchEvent('input');
      await timeOpenInput.blur();

      const timeCloseInput = dialog.locator('input#timeClose');
      await timeCloseInput.click();
      await timeCloseInput.fill(row.timeClose);
      await timeCloseInput.dispatchEvent('input');
      await timeCloseInput.blur();

      // timeZone — native <select> populated from gps.getTimezones()
      const tzSelect = dialog.locator('select#timeZone');
      await tzSelect.selectOption({value: row.timeZone});
      await tzSelect.dispatchEvent('change');

      // website — optional URL. CSV may contain '\N' for null rows.
      if (row.website && row.website !== '\\N') {
        const websiteInput = dialog.locator('input#website');
        await websiteInput.click();
        await websiteInput.fill(row.website);
        await websiteInput.dispatchEvent('input');
        await websiteInput.blur();
      }

      await dialog.locator('button[type="submit"]').click();
      await dialog.waitFor({state: 'hidden', timeout: 10_000});

      await expect(page.locator('td', {hasText: row.name}).first()).toBeVisible({timeout: 5_000});
    });
  }
});
