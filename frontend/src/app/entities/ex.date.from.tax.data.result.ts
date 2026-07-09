/**
 * Result of back-filling dividend ex-dates from the imported Swiss ICTax tax data for a single tax year.
 */
export interface ExDateFromTaxDataResult {
  /** Number of dividend transactions of the tenant within the tax year that were examined. */
  dividendTransactions: number;
  /** Number of examined transactions that already had an ex-date and were therefore left unchanged. */
  alreadySet: number;
  /** Number of transactions whose ex-date was set from the matching tax data. */
  exDateAssigned: number;
  /** Number of transactions without an ex-date that could not be matched to any tax payment. */
  unmatched: number;
}
