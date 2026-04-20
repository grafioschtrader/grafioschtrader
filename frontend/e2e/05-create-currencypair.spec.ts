import {test, expect} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import {loginAsCsvUser, parseCsvRow} from './helpers';

interface CurrencypairRow {
  fromCurrency: string;
  toCurrency: string;
  idConnectorHistory: string;
  idConnectorIntra: string;
  e2e: string;
}

const CSV_PATH = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/currencypair.csv');
const WATCHLIST_NAME = 'currencypair';
const LOGIN_NICKNAME = 'alledit';

function loadE2ERows(): CurrencypairRow[] {
  const csv = fs.readFileSync(CSV_PATH, 'utf-8');
  return csv.split(/\r?\n/)
    .filter(l => l.trim().length > 0)
    .map(line => {
      const [fromCurrency, toCurrency, idConnectorHistory, idConnectorIntra, e2e] = parseCsvRow(line);
      return {fromCurrency, toCurrency, idConnectorHistory, idConnectorIntra, e2e};
    })
    .filter(r => r.e2e === 'e');
}

test.describe.serial('Create currencypair watchlist and seed pairs (hugo.graf@grafiosch.com)', () => {

  test('creates "currencypair" watchlist if missing', async ({page}) => {
    await loginAsCsvUser(page, LOGIN_NICKNAME);

    // If the watchlist is already in the tree, we're done — the spec is idempotent. Use the treeitem's
    // aria-label for an exact match (the rendered `.p-tree-node-content` contains extra whitespace and
    // nested toggler/icon elements that break a `^...$` regex on innerText).
    const existing = page.getByRole('treeitem', {name: WATCHLIST_NAME, exact: true});
    if (await existing.count() > 0) {
      await expect(existing.first()).toBeVisible();
      return;
    }

    // The watchlist root node is labelled via 'WATCHLIST_CORRELATION_MATRIX' (en: "Watchlist - Correlation matrix",
    // de: "Watchlist - Korrelationsmatrix"). Right-click it to open the "Create Watchlist..." menu.
    const watchlistRoot = page.locator('.p-tree-node-content', {
      hasText: /Watchlist\s*-\s*(Correlation\s*matrix|Korrelationsmatrix)/i
    }).first();
    await watchlistRoot.waitFor({state: 'visible', timeout: 15_000});
    await watchlistRoot.click({button: 'right'});

    // Menu label is 'CREATE|WATCHLIST...' → "Create Watchlist..." (EN) / "Erstellen Watchlist..." (DE).
    const menu = page.locator('[role="menu"]:visible');
    await menu.waitFor({state: 'visible', timeout: 5_000});
    await menu.getByText(/(Create|Erstellen)\s*Watchlist/i).first().click();

    const dialog = page.locator('.p-dialog');
    await dialog.waitFor({state: 'visible', timeout: 10_000});
    const nameInput = dialog.locator('#name');
    await nameInput.click();
    await nameInput.fill(WATCHLIST_NAME);
    await nameInput.dispatchEvent('input');
    await nameInput.blur();
    await dialog.locator('button[type="submit"]').click();
    await dialog.waitFor({state: 'hidden', timeout: 10_000});

    await expect(page.getByRole('treeitem', {name: WATCHLIST_NAME, exact: true}).first())
      .toBeVisible({timeout: 10_000});
  });

  for (const row of loadE2ERows()) {
    test(`creates currency pair ${row.fromCurrency}/${row.toCurrency} if missing`, async ({page}) => {
      await loginAsCsvUser(page, LOGIN_NICKNAME);

      const watchlistNode = page.getByRole('treeitem', {name: WATCHLIST_NAME, exact: true}).first();
      await watchlistNode.waitFor({state: 'visible', timeout: 10_000});
      await watchlistNode.click();

      // Wait for the watchlist table to render so the "skip-if-present" check is meaningful.
      await page.waitForTimeout(1500);

      // Crypto currencies render with a "(Cryptocurrency)" suffix in the watchlist name cell. The
      // backend builds the dropdown value as `cc + "(" + i18n("cryptocurrency") + ")"` in
      // GlobalparametersService.getCurrencies (key="BTC"); the frontend renders the visible label
      // with a separating space, so the watchlist td reads "BTC (Cryptocurrency)/USD".
      // Match on the 3-letter codes with any text between to tolerate either form.
      const pairPattern = new RegExp(`\\b${row.fromCurrency}\\b.*\\b${row.toCurrency}\\b`);
      if (await page.locator('td').filter({hasText: pairPattern}).count() > 0) {
        return;
      }

      // Activate the content area (left-click first, then right-click). Mirrors the pattern used by
      // spec 01-create-portfolio.spec.ts — PrimeNG context menu requires the panel to be active first.
      const contentArea = page.locator('.data-container').first();
      await contentArea.waitFor({state: 'visible', timeout: 10_000});
      await contentArea.click();
      await page.waitForTimeout(300);
      await contentArea.click({button: 'right'});

      // Menu label 'CREATE_AND_ADD_CURRENCYPAIR...' → "Create and add currencypair..." (EN) /
      // "Hinzufügen neu erstelltes Währungspaar..." (DE).
      const menu = page.locator('[role="menu"]:visible');
      await menu.waitFor({state: 'visible', timeout: 5_000});
      await menu.getByText(/(Create\s*and\s*add\s*currencypair|Hinzuf.*W.*hrungspaar)/i).first().click();

      const dialog = page.locator('.p-dialog');
      await dialog.waitFor({state: 'visible', timeout: 10_000});

      // Currency selects populate asynchronously from GlobalparameterGTService.getCurrencies().
      const fromSelect = dialog.locator('select#fromCurrency');
      await fromSelect.waitFor({state: 'visible', timeout: 10_000});
      await expect(fromSelect.locator('option')).not.toHaveCount(0, {timeout: 10_000});
      await fromSelect.selectOption({value: row.fromCurrency});
      await fromSelect.dispatchEvent('change');

      const toSelect = dialog.locator('select#toCurrency');
      // valueChangedOnFromCurrency filters toCurrency options based on fromCurrency value.
      await expect(toSelect.locator(`option[value="${row.toCurrency}"]`)).toHaveCount(1, {timeout: 10_000});
      await toSelect.selectOption({value: row.toCurrency});
      await toSelect.dispatchEvent('change');

      // Connector selects populate asynchronously from CurrencypairService.getFeedConnectors().
      const histSelect = dialog.locator('select#idConnectorHistory');
      await expect(histSelect.locator(`option[value="${row.idConnectorHistory}"]`))
        .toHaveCount(1, {timeout: 10_000});
      await histSelect.selectOption({value: row.idConnectorHistory});
      await histSelect.dispatchEvent('change');

      const intraSelect = dialog.locator('select#idConnectorIntra');
      await expect(intraSelect.locator(`option[value="${row.idConnectorIntra}"]`))
        .toHaveCount(1, {timeout: 10_000});
      await intraSelect.selectOption({value: row.idConnectorIntra});
      await intraSelect.dispatchEvent('change');

      await dialog.locator('button[type="submit"]').click();
      await dialog.waitFor({state: 'hidden', timeout: 15_000});

      // Generous timeout: the new pair row appears only after the watchlist refetches its rows
      // and the connector returns first quotes. 10s is too tight for crypto/Yahoo (BTC/USD) under
      // load — bump to 30s.
      await expect(page.locator('td').filter({hasText: pairPattern}).first())
        .toBeVisible({timeout: 30_000});
    });
  }
});
