import { expect, Locator, Page, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { loginAsFixtureUser } from './helpers';
import { expectToast } from './manage-client.helpers';
import { fillText, selectByValue } from './generic-connector.helpers';
import { selectDynamicFormOptionByText } from './dynamic-form.helpers';

/**
 * A linked instrument of a derived security. Instruments are referenced by name because the
 * production ids do not survive into grafioschtrader_t — the USD/CHF pair for example is filtered
 * out of V2__testdata.sql (API-key connector) and recreated with a new id by 045.
 *
 * searchName goes into the search dialog's name criterion, displayName is the exact text of the
 * result row's name cell. For a currency pair that is `Currencypair.getName()` → "USD/CHF".
 */
interface InstrumentRef {
  type: 'SECURITY' | 'CURRENCYPAIR';
  searchName: string;
  displayName: string;
}

/** One derived instrument of security-creations.json. */
interface DerivedInstrument {
  creationType: 'DERIVED';
  sourceIdSecuritycurrency: number;
  name: string;
  /** Base instrument, formula variable 'o'; becomes security.id_link_securitycurrency. */
  baseInstrument: InstrumentRef;
  /** null for a plain link without price calculation (the two Forex instruments). */
  formulaPrices: string | null;
  /** Keyed by formula variable p..s; each entry becomes a security_derived_link row. */
  additionalInstruments: { [varName: string]: InstrumentRef };
  currency: string;
  /** German sub asset class — the discriminator inside the composed asset class option label. */
  assetclassSubCategoryDE: string;
  stockexchangeName: string;
  activeFromDate: string;
  activeToDate: string;
  /** DistributionFrequency enum name; the select's option values are the enum names. */
  distributionFrequency: string;
  leverageFactor: number;
  e2e: string;
}

interface SecurityCreationGroup {
  loginNickname: string;
  watchlistName: string;
  securities: DerivedInstrument[];
}

interface SecurityCreationFixture {
  groups: SecurityCreationGroup[];
}

interface DerivedScenario {
  loginNickname: string;
  watchlistName: string;
  security: DerivedInstrument;
}

const FIXTURE_PATH = path.resolve(
  __dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/security-creations.json'
);

function loadFixture(): DerivedScenario[] {
  if (!fs.existsSync(FIXTURE_PATH)) {
    return [];
  }
  const fixture = JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf-8')) as SecurityCreationFixture;
  return fixture.groups.flatMap((group) =>
    group.securities
      .filter((security) => security.creationType === 'DERIVED' && security.e2e === 'e')
      .map((security) => ({
        loginNickname: group.loginNickname,
        watchlistName: group.watchlistName,
        security
      }))
  );
}

const SCENARIOS = loadFixture();

// The login user is de-CH, so every label matcher has to accept the German and the English text.
const RX = {
  // CREATE_AND_ADD_SECURITY_DERIVED. Deliberately different words than CREATE_AND_ADD_SECURITY
  // ("Create and add security" / "Hinzufügen neues Wertpapier"), which sits right above it.
  createDerivedItem: /(Add\s*new\s*derived\s*security|Hinzuf.*abgeleitetes\s*Instrument)/i,
  assignSelected: /(Assign\s*selected|Selektierte\s*zuweisen)/i,
  recordSaved: /(was saved|wurde gespeichert)/i
};

/**
 * Converts an ISO date to the two-digit-year short format the calendar parses for de-CH
 * (formConfig.dateFormat = 'dd.mm.y'), e.g. 2000-02-03 → 03.02.00. Optimus UI's shortYearCutoff maps
 * a two-digit year up to (current year + 10) into the 2000s, which covers 00 and 36.
 */
function toDeChShortDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-');
  return `${day}.${month}.${year.slice(2)}`;
}

/**
 * Types a date into a p-datepicker. Real key events are mandatory: Optimus UI's onUserInput ignores
 * input events that were not preceded by a keydown, so fill() never reaches the model and blur
 * wipes the text again. Existing content is cleared first because picking a security base
 * instrument copies the base's dates into the form.
 */
async function typeDate(scope: Locator, fieldId: string, isoDate: string): Promise<void> {
  const shortDate = toDeChShortDate(isoDate);
  const input = scope.locator(`#${fieldId} input, input#${fieldId}`).first();
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(shortDate, { delay: 20 });
  await input.blur();
  await expect(input, `${fieldId} did not keep the typed date`).toHaveValue(shortDate);
}

/**
 * Types into a p-inputNumber when the field is on screen. Like the date picker it builds its model
 * from the key handlers, so a fill() would leave the reactive form untouched; de-CH uses '.' as
 * decimal separator, so the plain JavaScript representation is already in the expected format.
 *
 * The field may legitimately be hidden: SecurityEditSupport.hideShowSomeFields() shows
 * leverageFactor only for the asset classes ETF and ISSUER_RISK_PRODUCT. For every other instrument
 * the form default applies, and the assertion on the save response verifies it.
 */
async function typeNumberIfVisible(scope: Locator, fieldId: string, value: number): Promise<void> {
  const input = scope.locator(`#${fieldId} input, input#${fieldId}`).first();
  if (!(await input.isVisible())) {
    return;
  }
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(String(value), { delay: 20 });
  await input.press('Tab');
}

/**
 * Walks the search result pages until the wanted row is rendered. A currency search such as "USD"
 * matches roughly twenty pairs while the result table paginates at ten rows.
 */
async function rowAcrossPages(page: Page, table: Locator, displayName: string): Promise<Locator> {
  const row = table
    .locator('tr')
    .filter({ has: page.locator(`td:text-is("${displayName}")`) })
    .first();
  const nextPage = table.locator('.p-paginator-next');

  for (let attempt = 0; attempt < 6; attempt++) {
    try {
      await expect(row).toBeVisible({ timeout: attempt === 0 ? 15_000 : 3_000 });
      return row;
    } catch {
      // Not on this page — fall through and try the next one.
    }
    if ((await nextPage.count()) === 0 || (await nextPage.isDisabled())) {
      break;
    }
    await nextPage.click();
    await page.waitForTimeout(300);
  }
  throw new Error(`search result contains no row "${displayName}"`);
}

/**
 * Assigns an instrument to one of the derived dialog's input-button fields (baseProductName or
 * additionalInstrumentName_<var>). The button opens SecuritycurrencySearchAndSetComponent as a
 * separate DynamicDialog stacked on top of the derived dialog, so it must never be addressed
 * through a bare '.p-dialog'.
 */
async function pickInstrument(page: Page, dlg: Locator, fieldId: string, ref: InstrumentRef): Promise<void> {
  const openButton = dlg.locator(`p-inputgroup:has(input#${fieldId}) button`).first();
  const search = page
    .locator('.p-dialog')
    .filter({ has: page.locator('securitycurrency-search-and-set') })
    .first();

  // The first click right after the derived dialog opened only moves the focus — the button
  // receives it (it becomes document.activeElement) but the (click) handler does not run and no
  // DynamicDialog is created. Verified with a DOM probe: click #1 leaves the body untouched, click
  // #2 appends the P-DYNAMICDIALOG. So click, verify, and repeat once. The count check before each
  // click makes sure a successful open is never followed by a second one.
  for (let attempt = 0; attempt < 3 && (await search.count()) === 0; attempt++) {
    await openButton.click();
    try {
      await search.waitFor({ state: 'visible', timeout: 3_000 });
    } catch {
      // Swallowed click — try again.
    }
  }
  await search.waitFor({ state: 'visible', timeout: 10_000 });

  // Same reset race as the outer dialog: initialize() assigns the dropdown options and then calls
  // setDefaultValuesAndEnableSubmit(). Probe idStockexchange, because that select is hidden again
  // the moment assetclassType becomes CURRENCY_PAIR.
  await expect(search.locator('select#idStockexchange').locator('option')).not.toHaveCount(0, { timeout: 15_000 });

  if (ref.type === 'CURRENCYPAIR') {
    // Restricts the security half of the search to currency pairs (of which none can be returned,
    // derived instruments are excluded) and keeps the result list to pairs only. The currency
    // criterion stays empty on purpose: CurrencyPairSearchBuilder uses currency INSTEAD of name.
    await selectByValue(search, 'assetclassType', 'CURRENCY_PAIR');
  }

  await fillText(search, 'input#name', ref.searchName);
  await search.locator('button[type="submit"]').click();

  const table = search.locator('securitycurrency-search-and-set-table');
  const row = await rowAcrossPages(page, table, ref.displayName);
  await row.click();

  const assign = search.getByRole('button', { name: RX.assignSelected }).first();
  await expect(assign).toBeEnabled({ timeout: 5_000 });
  await assign.click();
  await search.waitFor({ state: 'hidden', timeout: 10_000 });

  await expect(dlg.locator(`input#${fieldId}`)).toHaveValue(ref.displayName, { timeout: 10_000 });
}

/** Opens the watchlist and waits for its table to be rendered. */
async function openWatchlist(page: Page, watchlistName: string): Promise<void> {
  const node = page.getByRole('treeitem', { name: watchlistName, exact: true }).first();
  await node.waitFor({ state: 'visible', timeout: 15_000 });
  await node.click();
  await page.locator('.data-container').first().waitFor({ state: 'visible', timeout: 15_000 });
  // Let the watchlist table render before the skip-if-present check.
  await page.waitForTimeout(1500);
}

test.describe.serial('derived instruments — watchlist and calculated securities', () => {
  // The search-and-assign dialog stacks a full criteria form, a ten row result table, the paginator
  // and the assign button. At the default 720px height it overflows the viewport and the assign
  // button cannot be scrolled into view, because a centered p-dialog does not scroll the body.
  test.use({ viewport: { width: 1600, height: 1400 } });

  for (const scenario of SCENARIOS) {
    const row = scenario.security;
    test(`adds derived instrument ${row.name}`, async ({ page }) => {
      await loginAsFixtureUser(page, scenario.loginNickname);
      await openWatchlist(page, scenario.watchlistName);

      // Derived instruments are globally shared and security has no unique name index, so a second
      // run would silently create duplicates — skip what is already in the watchlist.
      if ((await page.locator('td', { hasText: row.name }).count()) > 0) {
        return;
      }

      // Activate the panel, then open its context menu.
      const contentArea = page.locator('.data-container').first();
      await contentArea.click();
      await page.waitForTimeout(300);
      await contentArea.click({ button: 'right' });

      const menu = page.locator('[role="menu"]:visible');
      await menu.waitFor({ state: 'visible', timeout: 5_000 });
      await menu.getByText(RX.createDerivedItem).first().click();

      // The derived dialog is a plain p-dialog rendered inside its component, whereas the search
      // dialog opened later is a DynamicDialog appended to the body — scoping through the component
      // tag keeps the two apart without relying on header text.
      const dlg = page.locator('security-derived-edit .p-dialog').first();
      await dlg.waitFor({ state: 'visible', timeout: 10_000 });

      // NOTHING may be entered before the dialog's master data has arrived. initialize() loads
      // stock exchanges, currencies and asset classes in one combineLatest whose subscribe assigns
      // the dropdown options and then calls setDefaultValuesAndEnableSubmit() — a form reset that
      // silently wipes anything typed earlier.
      await expect(dlg.locator('select#assetClass').locator('option')).not.toHaveCount(0, { timeout: 15_000 });

      // --- Derived data: base instrument, formula, additional instruments ---
      await pickInstrument(page, dlg, 'baseProductName', row.baseInstrument);

      if (row.formulaPrices) {
        // Typing the formula spawns one additionalInstrumentName_<var> input-button per variable
        // in 'opqrs' except 'o'. The field config array is rebuilt, but createControl() reuses the
        // existing form controls, so values entered so far survive.
        await fillText(dlg, 'textarea#formulaPrices', row.formulaPrices);
      }
      for (const [varName, ref] of Object.entries(row.additionalInstruments)) {
        await pickInstrument(page, dlg, `additionalInstrumentName_${varName}`, ref);
      }

      // --- Base data, deliberately after the instrument pickers ---
      // Picking a security base transfers the base's name, currency, asset class, stock exchange
      // and both dates into the form; picking a currency pair sets currency to its FROM currency.
      // Everything therefore has to be set explicitly afterwards.
      await fillText(dlg, 'input#name', row.name);
      // Asset class option labels are composed as "{categoryType} / {subCategoryNLS[de]} /
      // {specialInvestmentInstrument}", so the German sub category is the discriminator.
      await selectDynamicFormOptionByText(page, dlg, 'assetClass', row.assetclassSubCategoryDE);
      await selectDynamicFormOptionByText(page, dlg, 'stockexchange', row.stockexchangeName);
      await selectByValue(dlg, 'currency', row.currency);
      await selectByValue(dlg, 'distributionFrequency', row.distributionFrequency);
      await typeNumberIfVisible(dlg, 'leverageFactor', row.leverageFactor);
      await typeDate(dlg, 'activeFromDate', row.activeFromDate);
      await typeDate(dlg, 'activeToDate', row.activeToDate);

      // --- Save and verify what was really persisted ---
      const responsePromise = page.waitForResponse(
        (response) =>
          /\/api\/security$/.test(new URL(response.url()).pathname) && response.request().method() === 'POST',
        { timeout: 20_000 }
      );
      // The derived dialog has exactly one submit button (AuditHelper.SUBMIT_FIELD_BUTTON); unlike
      // the plain security dialog there are no splits/trading-period sub-forms.
      await dlg.locator('button[type="submit"]').first().click();

      const response = await responsePromise;
      expect(response.ok(), `POST /api/security returned ${response.status()}: ${await response.text()}`).toBe(true);
      const saved = await response.json();

      expect(saved.name).toBe(row.name);
      expect(saved.currency).toBe(row.currency);
      expect(saved.leverageFactor).toBeCloseTo(row.leverageFactor, 1);
      // The two-digit-year calendar inputs are the most fragile fields of the form — verify that the
      // typed dates really survived the round trip instead of falling back to the form defaults.
      expect(saved.activeFromDate).toBe(row.activeFromDate);
      expect(saved.activeToDate).toBe(row.activeToDate);
      expect(saved.formulaPrices ?? null).toBe(row.formulaPrices);
      // The base instrument is the decisive part of a derived security — without it the instrument
      // would be an ordinary security without a price source.
      expect(typeof saved.idLinkSecuritycurrency, 'base instrument was not linked').toBe('number');

      const expectedVars = Object.keys(row.additionalInstruments);
      const savedLinks = (saved.securityDerivedLinks ?? []) as { varName: string; idLinkSecuritycurrency: number }[];
      expect(savedLinks.map((l) => l.varName).sort()).toEqual(expectedVars.sort());
      for (const link of savedLinks) {
        expect(typeof link.idLinkSecuritycurrency).toBe('number');
        expect(link.idLinkSecuritycurrency).not.toBe(saved.idLinkSecuritycurrency);
      }

      await expectToast(page, RX.recordSaved);
      await expect(page.locator('td', { hasText: row.name }).first()).toBeVisible({ timeout: 15_000 });
    });
  }
});
