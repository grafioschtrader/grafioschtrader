import {test, expect, Locator, Page} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import {loginAsFixtureUser} from './helpers';
import {toShortDate} from './portfolio.helpers';

/**
 * Create-time business values from generated/securities.json. References to the stock exchange and asset class use
 * natural keys so the same fixture works after the backend suite has recreated those records with different IDs.
 */
interface SecurityRow {
  name: string;
  isin: string | null;
  tickerSymbol: string | null;
  currency: string;
  activeFromDate: string;
  activeToDate: string;
  distributionFrequency: string;
  denomination: number | null;
  leverageFactor: number;
  stockexchangeName: string;
  categoryType: string;
  subCategoryDE: string;
  specialInvestmentInstrument: string;
  stockexchangeLink: string | null;
  productLink: string | null;
  formulaPrices: string | null;
  idConnectorHistory: string | null;
  urlHistoryExtend: string | null;
  idConnectorIntra: string | null;
  urlIntraExtend: string | null;
  idConnectorDividend: string | null;
  urlDividendExtend: string | null;
  dividendCurrency: string | null;
  idConnectorSplit: string | null;
  urlSplitExtend: string | null;
  note: string | null;
  e2e: string;
}

const JSON_PATH = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/generated/securities.json');
const WATCHLIST_NAME = 'Spain';
const LOGIN_NICKNAME = 'alledit';

function loadE2ERows(): SecurityRow[] {
  if (!fs.existsSync(JSON_PATH)) {
    console.warn(`Fixture ${JSON_PATH} not found - skipping security e2e (run nv.bat to generate it).`);
    return [];
  }
  return (JSON.parse(fs.readFileSync(JSON_PATH, 'utf-8')) as SecurityRow[]).filter(row => row.e2e === 'e');
}

function hasText(value: string | null | undefined): value is string {
  return value !== null && value !== undefined && value.length > 0;
}

/**
 * Writes an ISO date into a p-datepicker of the dialog. Two things make a plain fill() useless here:
 * the picker ignores input events that were not preceded by a keydown (PrimeNG's isKeydown guard),
 * so the reactive form never sees the value, and it parses only the two-digit-year short format of
 * the logged-in user (de-CH 'dd.mm.y'). An ISO string therefore ends up as an unparsable text, the
 * control turns invalid ("the entered date cannot be interpreted") and Save stays disabled forever.
 * Same trap as in 065-create-derived-security and 010-import-template-group.
 *
 * The existing content is cleared first — the dialog prefills both date fields with defaults.
 */
/**
 * Locates the watchlist cell that holds exactly this security name. A substring match is wrong here:
 * 'IBEX 35' is contained in 'Lyxor ETF IBEX 35 (DR) (EUR)', so the index would silently count as
 * already present and never be created, while the closing assertion would confirm the ETF's row.
 */
function nameCell(page: Page, name: string): Locator {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return page.locator('td', {hasText: new RegExp(`^\\s*${escaped}\\s*$`)});
}

async function typeDate(scope: Locator, fieldId: string, isoDate: string, locale: string): Promise<void> {
  if (!hasText(isoDate)) {
    return;
  }
  const input = scope.locator(`#${fieldId} input, input#${fieldId}`).first();
  if (await input.count() === 0) {
    return;
  }
  const shortDate = toShortDate(isoDate, locale);
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(shortDate, {delay: 20});
  await input.blur();
  await expect(input, `${fieldId} did not keep the typed date`).toHaveValue(shortDate);
}

/**
 * Types into a visible p-inputNumber. leverageFactor remains in the DOM while hidden for asset classes that use the
 * default factor, so checking locator count alone would wait until the complete test timeout.
 */
async function typeNumberIfVisible(scope: Locator, fieldId: string, value: number): Promise<void> {
  const input = scope.locator(`#${fieldId} input, input#${fieldId}`).first();
  if (!await input.isVisible()) {
    return;
  }
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(String(value), {delay: 20});
  await input.press('Tab');
}

test.describe.serial('Seed Spanish securities in the Spain watchlist', () => {
  for (const row of loadE2ERows()) {
    test(`adds Spanish security ${row.name} (${row.isin || row.tickerSymbol || 'no-id'})`, async ({page}) => {
      const creds = await loginAsFixtureUser(page, LOGIN_NICKNAME);

      const watchlistNode = page.getByRole('treeitem', {name: WATCHLIST_NAME, exact: true}).first();
      await watchlistNode.waitFor({state: 'visible', timeout: 10_000});
      await watchlistNode.click();

      // Let the watchlist table render before the skip-if-present check.
      await page.waitForTimeout(1500);

      // Skip if this security is already in the table (idempotent).
      if (await nameCell(page, row.name).count() > 0) {
        return;
      }

      // Activate panel, then open context menu.
      const contentArea = page.locator('.data-container').first();
      await contentArea.waitFor({state: 'visible', timeout: 10_000});
      await contentArea.click();
      await page.waitForTimeout(300);
      await contentArea.click({button: 'right'});

      // Menu label key: CREATE_AND_ADD_SECURITY → "Create and add security" (EN)
      // / "Hinzufügen neues Wertpapier" (DE). Exclude the CREATE_AND_ADD_SECURITY_DERIVED
      // entry which renders as "Add new derived security".
      const menu = page.locator('[role="menu"]:visible');
      await menu.waitFor({state: 'visible', timeout: 5_000});
      await menu.getByText(/^(Create\s*and\s*add\s*security|Hinzuf.*neues\s*Wertpapier)\b/i)
        .first().click();

      const dialog = page.locator('.p-dialog');
      await dialog.waitFor({state: 'visible', timeout: 10_000});

      // NOTHING may be typed before the dialog's master data has arrived. SecurityEditComponent
      // loads stock exchanges, currencies, asset classes and connectors in one combineLatest and
      // its subscribe assigns the dropdown options and then calls setDefaultValuesAndEnableSubmit()
      // — which is a form.reset(). Anything entered earlier is silently wiped, leaving 'name'
      // empty and Save disabled. The options are assigned in the same callback right before the
      // reset, so their presence proves the reset already happened.
      const assetClassSelect = dialog.locator('select#assetClass').first();
      await assetClassSelect.waitFor({state: 'visible', timeout: 10_000});
      await expect(assetClassSelect.locator('option')).not.toHaveCount(0, {timeout: 15_000});

      // --- Base data ---
      const nameInput = dialog.locator('#name');
      await nameInput.click();
      await nameInput.fill(row.name);
      await nameInput.dispatchEvent('input');

      // Asset class select — option labels are composed as "{categoryType} / {subCategoryDE} /
      // {specialInvestmentInstrument}" by BusinessSelectOptionsHelper.translateAssetclass, so we
      // can't match on subCategoryDE alone. Pick the first option whose visible text contains it.
      const assetClassOptionValue = await assetClassSelect.locator('option')
        .filter({hasText: row.subCategoryDE})
        .first()
        .getAttribute('value');
      await assetClassSelect.selectOption(assetClassOptionValue);
      await assetClassSelect.dispatchEvent('change');

      // Stockexchange select — options are value=idStockexchange, label=name. Match by partial text.
      const stockexchangeSelect = dialog.locator('select#stockexchange').first();
      await expect(stockexchangeSelect.locator('option')).not.toHaveCount(0, {timeout: 10_000});
      const stockexchangeOptionValue = await stockexchangeSelect.locator('option')
        .filter({hasText: row.stockexchangeName})
        .first()
        .getAttribute('value');
      await stockexchangeSelect.selectOption(stockexchangeOptionValue);
      await stockexchangeSelect.dispatchEvent('change');

      if (hasText(row.isin)) {
        const isinInput = dialog.locator('#isin');
        await isinInput.click();
        await isinInput.fill(row.isin);
        await isinInput.dispatchEvent('input');
      }

      if (hasText(row.tickerSymbol)) {
        const tickerInput = dialog.locator('#tickerSymbol');
        await tickerInput.click();
        await tickerInput.fill(row.tickerSymbol);
        await tickerInput.dispatchEvent('input');
      }

      const currencySelect = dialog.locator('select#currency');
      await expect(currencySelect.locator('option')).not.toHaveCount(0, {timeout: 10_000});
      await currencySelect.selectOption({value: row.currency});
      await currencySelect.dispatchEvent('change');

      // JSON carries the REST enum name, which is also the select option value.
      const distSelect = dialog.locator('select#distributionFrequency');
      if (await distSelect.count() > 0 && hasText(row.distributionFrequency)) {
        await distSelect.selectOption({value: row.distributionFrequency});
        await distSelect.dispatchEvent('change');
      }

      if (row.denomination !== null) {
        const denomInput = dialog.locator('#denomination input, input#denomination').first();
        if (await denomInput.count() > 0) {
          await denomInput.fill(String(row.denomination));
          await denomInput.dispatchEvent('input');
        }
      }

      await typeNumberIfVisible(dialog, 'leverageFactor', row.leverageFactor);

      for (const [field, value] of [
        ['stockexchangeLink', row.stockexchangeLink], ['productLink', row.productLink], ['note', row.note]
      ] as const) {
        if (hasText(value)) {
          const input = dialog.locator(`#${field}`).first();
          if (await input.count() > 0) {
            await input.fill(value);
            await input.dispatchEvent('input');
          }
        }
      }

      // Dates — PrimeNG p-calendar exposes an inner <input> that has to be typed key by key.
      await typeDate(dialog, 'activeFromDate', row.activeFromDate, creds.locale);
      await typeDate(dialog, 'activeToDate', row.activeToDate, creds.locale);

      // --- Connectors ---
      if (hasText(row.idConnectorHistory)) {
        const histSelect = dialog.locator('select#idConnectorHistory');
        await expect(histSelect.locator(`option[value="${row.idConnectorHistory}"]`))
          .toHaveCount(1, {timeout: 10_000});
        await histSelect.selectOption({value: row.idConnectorHistory});
        await histSelect.dispatchEvent('change');
      }
      if (hasText(row.urlHistoryExtend)) {
        const u = dialog.locator('#urlHistoryExtend');
        if (await u.count() > 0) {
          await u.fill(row.urlHistoryExtend);
          await u.dispatchEvent('input');
        }
      }
      if (hasText(row.idConnectorIntra)) {
        const intraSelect = dialog.locator('select#idConnectorIntra');
        await expect(intraSelect.locator(`option[value="${row.idConnectorIntra}"]`))
          .toHaveCount(1, {timeout: 10_000});
        await intraSelect.selectOption({value: row.idConnectorIntra});
        await intraSelect.dispatchEvent('change');
      }
      if (hasText(row.urlIntraExtend)) {
        const u = dialog.locator('#urlIntraExtend');
        if (await u.count() > 0) {
          await u.fill(row.urlIntraExtend);
          await u.dispatchEvent('input');
        }
      }

      if (hasText(row.idConnectorDividend)) {
        const dividendSelect = dialog.locator('select#idConnectorDividend');
        if (await dividendSelect.count() > 0) {
          await expect(dividendSelect.locator(`option[value="${row.idConnectorDividend}"]`))
            .toHaveCount(1, {timeout: 10_000});
          await dividendSelect.selectOption({value: row.idConnectorDividend});
          await dividendSelect.dispatchEvent('change');
        }
      }
      if (hasText(row.urlDividendExtend)) {
        const input = dialog.locator('#urlDividendExtend').first();
        if (await input.count() > 0) {
          await input.fill(row.urlDividendExtend);
          await input.dispatchEvent('input');
        }
      }
      if (hasText(row.dividendCurrency)) {
        const currency = dialog.locator('select#dividendCurrency').first();
        if (await currency.count() > 0) {
          await currency.selectOption({value: row.dividendCurrency});
          await currency.dispatchEvent('change');
        }
      }
      if (hasText(row.idConnectorSplit)) {
        const splitSelect = dialog.locator('select#idConnectorSplit');
        if (await splitSelect.count() > 0) {
          await expect(splitSelect.locator(`option[value="${row.idConnectorSplit}"]`))
            .toHaveCount(1, {timeout: 10_000});
          await splitSelect.selectOption({value: row.idConnectorSplit});
          await splitSelect.dispatchEvent('change');
        }
      }
      if (hasText(row.urlSplitExtend)) {
        const input = dialog.locator('#urlSplitExtend').first();
        if (await input.count() > 0) {
          await input.fill(row.urlSplitExtend);
          await input.dispatchEvent('input');
        }
      }

      // The security dialog contains three submit buttons (main Save, Security splits "Apply",
      // Trading periods "Apply"). Target the top-level Save by role+name to avoid strict-mode
      // violations.
      await dialog.getByRole('button', {name: /^(Save|Speichern)$/}).first().click();
      await dialog.waitFor({state: 'hidden', timeout: 15_000});

      await expect(nameCell(page, row.name).first())
        .toBeVisible({timeout: 10_000});
    });
  }
});
