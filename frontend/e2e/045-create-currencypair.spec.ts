import { expect, Locator, Page, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { loginAsFixtureUser, parseCsvRow } from './helpers';

interface CurrencypairRow {
  fromCurrency: string;
  toCurrency: string;
  idConnectorHistory: string;
  idConnectorIntra: string;
  e2e: string;
}

const CSV_PATH = path.resolve(
  __dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/currencypair.csv'
);
const WATCHLIST_NAME = 'currencypair';
const LOGIN_NICKNAME = 'alledit';

function loadE2ERows(): CurrencypairRow[] {
  const csv = fs.readFileSync(CSV_PATH, 'utf-8');
  return csv
    .split(/\r?\n/)
    .filter((l) => l.trim().length > 0)
    .slice(1)
    .map((line) => {
      const [fromCurrency, toCurrency, idConnectorHistory, idConnectorIntra, e2e] = parseCsvRow(line);
      return { fromCurrency, toCurrency, idConnectorHistory, idConnectorIntra, e2e };
    })
    .filter((r) => r.e2e === 'e');
}

async function openWatchlistContextMenu(page: Page): Promise<Locator> {
  const contentArea = page.locator('.data-container').first();
  await contentArea.waitFor({ state: 'visible', timeout: 10_000 });
  await contentArea.click();
  await page.waitForTimeout(300);
  await contentArea.click({ button: 'right' });

  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({ state: 'visible', timeout: 5_000 });
  return menu;
}

async function addExistingCurrencypair(page: Page, row: CurrencypairRow, pairPattern: RegExp): Promise<void> {
  const menu = await openWatchlistContextMenu(page);
  await menu
    .getByText(/(Add\s+existing\s+instrument|Bestehendes\s+Instrument\s+hinzuf.gen)/i)
    .first()
    .click();

  const addDialog = page.locator('watchlist-add-instrument');
  await addDialog.locator('.p-dialog').waitFor({ state: 'visible', timeout: 10_000 });
  // initialize() resets the form after loading its options, so wait for its last populated select.
  await expect(addDialog.locator('select#idStockexchange option')).not.toHaveCount(0, { timeout: 15_000 });
  await addDialog.locator('select#assetclassType').selectOption({ value: 'CURRENCY_PAIR' });
  await addDialog.locator('select#currency').selectOption({ value: row.fromCurrency });
  await addDialog.locator('button[type="submit"]').click();

  const resultRow = addDialog.locator('add-instrument-table tbody tr').filter({ hasText: pairPattern });
  await expect(resultRow).toHaveCount(1, { timeout: 15_000 });
  await resultRow.locator('p-tablecheckbox').click();

  const addButton = addDialog.getByRole('button', { name: /^(Add|Hinzuf.gen)$/ });
  await expect(addButton).toBeEnabled({ timeout: 5_000 });
  await addButton.click();
  await expect(addButton).toBeDisabled({ timeout: 15_000 });
  await addDialog.getByRole('button', { name: /^(Close|Beenden)$/ }).click();
  await addDialog.locator('.p-dialog').waitFor({ state: 'hidden', timeout: 10_000 });
}

test.describe.serial('Seed currency pairs in the currencypair watchlist', () => {
  for (const row of loadE2ERows()) {
    test(`creates currency pair ${row.fromCurrency}/${row.toCurrency} if missing`, async ({ page }) => {
      await loginAsFixtureUser(page, LOGIN_NICKNAME);

      const watchlistNode = page.getByRole('treeitem', { name: WATCHLIST_NAME, exact: true }).first();
      await watchlistNode.waitFor({ state: 'visible', timeout: 10_000 });
      await watchlistNode.click();

      // Wait for the watchlist table to render so the "skip-if-present" check is meaningful.
      await page.waitForTimeout(1500);

      // Crypto currencies render with a "(Cryptocurrency)" suffix in the watchlist name cell. The
      // backend builds the dropdown value as `cc + "(" + i18n("cryptocurrency") + ")"` in
      // GlobalparametersService.getCurrencies (key="BTC"); the frontend renders the visible label
      // with a separating space, so the watchlist td reads "BTC (Cryptocurrency)/USD".
      // Match on the 3-letter codes with any text between to tolerate either form.
      const pairPattern = new RegExp(`\\b${row.fromCurrency}\\b.*\\b${row.toCurrency}\\b`);
      if ((await page.locator('td').filter({ hasText: pairPattern }).count()) > 0) {
        return;
      }

      // Activate the content area (left-click first, then right-click). Mirrors the pattern used by
      // spec 025-create-portfolio.spec.ts — Optimus UI context menu requires the panel to be active first.
      const menu = await openWatchlistContextMenu(page);

      // Menu label 'CREATE_AND_ADD_CURRENCYPAIR...' → "Create and add currencypair..." (EN) /
      // "Hinzufügen neu erstelltes Währungspaar..." (DE).
      await menu
        .getByText(/(Create\s*and\s*add\s*currencypair|Hinzuf.*W.*hrungspaar)/i)
        .first()
        .click();

      const dialog = page.locator('.p-dialog');
      await dialog.waitFor({ state: 'visible', timeout: 10_000 });

      // Currency selects populate asynchronously from GlobalparameterGTService.getCurrencies().
      const fromSelect = dialog.locator('select#fromCurrency');
      await fromSelect.waitFor({ state: 'visible', timeout: 10_000 });
      await expect(fromSelect.locator('option')).not.toHaveCount(0, { timeout: 10_000 });
      await fromSelect.selectOption({ value: row.fromCurrency });

      const toSelect = dialog.locator('select#toCurrency');
      // valueChangedOnFromCurrency filters toCurrency options based on fromCurrency value.
      const toOption = toSelect.locator(`option[value="${row.toCurrency}"]`);
      const canCreatePair = await toOption
        .waitFor({ state: 'attached', timeout: 3_000 })
        .then(() => true)
        .catch(() => false);
      if (!canCreatePair) {
        // The pair can have been created indirectly after an earlier CSV row. It is then correctly
        // excluded from this create dialog, but still needs adding to the current watchlist.
        await dialog.locator('.p-dialog-close-button').click();
        await dialog.waitFor({ state: 'hidden', timeout: 10_000 });
        await addExistingCurrencypair(page, row, pairPattern);
        await expect(page.locator('td').filter({ hasText: pairPattern }).first()).toBeVisible({ timeout: 30_000 });
        return;
      }
      await toSelect.selectOption({ value: row.toCurrency });

      // Connector selects populate asynchronously from CurrencypairService.getFeedConnectors().
      const histSelect = dialog.locator('select#idConnectorHistory');
      await expect(histSelect.locator(`option[value="${row.idConnectorHistory}"]`)).toHaveCount(1, { timeout: 10_000 });
      await histSelect.selectOption({ value: row.idConnectorHistory });

      const intraSelect = dialog.locator('select#idConnectorIntra');
      await expect(intraSelect.locator(`option[value="${row.idConnectorIntra}"]`)).toHaveCount(1, { timeout: 10_000 });
      await intraSelect.selectOption({ value: row.idConnectorIntra });

      const addToWatchlistResponse = page.waitForResponse(
        (response) =>
          response.request().method() === 'PUT' &&
          /\/api\/watchlist\/\d+\/addSecuritycurrency$/.test(new URL(response.url()).pathname),
        { timeout: 30_000 }
      );
      await dialog.locator('button[type="submit"]').click();
      await dialog.waitFor({ state: 'hidden', timeout: 15_000 });
      expect((await addToWatchlistResponse).ok()).toBeTruthy();

      // The new pair row appears only after the watchlist refetches its rows and the connector returns
      // first quotes. Keep this separate from awaiting the add-to-watchlist response above so a failed
      // write is reported as such instead of looking like a table-rendering timeout.
      await expect(page.locator('td').filter({ hasText: pairPattern }).first()).toBeVisible({ timeout: 30_000 });
    });
  }
});
