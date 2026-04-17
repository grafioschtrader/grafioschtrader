import {test, expect} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import {login, parseCsvRow} from './helpers';
import {TradingPlatformFeePlan} from '../src/app/shared/types/trading.platform.fee.plan';

interface TppRow {
  transactionFeePlan: string;
  importPlatformName: string;
  platformPlanNameDE: string;
  platformPlanNameEN: string;
  e2e: string;
}

const CSV_PATH = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/generated/tradingplatformplan.csv');

function loadE2ERows(): TppRow[] {
  const csv = fs.readFileSync(CSV_PATH, 'utf-8');
  return csv.split(/\r?\n/)
    .filter(l => l.trim().length > 0)
    .map(line => {
      const [fee, platform, nameDE, nameEN, e2e] = parseCsvRow(line);
      return {
        transactionFeePlan: fee,
        importPlatformName: platform,
        platformPlanNameDE: nameDE,
        platformPlanNameEN: nameEN,
        e2e,
      };
    })
    // Skip rows with no import platform (\N marker from LEFT JOIN in nv.bat) — the dialog's
    // platform dropdown would have nothing to pick, and such rows are exercised by the backend
    // integration test instead.
    .filter(r => r.e2e === 'e' && r.importPlatformName && r.importPlatformName !== '\\N');
}

test.describe.serial('Create trading platform plans (e2e=\'e\' rows from shared CSV)', () => {
  const rows = loadE2ERows();

  for (const row of rows) {
    test(`creates TPP: ${row.platformPlanNameEN} (${row.importPlatformName})`, async ({page}) => {
      await login(page);

      // Navigate to the Trading Platform Plan view by clicking the tree node labelled "Trading platform plan"
      // (rendered from AppSettings.TRADING_PLATFORM_PLAN via i18n). The contributor lives in
      // src/app/shared/contributor/basedata-main-tree.contributor.ts.
      const tppTreeNode = page.locator('.p-tree-node-content', {hasText: /Trading\s*platform\s*plan/i}).first();
      await tppTreeNode.waitFor({state: 'visible', timeout: 15_000});
      await tppTreeNode.click();

      // Wait for the table to be visible and active
      const contentArea = page.locator('.data-container').first();
      await contentArea.waitFor({state: 'visible', timeout: 10_000});
      await contentArea.click();
      await page.waitForTimeout(300);
      await contentArea.click({button: 'right'});

      // The context menu "Create trading platform plan..." is rendered at body level
      const menuList = page.locator('[role="menu"]:visible');
      await menuList.waitFor({state: 'visible', timeout: 5_000});
      await menuList.getByText(/Create.*Trading.*platform.*plan/i).first().click();

      // Dialog opens
      const dialog = page.locator('.p-dialog');
      await dialog.waitFor({state: 'visible', timeout: 10_000});

      // Fill English and German platform plan names. Dispatch `input` so Angular reactive form
      // commits the value, and `blur` so the validator state transitions to "touched & valid".
      const enInput = dialog.locator('#en');
      await enInput.click();
      await enInput.fill(row.platformPlanNameEN);
      await enInput.dispatchEvent('input');
      await enInput.blur();

      const deInput = dialog.locator('#de');
      await deInput.click();
      await deInput.fill(row.platformPlanNameDE);
      await deInput.dispatchEvent('input');
      await deInput.blur();

      // Select transactionFeePlan — the form renders a native <select> whose option values are enum
      // names (see FormInputSelectComponent + SelectOptionsHelper.createHtmlOptionsFromEnum). Use the
      // TypeScript enum reverse lookup to map the CSV byte value to its enum name.
      const feePlanKey = TradingPlatformFeePlan[Number(row.transactionFeePlan)];
      expect(feePlanKey, `unmapped fee plan byte: ${row.transactionFeePlan}`).toBeTruthy();
      const feePlanSelect = dialog.locator('select#transactionFeePlan');
      await feePlanSelect.selectOption({value: feePlanKey});
      await feePlanSelect.dispatchEvent('change');

      // Select importTransactionPlatform by the visible platform name (also a native <select>).
      const platformSelect = dialog.locator('select#idTransactionImportPlatform');
      await platformSelect.selectOption({label: row.importPlatformName});
      await platformSelect.dispatchEvent('change');

      // Note: AuditHelper.editWithoutProposalInForm hides noteRequest/noteAcceptReject/rejectBtn for
      // new entities, so no justification textarea needs to be filled in the create flow.

      // Submit
      await dialog.locator('button[type="submit"]').click();
      await dialog.waitFor({state: 'hidden', timeout: 10_000});

      // Verify the new row appears in the table
      await expect(page.locator('td', {hasText: row.platformPlanNameEN}).first()).toBeVisible({timeout: 5_000});
    });
  }
});
