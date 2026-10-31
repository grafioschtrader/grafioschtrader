import { detailEntry, MobileDetailEntry, MobileDetailSection } from '../shared/mobile.detail';

/** How the report groups the accounts of the tenant, the two choices of the desktop view. */
export type MobilePortfolioGrouping = 'portfolio' | 'currency';

/**
 * The figures every level of the report carries: the grand total, a group and one account. All amounts are in the
 * main currency of the tenant.
 */
interface MobileSummaryFigures {
  valueMC: number | null;
  valueSecuritiesMC: number | null;
  cashBalanceMC: number | null;
  gainLossSecuritiesMC: number | null;
  gainLossCurrencyMC: number | null;
  accountInterestMC: number | null;
  accountFeesMC: number | null;
  cashAccountTransactionFeeMC: number | null;
  excludedDivTaxMC: number | null;
  externalCashTransferMC: number | null;
  cashTransferMC: number | null;
  disposalCostMC: number | null;
  valueAfterDisposalMC: number | null;
}

/**
 * The fields of the account report (backend AccountPositionGrandSummary) the mobile application reads, declared here
 * like those of the watchlist. The disposal cost fields are only present when the estimate is switched on.
 */
export interface MobileAccountGrandSummary {
  mainCurrency: string;
  grandValueMC?: number | null;
  grandValueSecuritiesMC?: number | null;
  grandCashBalanceMC?: number | null;
  grandGainLossSecuritiesMC?: number | null;
  grandGainLossCurrencyMC?: number | null;
  grandAccountInterestMC?: number | null;
  grandAccountFeesMC?: number | null;
  grandCashAccountTransactionFeeMC?: number | null;
  grandExcludedDivTaxMC?: number | null;
  grandExternalCashTransferMC?: number | null;
  grandCashTransferMC?: number | null;
  grandDisposalCostMC?: number | null;
  grandValueAfterDisposalMC?: number | null;
  missingExchangeRates?: { fromCurrency: string; toCurrency: string }[] | null;
  accountPositionGroupSummaryList: MobileAccountGroupSummary[] | null;
}

export interface MobileAccountGroupSummary {
  groupName: string | null;
  groupValueMC?: number | null;
  groupValueSecuritiesMC?: number | null;
  groupCashBalanceMC?: number | null;
  groupGainLossSecuritiesMC?: number | null;
  groupGainLossCurrencyMC?: number | null;
  groupAccountInterestMC?: number | null;
  groupAccountFeesMC?: number | null;
  groupCashAccountTransactionFeeMC?: number | null;
  groupExcludedDivTaxMC?: number | null;
  groupExternalCashTransferMC?: number | null;
  groupCashTransferMC?: number | null;
  groupDisposalCostMC?: number | null;
  groupValueAfterDisposalMC?: number | null;
  accountPositionSummaryList: MobileAccountSummary[] | null;
}

export interface MobileAccountSummary extends Partial<MobileSummaryFigures> {
  cashaccount: { idSecuritycashAccount: number; name?: string | null; currency?: string | null };
  /** Balance in the currency of the account. */
  cashBalance?: number | null;
  /** Exchange rate of the account currency to the main currency; null for an account in the main currency. */
  closePrice?: number | null;
}

/** One group of the overview: its value, how it is made up, and its share of the total. */
export interface MobilePortfolioGroupCard {
  groupName: string;
  valueMC: number | null;
  valueSecuritiesMC: number | null;
  cashBalanceMC: number | null;
  gainLossSecuritiesMC: number | null;
  /** Share of the group in the total value; null when the total is not positive. */
  sharePercentage: number | null;
}

/**
 * The groups of the report for the overview, in the order of the server.
 *
 * @param summary The account report
 * @returns One card per group
 */
export function mobilePortfolioGroupCards(summary: MobileAccountGrandSummary): MobilePortfolioGroupCard[] {
  const total = summary.grandValueMC ?? 0;
  return (summary.accountPositionGroupSummaryList ?? []).map((group) => ({
    groupName: group.groupName ?? '',
    valueMC: group.groupValueMC ?? null,
    valueSecuritiesMC: group.groupValueSecuritiesMC ?? null,
    cashBalanceMC: group.groupCashBalanceMC ?? null,
    gainLossSecuritiesMC: group.groupGainLossSecuritiesMC ?? null,
    sharePercentage: total > 0 && group.groupValueMC != null ? (group.groupValueMC / total) * 100 : null
  }));
}

/** Finds a group of the report by its name; null when the report no longer contains it. */
export function findMobilePortfolioGroup(
  summary: MobileAccountGrandSummary,
  groupName: string
): MobileAccountGroupSummary | null {
  return (summary.accountPositionGroupSummaryList ?? []).find((group) => group.groupName === groupName) ?? null;
}

/** The currency pairs without an exchange rate, such as 'BTC/CHF'; their accounts are missing from every sum. */
export function mobileMissingExchangeRates(summary: MobileAccountGrandSummary): string | null {
  const missing = summary.missingExchangeRates ?? [];
  return missing.length ? missing.map((rate) => `${rate.fromCurrency}/${rate.toCurrency}`).join(', ') : null;
}

/** The composition and results of the whole tenant, below the groups of the overview. */
export function mobileGrandSection(summary: MobileAccountGrandSummary): MobileDetailSection {
  return figureSection({ titleKey: 'GRAND_TOTAL' }, grandFigures(summary), summary.mainCurrency);
}

/** The composition and results of one group, at the top of its page. */
export function mobileGroupSection(group: MobileAccountGroupSummary, mainCurrency: string): MobileDetailSection {
  return figureSection({ titleKey: 'TOTAL' }, groupFigures(group), mainCurrency);
}

/**
 * One section per account of a group, titled with the account name. An account shows its balance in its own currency
 * and, for a foreign currency, the exchange rate; its other figures are in the main currency, like on the desktop.
 *
 * @param group Group of the report
 * @param mainCurrency Main currency of the tenant
 * @returns The sections in the order of the server
 */
export function mobileAccountSections(group: MobileAccountGroupSummary, mainCurrency: string): MobileDetailSection[] {
  return (group.accountPositionSummaryList ?? []).map((account) => {
    const currency = account.cashaccount.currency ?? mainCurrency;
    return figureSection({ title: account.cashaccount.name ?? '' }, accountFigures(account), mainCurrency, [
      detailEntry('CASH_BALANCE', account.cashBalance, 'amount', { currency }),
      currency !== mainCurrency ? detailEntry('CURRENCY_RATE', account.closePrice, 'price') : null,
      amount('VALUE', account.valueMC, mainCurrency, false)
    ]);
  });
}

/**
 * The lines of one level of the report. Amounts of zero are left out, so an account without securities or fees shows
 * only what it holds.
 *
 * @param title Title of the section
 * @param figures Figures of the level, in the main currency
 * @param currency Main currency of the tenant
 * @param accountLines Lines of an account, which take the place of the balance in the main currency
 */
function figureSection(
  title: Pick<MobileDetailSection, 'titleKey' | 'title'>,
  figures: MobileSummaryFigures,
  currency: string,
  accountLines?: (MobileDetailEntry | null)[]
): MobileDetailSection {
  const entries = [
    ...(accountLines ?? [amount('CASH_BALANCE', figures.cashBalanceMC, currency, false)]),
    amount('SECURITY', figures.valueSecuritiesMC, currency, false),
    amount('GAIN_LOSS_SECURITIES', figures.gainLossSecuritiesMC, currency, true),
    amount('GAIN_LOSS_CURRENCY', figures.gainLossCurrencyMC, currency, true),
    amount('INTEREST_CASHACCOUNT', figures.accountInterestMC, currency, true),
    amount('FEE', figures.accountFeesMC, currency, true),
    amount('CASH_ACCOUNT_TRANSACTION_FEE', figures.cashAccountTransactionFeeMC, currency, true),
    amount('EXCLUDED_DIV_TAX', figures.excludedDivTaxMC, currency, true),
    amount('EXTERNAL_CASH_TRANSFER', figures.externalCashTransferMC, currency, true),
    amount('CASH_TRANSFER', figures.cashTransferMC, currency, true),
    amount('DISPOSAL_COST', figures.disposalCostMC, currency, true),
    amount('VALUE_AFTER_DISPOSAL', figures.valueAfterDisposalMC, currency, false)
  ];
  return { ...title, entries: entries.filter((e): e is MobileDetailEntry => e !== null) };
}

/** An amount line, or null for a missing amount or one of zero. */
function amount(
  labelKey: string,
  value: number | null | undefined,
  currency: string,
  signed: boolean
): MobileDetailEntry | null {
  return value ? detailEntry(labelKey, value, 'amount', { currency, signed }) : null;
}

function grandFigures(s: MobileAccountGrandSummary): MobileSummaryFigures {
  return {
    valueMC: s.grandValueMC ?? null,
    valueSecuritiesMC: s.grandValueSecuritiesMC ?? null,
    cashBalanceMC: s.grandCashBalanceMC ?? null,
    gainLossSecuritiesMC: s.grandGainLossSecuritiesMC ?? null,
    gainLossCurrencyMC: s.grandGainLossCurrencyMC ?? null,
    accountInterestMC: s.grandAccountInterestMC ?? null,
    accountFeesMC: s.grandAccountFeesMC ?? null,
    cashAccountTransactionFeeMC: s.grandCashAccountTransactionFeeMC ?? null,
    excludedDivTaxMC: s.grandExcludedDivTaxMC ?? null,
    externalCashTransferMC: s.grandExternalCashTransferMC ?? null,
    cashTransferMC: s.grandCashTransferMC ?? null,
    disposalCostMC: s.grandDisposalCostMC ?? null,
    valueAfterDisposalMC: s.grandValueAfterDisposalMC ?? null
  };
}

function groupFigures(g: MobileAccountGroupSummary): MobileSummaryFigures {
  return {
    valueMC: g.groupValueMC ?? null,
    valueSecuritiesMC: g.groupValueSecuritiesMC ?? null,
    cashBalanceMC: g.groupCashBalanceMC ?? null,
    gainLossSecuritiesMC: g.groupGainLossSecuritiesMC ?? null,
    gainLossCurrencyMC: g.groupGainLossCurrencyMC ?? null,
    accountInterestMC: g.groupAccountInterestMC ?? null,
    accountFeesMC: g.groupAccountFeesMC ?? null,
    cashAccountTransactionFeeMC: g.groupCashAccountTransactionFeeMC ?? null,
    excludedDivTaxMC: g.groupExcludedDivTaxMC ?? null,
    externalCashTransferMC: g.groupExternalCashTransferMC ?? null,
    cashTransferMC: g.groupCashTransferMC ?? null,
    disposalCostMC: g.groupDisposalCostMC ?? null,
    valueAfterDisposalMC: g.groupValueAfterDisposalMC ?? null
  };
}

function accountFigures(a: MobileAccountSummary): MobileSummaryFigures {
  return {
    valueMC: a.valueMC ?? null,
    valueSecuritiesMC: a.valueSecuritiesMC ?? null,
    cashBalanceMC: a.cashBalanceMC ?? null,
    gainLossSecuritiesMC: a.gainLossSecuritiesMC ?? null,
    gainLossCurrencyMC: a.gainLossCurrencyMC ?? null,
    accountInterestMC: a.accountInterestMC ?? null,
    accountFeesMC: a.accountFeesMC ?? null,
    cashAccountTransactionFeeMC: a.cashAccountTransactionFeeMC ?? null,
    excludedDivTaxMC: a.excludedDivTaxMC ?? null,
    externalCashTransferMC: a.externalCashTransferMC ?? null,
    cashTransferMC: a.cashTransferMC ?? null,
    disposalCostMC: a.disposalCostMC ?? null,
    valueAfterDisposalMC: a.valueAfterDisposalMC ?? null
  };
}
