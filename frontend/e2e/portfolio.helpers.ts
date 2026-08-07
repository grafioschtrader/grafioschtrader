import {expect, Locator, Page} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

/**
 * Shared fixture loading and UI helpers for the portfolio specs 015-create-portfolio (create) and
 * 888-delete-portfolio (teardown). Both drive the same JSON fixture, so the portfolio, cash account
 * and securities account names exist exactly once.
 *
 * The fixture spans two users with different locales — 'e2euser' runs an English UI (en-US) and
 * 'alledit' a German one (de-CH) — therefore every text selector here matches both languages (same
 * convention as manage-client.helpers.ts / 050-correlation-matrix.spec.ts).
 */
const FIXTURE_PATH = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/portfolios.json');

export interface CashAccountData {
  name: string;
  currency: string;
  /** Integration fixtures may assign the cash account to a securities account by name. */
  connectSecurityAccount?: string;
}

export interface TradingPeriodData {
  /** SpecialInvestmentInstruments enum name, e.g. 'CFD' — the option value of the first select. */
  specInvestInstrument: string;
  /** AssetclassType enum name, e.g. 'EQUITIES'. null selects the empty option = all asset classes. */
  categoryType: string | null;
  /** ISO date (yyyy-MM-dd) for the "Date from" column. */
  dateFrom: string;
}

export interface SecurityAccountData {
  name: string;
  /**
   * Case-insensitive substring of the trading platform plan label to pick. The label is language
   * dependent, hence the substring match. null takes the first non-empty option.
   */
  tradingPlatformPlan: string | null;
  lowestTransactionCost: number;
  /** Rows added on top of the two the dialog pre-fills (Equities/Direct investment, Equities/ETF). */
  tradingPeriods: TradingPeriodData[];
}

export type CashTransactionType = 'DEPOSIT' | 'INTEREST_CASHACCOUNT' | 'WITHDRAWAL' | 'FEE';

export interface ValuationExchangeRateData {
  /** Tenant main currency into which the cash-account amount is valued. */
  toCurrency: string;
  /** Exact-date close copied from the source database, used only when that quote is missing. */
  close: number;
}

export interface SingleCashTransactionData {
  kind: 'single';
  transactionType: CashTransactionType;
  /** ISO calendar date; the e2e test uses a deterministic local noon time. */
  date: string;
  cashAccount: string;
  /** Value entered in the form. The backend negates positive withdrawals and fees before persisting them. */
  amount: number;
  securityAccount?: string;
  taxCost?: number;
  note?: string;
  /** Required for a foreign-currency standalone booking when the test database has no historical rate. */
  valuationExchangeRate?: ValuationExchangeRateData;
}

export interface CashTransferData {
  kind: 'transfer';
  /** ISO calendar date; the e2e test uses a deterministic local noon time. */
  date: string;
  debitCashAccount: string;
  creditPortfolio: string;
  creditCashAccount: string;
  creditAmount: number;
  /** Omitted for a same-currency transfer, whose disabled form field is set to one. */
  exchangeRate?: number;
  /** Rounded amount displayed and persisted on the debit account. */
  expectedDebitAmount: number;
  transactionCost?: number;
  note?: string;
}

export type BankAccountTransactionData = SingleCashTransactionData | CashTransferData;

export interface StandingOrderScheduleData {
  repeatUnit: 'DAYS' | 'MONTHS' | 'YEARS';
  repeatInterval: number;
  periodDayPosition: 'SPECIFIC_DAY' | 'FIRST_DAY' | 'LAST_DAY';
  dayOfExecution?: number;
  monthOfExecution?: number;
  weekendAdjust: 'BEFORE' | 'AFTER';
  validFrom: string;
  validTo: string;
  note?: string;
  transactionCost?: number;
}

export interface CashStandingOrderData extends StandingOrderScheduleData {
  kind: 'cash';
  transactionType: 'WITHDRAWAL' | 'DEPOSIT' | 'INTEREST_CASHACCOUNT' | 'FEE';
  cashAccount: string;
  cashaccountAmount: number;
  amountCurrency?: string;
  cashaccountAmountFormula?: string;
}

export interface SecurityStandingOrderData extends StandingOrderScheduleData {
  kind: 'security';
  transactionType: 'ACCUMULATE' | 'REDUCE';
  securityAccount: string;
  cashAccount: string;
  securityName: string;
  securityIsin: string;
  units?: number;
  investAmount?: number;
  amountIncludesCosts: boolean;
  fractionalUnits: boolean;
  taxCost?: number;
  taxCostFormula?: string;
  transactionCostFormula?: string;
}

export type StandingOrderData = CashStandingOrderData | SecurityStandingOrderData;

export interface PortfolioFixture {
  /** users.csv nickname the portfolio belongs to. */
  loginNickname: string;
  name: string;
  currency: string;
  /** true = 888 tears this portfolio down completely; false/absent = it stays in the database. */
  delete?: boolean;
  cashAccounts: CashAccountData[];
  securityAccounts: SecurityAccountData[];
  transactions?: BankAccountTransactionData[];
  standingOrders?: StandingOrderData[];
  e2e: string;
}

interface PortfolioFixtureFile {
  portfolios: PortfolioFixture[];
}

/**
 * Loads the e2e-tagged portfolios from the hand-authored fixture; empty when the file is missing so
 * the specs degrade into zero tests instead of a collection error.
 */
export function loadPortfolios(): PortfolioFixture[] {
  if (!fs.existsSync(FIXTURE_PATH)) {
    console.warn(`Fixture ${FIXTURE_PATH} not found - skipping the portfolio e2e specs.`);
    return [];
  }
  const fixture = JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf-8')) as PortfolioFixtureFile;
  return fixture.portfolios.filter(p => p.e2e === 'e');
}

/**
 * Bilingual label patterns. TranslateHelper.translateMenuItem composes the menu labels from
 * '<VERB>|<ENTITY>' and the grafiosch-base messages{,_de}.properties entries CREATE='Create
 * {{i18nRecord}}'/'Erstellen {{i18nRecord}}', NEW='New {{i18nRecord}}'/'Neues {{i18nRecord}}',
 * DELETE_RECORD='Delete {{i18nRecord}}'/'Löschen {{i18nRecord}}'. The tree uses 'DELETE|PORTFOLIO'
 * and 'DELETE|SECURITYACCOUNT', but DELETE='Delete'/'Löschen' carries no placeholder, so the entity
 * never appears there. The confirm dialog's accept button comes from PrimeNG
 * (src/app/lib/translator/primeng.translations.ts).
 */
export const RX = {
  createPortfolio: /(Create\s+Portfolio|Erstellen\s+Portfolio)/i,
  securityAccountsFolder: /(Securities\s+accounts|Depots)/i,
  createSecurityAccount: /(Create\s+Securities\s*account|Erstellen\s+Depot)/i,
  createCashAccount: /(New\s+Cash\s*account|Neues\s+Bankkonto)/i,
  deleteNode: /^(Delete|Löschen)$/,
  deleteCashAccount: /(Delete\s+Cash\s*account|Löschen\s+Bankkonto)/i,
  /** Header of the delete question — AppHelper.confirmationDialog translates MSG_GENERAL_HEADER. */
  confirmHeader: /^(Confirmation|Bestätigung)$/,
  confirmYes: /^(Yes|Ja)$/,
};

/** Label of the portfolio node in the main tree, e.g. "SaxoTrader / CHF". */
export function portfolioLabel(p: PortfolioFixture): string {
  return `${p.name} / ${p.currency}`;
}

/** The portfolio's own tree node content element. */
export function portfolioNode(page: Page, p: PortfolioFixture): Locator {
  return page.locator('.p-tree-node-content', {hasText: portfolioLabel(p)}).first();
}

/**
 * The <p-treenode> element that owns the portfolio, so its children ("Securities accounts"/"Depots"
 * and the securities account leaves) can be addressed unambiguously. `alledit` ends up with two
 * portfolios that carry an identically labelled folder, so an unscoped getByText would be ambiguous.
 * `.last()` picks the innermost of the nesting <p-treenode> ancestors that all contain the label.
 */
export function portfolioSubtree(page: Page, p: PortfolioFixture): Locator {
  return page.locator('p-treenode')
    .filter({has: page.locator('.p-tree-node-content', {hasText: portfolioLabel(p)})})
    .last();
}

/** Right-clicks `target` and clicks the p-contextmenu item matching `itemRx`. */
export async function openTreeContextMenu(page: Page, target: Locator, itemRx: RegExp): Promise<void> {
  await target.waitFor({state: 'visible', timeout: 15_000});
  await target.click({button: 'right'});
  const item = page.locator('p-contextmenu').getByText(itemRx).first();
  await item.waitFor({state: 'visible', timeout: 5_000});
  await item.click();
}

/**
 * Confirms the delete question. Header and buttons are both translated — the header comes from
 * MSG_GENERAL_HEADER ('Confirmation'/'Bestätigung', the default headerKey of
 * AppHelper.confirmationDialog), the buttons from PrimeNG.
 */
export async function confirmDelete(page: Page): Promise<void> {
  const confirmDialog = page.getByRole('alertdialog', {name: RX.confirmHeader});
  await confirmDialog.waitFor({state: 'visible', timeout: 10_000});
  await confirmDialog.getByRole('button', {name: RX.confirmYes}).click();
}

/**
 * Waits for a locator and reports whether it showed up, instead of failing. Needed by the teardown
 * spec, which must tolerate an entity that a partially failed create run never produced — but must
 * NOT mistake a tree that is still rendering for an already deleted node. A bare isVisible() checks
 * the current instant and would silently skip a node that appears a moment later, turning the
 * teardown green while it deleted nothing.
 */
export async function appearsWithin(target: Locator, timeout = 15_000): Promise<boolean> {
  return target.waitFor({state: 'visible', timeout})
    .then(() => true)
    .catch(() => false);
}

/** Fills a dynamic-form text input and dispatches the events Angular needs to commit the value. */
export async function fillFormInput(dialog: Locator, selector: string, value: string): Promise<void> {
  const input = dialog.locator(selector);
  await input.waitFor({state: 'visible', timeout: 10_000});
  await input.click();
  await input.fill(value);
  await input.dispatchEvent('input');
  await input.blur();
}

/**
 * Selects a value in a native <select> of a dynamic-form whose options are loaded asynchronously from
 * the backend (currencies, trading platform plans).
 */
export async function selectWhenLoaded(dialog: Locator, selector: string,
    option: {value?: string; label?: string; index?: number}): Promise<void> {
  const select = dialog.locator(selector);
  await select.waitFor({state: 'visible', timeout: 10_000});
  await expect(select.locator('option')).not.toHaveCount(0, {timeout: 10_000});
  await select.selectOption(option as any);
  await select.dispatchEvent('change');
}

/**
 * Formats an ISO date the way the PrimeNG date picker of the given user renders it. The picker uses
 * GlobalparameterService.getCalendarTwoNumberDateFormat(), which is moment's locale pattern 'L' with
 * the four-digit year token replaced by PrimeNG's `y` — and that token is a **two-digit** year. So
 * de-CH types '03.01.00' and en-US '01/03/00'; a four-digit year is rejected and blur wipes the field
 * again (same trap as 010-import-template-group and 047-create-derived-security).
 */
export function toShortDate(isoDate: string, locale: string): string {
  const [year, month, day] = isoDate.split('-');
  const yy = year.slice(2);
  switch (locale) {
    case 'de-CH':
      return `${day}.${month}.${yy}`;
    case 'en-US':
      return `${month}/${day}/${yy}`;
    default:
      throw new Error(`Unknown date picker format for locale '${locale}' - extend toShortDate()`);
  }
}

/**
 * Writes an ISO date into a p-datepicker inside an editable-table row. The picker ignores input
 * events without a preceding keydown (PrimeNG's isKeydown guard), so fill() never reaches the model —
 * the text has to be typed key by key, exactly as in 047-create-derived-security.spec.ts.
 */
export async function setRowDate(row: Locator, index: number, isoDate: string,
    locale: string): Promise<void> {
  const input = row.locator('p-datepicker input').nth(index);
  await input.waitFor({state: 'visible', timeout: 5_000});
  const wanted = toShortDate(isoDate, locale);
  if (await input.inputValue() === wanted) {
    return;
  }
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(wanted, {delay: 20});
  await input.blur();
  await expect(input, `date picker did not keep the typed date ${wanted}`).toHaveValue(wanted);
}

/**
 * Adds one row to the trading period table of the securities account dialog. The table is an
 * EditableTableComponent in row-edit mode; its '+' button sits in a sibling div outside
 * <editable-table> and it renders no .data-container, so addEditableTableRow() from
 * generic-connector.helpers.ts does not apply here.
 *
 * Nothing is persisted per row — the complete securities account including its trading periods is
 * sent when the dialog is submitted.
 *
 * @param locale users.csv locale of the logged-in user, needed for the date picker format.
 */
export async function addTradingPeriod(dialog: Locator, tp: TradingPeriodData,
    locale: string): Promise<void> {
  const table = dialog.locator('trading-period-table');
  await table.locator('button:has(i.pi-plus)').click();

  // addNewRow() appends the row and enters edit mode after a ~50ms setTimeout.
  const row = table.locator('tbody tr').last();
  await row.locator('button:has(i.pi-check)').waitFor({state: 'visible', timeout: 5_000});

  // Column order: specInvestInstrument, categoryType, dateFrom, dateTo. The option values are the
  // enum names, so the selection is independent of the UI language.
  await row.locator('select').nth(0).selectOption(tp.specInvestInstrument);
  await row.locator('select').nth(1).selectOption(tp.categoryType ?? '');
  await setRowDate(row, 0, tp.dateFrom, locale);

  await row.locator('button:has(i.pi-check)').click();
}
