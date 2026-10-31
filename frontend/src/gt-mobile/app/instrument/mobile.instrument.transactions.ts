import { MobileDetailEntry, MobileDetailSection } from '../shared/mobile.detail';

/**
 * The fields of the security transaction report (backend SecurityTransactionSummary) the mobile application reads,
 * declared here like those of the watchlist. The backend removes the security from every transaction of the list.
 */
export interface MobileSecurityTransactionSummary {
  transactionPositionList: MobileSecurityTransactionPosition[] | null;
  securityPositionSummary: {
    units?: number | null;
    valueSecurity?: number | null;
    gainLossSecurity?: number | null;
    transactionCost?: number | null;
    taxCost?: number | null;
  } | null;
}

export interface MobileSecurityTransactionPosition {
  transaction: {
    idTransaction: number;
    /** Local date and time of the booking, ISO without a time zone. */
    transactionTime: string;
    transactionType: string;
    units?: number | null;
    quotation?: number | null;
    cashaccountAmount?: number | null;
    transactionCost?: number | null;
    taxCost?: number | null;
    cashaccount?: { name?: string | null; currency?: string | null } | null;
  };
  transactionGainLoss?: number | null;
  transactionGainLossPercentage?: number | null;
  /** Only when read for the chart: the price adjusted for later splits. */
  quotationSplitCorrection?: number | null;
  /** Units held after this transaction, adjusted for later splits. */
  holdingsSplitAdjusted?: number | null;
}

/** One transaction as a line of the mobile list. */
export interface MobileTransactionRow {
  idTransaction: number;
  transactionTime: string;
  /** Translation key of the transaction type. */
  typeKey: string;
  units: number | null;
  quotation: number | null;
  /** Amount booked on the cash account, in its currency. */
  cashaccountAmount: number | null;
  cashaccountCurrency: string;
  cashaccountName: string;
  /** Gain of a sale in the currency of the security; null for other transactions. */
  gainLoss: number | null;
  gainLossPercentage: number | null;
  /** Costs other than zero; null when there were none, so the line stays short. */
  transactionCost: number | null;
  taxCost: number | null;
}

/**
 * Turns the transaction report of a security into the lines of the mobile list, the newest first. Transactions at the
 * same time keep the reverse order of the server, so the later booking stays on top.
 *
 * @param summary Transaction report of the security
 * @returns The lines in display order
 */
export function mobileTransactionRows(summary: MobileSecurityTransactionSummary): MobileTransactionRow[] {
  return [...(summary.transactionPositionList ?? [])]
    .reverse()
    .map(transactionRow)
    .sort((a, b) => (a.transactionTime < b.transactionTime ? 1 : a.transactionTime > b.transactionTime ? -1 : 0));
}

function transactionRow(position: MobileSecurityTransactionPosition): MobileTransactionRow {
  const transaction = position.transaction;
  return {
    idTransaction: transaction.idTransaction,
    transactionTime: transaction.transactionTime,
    typeKey: transaction.transactionType,
    units: transaction.units ?? null,
    quotation: transaction.quotation ?? null,
    cashaccountAmount: transaction.cashaccountAmount ?? null,
    cashaccountCurrency: transaction.cashaccount?.currency ?? '',
    cashaccountName: transaction.cashaccount?.name ?? '',
    gainLoss: position.transactionGainLoss ?? null,
    gainLossPercentage: position.transactionGainLossPercentage ?? null,
    transactionCost: transaction.transactionCost || null,
    taxCost: transaction.taxCost || null
  };
}

/**
 * The position the transactions lead to, shown above their list: units, value, gain and the costs paid, in the currency
 * of the security. Lines without a value are omitted.
 *
 * @param summary Transaction report of the security
 * @param currency Currency of the security
 * @returns The section, without lines when the report carries no position
 */
export function mobileTransactionSummarySection(
  summary: MobileSecurityTransactionSummary,
  currency: string
): MobileDetailSection {
  const position = summary.securityPositionSummary;
  const entries: (MobileDetailEntry | null)[] = position
    ? [
        position.units != null ? { labelKey: 'QUANTITY', value: position.units, format: 'units' } : null,
        amountEntry('VALUE_SECURITY', position.valueSecurity, currency, false),
        amountEntry('GAIN', position.gainLossSecurity, currency, true),
        amountEntry('TRANSACTION_COST', position.transactionCost, currency, false),
        amountEntry('TAX_COST', position.taxCost, currency, false)
      ]
    : [];
  return { titleKey: 'HOLDINGS', entries: entries.filter((e): e is MobileDetailEntry => e !== null) };
}

function amountEntry(
  labelKey: string,
  value: number | null | undefined,
  currency: string,
  signed: boolean
): MobileDetailEntry | null {
  return value == null ? null : { labelKey, value, format: 'amount', currency, signed };
}
