import {expect, Locator, Page, test} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import {loginAsFixtureUser, parseCsvRow} from './helpers';
import {AssetclassType} from '../src/app/shared/types/assetclass.type';
import {SpecialInvestmentInstruments} from '../src/app/shared/types/special.investment.instruments';

interface AcRow {
  categoryType: string;
  specialInvestmentInstrument: string;
  subCategoryDE: string;
  subCategoryEN: string;
  e2e: string;
}

interface ApiAssetclass {
  categoryType: string;
  specialInvestmentInstrument: string;
  subCategoryNLS: {map: Record<string, string>};
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

async function assetclassExists(page: Page, row: AcRow): Promise<boolean> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  const response = await page.request.get('/api/assetclass', {headers: {'x-auth-token': token!}});
  expect(response.ok(), `loading asset classes: ${await response.text()}`).toBeTruthy();

  const categoryType = AssetclassType[Number(row.categoryType)];
  const specialInvestmentInstrument = SpecialInvestmentInstruments[Number(row.specialInvestmentInstrument)];
  return (await response.json() as ApiAssetclass[]).some(assetclass =>
    assetclass.categoryType === categoryType
    && assetclass.specialInvestmentInstrument === specialInvestmentInstrument
    && assetclass.subCategoryNLS.map['en']?.trim() === row.subCategoryEN.trim()
    && assetclass.subCategoryNLS.map['de']?.trim() === row.subCategoryDE.trim());
}

async function fillSuggestionInput(input: Locator, value: string): Promise<void> {
  const overlay = input.locator('xpath=ancestor::p-autocomplete').locator('.p-autocomplete-overlay');
  await input.fill(value);
  await expect(input).toHaveValue(value);
  // Suggestions are debounced. Wait for this input's panel to open before closing it, or a late result can reopen
  // the panel over the next form row after Escape has already been sent.
  await expect(overlay).toBeVisible();
  await input.press('Escape');
  await expect(overlay).toBeHidden();
  await input.press('Tab');
}

test.describe.serial('Create asset classes (e2e=\'e\' rows from shared CSV)', () => {
  const rows = loadE2ERows();

  for (const row of rows) {
    // Dedupe label in case the CSV has multiple 'Stocks Spain ' rows (with/without trailing space)
    const label = `${row.subCategoryEN.trim()} / ${AssetclassType[Number(row.categoryType)]} / ${SpecialInvestmentInstruments[Number(row.specialInvestmentInstrument)]}`;

    test(`creates AssetClass: ${label}`, async ({page}) => {
      await loginAsFixtureUser(page, 'user');
      if (await assetclassExists(page, row)) {
        return;
      }

      // 'Asset class' lives under the collapsed 'Base Data - Data change request' root — expand it first.
      // 'user' has locale de-CH, so every text selector must match the German and the English label.
      const baseDataNode = page.locator('.p-tree-node-content', {hasText: /(Base Data|Basisdaten)/i}).first();
      await baseDataNode.waitFor({state: 'visible', timeout: 15_000});
      await baseDataNode.dblclick();

      const treeNode = page.locator('.p-tree-node-content',
        {hasText: /^\s*(Asset\s*class|Anlageklasse)\s*$/i}).first();
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
      await menuList.getByText(/(Create.*Asset.*class|Erstellen.*Anlageklasse)/i).first().click();

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
      await fillSuggestionInput(enInput, row.subCategoryEN.trim());

      const deInput = dialog.locator('.dynamic-form-row:has(label[for="de"]) input').first();
      await fillSuggestionInput(deInput, row.subCategoryDE.trim());

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
