import {test, expect, Locator, Page} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import {loginAsCsvUser, parseCsvRow} from './helpers';
import {toShortDate} from './portfolio.helpers';

/**
 * Shape of one row in generated/securities.csv — column order mirrors
 * SecurityResourceTest.SecurityAggregator (21 domain columns) plus the e2e flag.
 */
interface SecurityRow {
  name: string;
  isin: string;
  tickerSymbol: string;
  currency: string;
  activeFromDate: string;
  activeToDate: string;
  distFrequency: string;
  denomination: string;
  leverageFactor: string;
  stockexchangeName: string;
  categoryType: string;
  subCategoryDE: string;
  specInvestmentInstrument: string;
  productLink: string;
  idTenantPrivate: string;
  formulaPrices: string;
  idConnectorHistory: string;
  urlHistoryExtend: string;
  idConnectorIntra: string;
  urlIntraExtend: string;
  note: string;
  e2e: string;
}

const CSV_PATH = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/generated/securities.csv');
const WATCHLIST_NAME = 'Spain';
const LOGIN_NICKNAME = 'alledit';

function loadE2ERows(): SecurityRow[] {
  if (!fs.existsSync(CSV_PATH)) {
    return [];
  }
  const csv = fs.readFileSync(CSV_PATH, 'utf-8');
  return csv.split(/\r?\n/)
    .filter(l => l.trim().length > 0)
    .map(line => {
      const c = parseCsvRow(line);
      return {
        name: c[0], isin: c[1], tickerSymbol: c[2], currency: c[3],
        activeFromDate: c[4], activeToDate: c[5],
        distFrequency: c[6], denomination: c[7], leverageFactor: c[8],
        stockexchangeName: c[9],
        categoryType: c[10], subCategoryDE: c[11], specInvestmentInstrument: c[12],
        productLink: c[13], idTenantPrivate: c[14], formulaPrices: c[15],
        idConnectorHistory: c[16], urlHistoryExtend: c[17],
        idConnectorIntra: c[18], urlIntraExtend: c[19],
        note: c[20], e2e: c[21],
      };
    })
    .filter(r => r.e2e === 'e');
}

// mysqldump INTO OUTFILE writes SQL NULL as \N. Treat those as empty strings when filling the form.
function nonNull(v: string): string {
  return v === '\\N' || v === undefined ? '' : v;
}

/**
 * Writes an ISO date into a p-datepicker of the dialog. Two things make a plain fill() useless here:
 * the picker ignores input events that were not preceded by a keydown (PrimeNG's isKeydown guard),
 * so the reactive form never sees the value, and it parses only the two-digit-year short format of
 * the logged-in user (de-CH 'dd.mm.y'). An ISO string therefore ends up as an unparsable text, the
 * control turns invalid ("the entered date cannot be interpreted") and Save stays disabled forever.
 * Same trap as in 047-create-derived-security and 010-import-template-group.
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
  if (!nonNull(isoDate)) {
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

test.describe.serial('Create Spain watchlist and seed Spanish securities', () => {

  test('creates "Spain" watchlist if missing', async ({page}) => {
    await loginAsCsvUser(page, LOGIN_NICKNAME);

    const existing = page.getByRole('treeitem', {name: WATCHLIST_NAME, exact: true});
    if (await existing.count() > 0) {
      await expect(existing.first()).toBeVisible();
      return;
    }

    const watchlistRoot = page.locator('.p-tree-node-content', {
      hasText: /Watchlist\s*-\s*(Correlation\s*matrix|Korrelationsmatrix)/i
    }).first();
    await watchlistRoot.waitFor({state: 'visible', timeout: 15_000});
    await watchlistRoot.click({button: 'right'});

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
    test(`adds Spanish security ${row.name} (${row.isin || row.tickerSymbol || 'no-id'})`, async ({page}) => {
      const creds = await loginAsCsvUser(page, LOGIN_NICKNAME);

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

      if (nonNull(row.isin)) {
        const isinInput = dialog.locator('#isin');
        await isinInput.click();
        await isinInput.fill(row.isin);
        await isinInput.dispatchEvent('input');
      }

      if (nonNull(row.tickerSymbol)) {
        const tickerInput = dialog.locator('#tickerSymbol');
        await tickerInput.click();
        await tickerInput.fill(row.tickerSymbol);
        await tickerInput.dispatchEvent('input');
      }

      const currencySelect = dialog.locator('select#currency');
      await expect(currencySelect.locator('option')).not.toHaveCount(0, {timeout: 10_000});
      await currencySelect.selectOption({value: row.currency});
      await currencySelect.dispatchEvent('change');

      // distributionFrequency is stored as byte in CSV → enum key in the UI select.
      // The select's option values are enum names (e.g. 'ANNUAL', 'QUARTERLY'). If CSV exports the
      // byte code, the test may need to translate; for now pass through and fall back on defaults.
      const distSelect = dialog.locator('select#distributionFrequency');
      if (await distSelect.count() > 0 && nonNull(row.distFrequency)) {
        const byIndex = Number(row.distFrequency);
        if (!Number.isNaN(byIndex)) {
          const opts = distSelect.locator('option');
          const optCount = await opts.count();
          if (optCount > byIndex) {
            await distSelect.selectOption({index: byIndex});
            await distSelect.dispatchEvent('change');
          }
        }
      }

      if (nonNull(row.denomination)) {
        const denomInput = dialog.locator('#denomination input, input#denomination').first();
        if (await denomInput.count() > 0) {
          await denomInput.fill(row.denomination);
          await denomInput.dispatchEvent('input');
        }
      }

      // Dates — PrimeNG p-calendar exposes an inner <input> that has to be typed key by key.
      await typeDate(dialog, 'activeFromDate', row.activeFromDate, creds.locale);
      await typeDate(dialog, 'activeToDate', row.activeToDate, creds.locale);

      // --- Connectors ---
      if (nonNull(row.idConnectorHistory)) {
        const histSelect = dialog.locator('select#idConnectorHistory');
        await expect(histSelect.locator(`option[value="${row.idConnectorHistory}"]`))
          .toHaveCount(1, {timeout: 10_000});
        await histSelect.selectOption({value: row.idConnectorHistory});
        await histSelect.dispatchEvent('change');
      }
      if (nonNull(row.urlHistoryExtend)) {
        const u = dialog.locator('#urlHistoryExtend');
        if (await u.count() > 0) {
          await u.fill(row.urlHistoryExtend);
          await u.dispatchEvent('input');
        }
      }
      if (nonNull(row.idConnectorIntra)) {
        const intraSelect = dialog.locator('select#idConnectorIntra');
        await expect(intraSelect.locator(`option[value="${row.idConnectorIntra}"]`))
          .toHaveCount(1, {timeout: 10_000});
        await intraSelect.selectOption({value: row.idConnectorIntra});
        await intraSelect.dispatchEvent('change');
      }
      if (nonNull(row.urlIntraExtend)) {
        const u = dialog.locator('#urlIntraExtend');
        if (await u.count() > 0) {
          await u.fill(row.urlIntraExtend);
          await u.dispatchEvent('input');
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
