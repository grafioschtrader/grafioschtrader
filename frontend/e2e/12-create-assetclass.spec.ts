import {test, expect} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import {loginAsCsvUser, parseCsvRow} from './helpers';
import {AssetclassType} from '../src/app/shared/types/assetclass.type';
import {SpecialInvestmentInstruments} from '../src/app/shared/types/special.investment.instruments';

interface AcRow {
  categoryType: string;
  specialInvestmentInstrument: string;
  subCategoryDE: string;
  subCategoryEN: string;
  e2e: string;
}

const CSV_PATH = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/generated/assetclasses.csv');

function loadE2ERows(): AcRow[] {
  const csv = fs.readFileSync(CSV_PATH, 'utf-8');
  return csv.split(/\r?\n/)
    .filter(l => l.trim().length > 0)
    .map(line => {
      const [categoryType, specialInvestmentInstrument, subCategoryDE, subCategoryEN, e2e] = parseCsvRow(line);
      return {categoryType, specialInvestmentInstrument, subCategoryDE, subCategoryEN, e2e};
    })
    .filter(r => r.e2e === 'e');
}

test.describe.serial('Create asset classes (e2e=\'e\' rows from shared CSV)', () => {
  const rows = loadE2ERows();

  for (const row of rows) {
    // Dedupe label in case the CSV has multiple 'Stocks Spain ' rows (with/without trailing space)
    const label = `${row.subCategoryEN.trim()} / ${AssetclassType[Number(row.categoryType)]} / ${SpecialInvestmentInstruments[Number(row.specialInvestmentInstrument)]}`;

    test(`creates AssetClass: ${label}`, async ({page}) => {
      await loginAsCsvUser(page, 'user');

      // 'Asset class' lives under the collapsed 'Base Data - Data change request' root — expand it first.
      const baseDataNode = page.locator('.p-tree-node-content', {hasText: /Base Data/i}).first();
      await baseDataNode.waitFor({state: 'visible', timeout: 15_000});
      await baseDataNode.dblclick();

      const treeNode = page.locator('.p-tree-node-content', {hasText: /^\s*Asset\s*class\s*$/i}).first();
      await treeNode.waitFor({state: 'visible', timeout: 15_000});
      await treeNode.click();

      // The Asset class view has no .data-container wrapper — right-click on the PrimeNG table itself
      // to open the context menu bound by pContextMenu on p-table.
      const contentArea = page.locator('p-table, .p-datatable').first();
      await contentArea.waitFor({state: 'visible', timeout: 10_000});
      await contentArea.click();
      await page.waitForTimeout(300);
      await contentArea.click({button: 'right'});

      const menuList = page.locator('[role="menu"]:visible');
      await menuList.waitFor({state: 'visible', timeout: 5_000});
      await menuList.getByText(/Create.*Asset.*class/i).first().click();

      const dialog = page.locator('.p-dialog');
      await dialog.waitFor({state: 'visible', timeout: 10_000});

      // categoryType — native <select> via createFieldSelectString
      const categoryTypeKey = AssetclassType[Number(row.categoryType)];
      expect(categoryTypeKey, `unmapped categoryType: ${row.categoryType}`).toBeTruthy();
      const categoryTypeSelect = dialog.locator('select#categoryType');
      await categoryTypeSelect.selectOption({value: categoryTypeKey});
      await categoryTypeSelect.dispatchEvent('change');

      // en / de — createFieldSuggestionInputString renders <p-autoComplete> whose inner <input> gets neither the
      // bound `[id]` nor any inputId (PrimeNG v21 does not propagate the outer id). The <label for="en"> still
      // exists on the form row, so locate by walking the row that contains that label.
      const enInput = dialog.locator('.dynamic-form-row:has(label[for="en"]) input').first();
      await enInput.click();
      await enInput.fill(row.subCategoryEN.trim());
      await enInput.dispatchEvent('input');
      await enInput.blur();

      const deInput = dialog.locator('.dynamic-form-row:has(label[for="de"]) input').first();
      await deInput.click();
      await deInput.fill(row.subCategoryDE.trim());
      await deInput.dispatchEvent('input');
      await deInput.blur();

      // specialInvestmentInstrument — native <select>
      const sInstrKey = SpecialInvestmentInstruments[Number(row.specialInvestmentInstrument)];
      expect(sInstrKey, `unmapped specialInvestmentInstrument: ${row.specialInvestmentInstrument}`).toBeTruthy();
      const sInstrSelect = dialog.locator('select#specialInvestmentInstrument');
      await sInstrSelect.selectOption({value: sInstrKey});
      await sInstrSelect.dispatchEvent('change');

      await dialog.locator('button[type="submit"]').click();
      await dialog.waitFor({state: 'hidden', timeout: 10_000});

      await expect(page.locator('td', {hasText: row.subCategoryEN.trim()}).first()).toBeVisible({timeout: 5_000});
    });
  }
});
