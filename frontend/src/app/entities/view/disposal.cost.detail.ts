/**
 * One line of the explanation of a disposal cost estimate of the hypothetical sale: the rule that priced a part of
 * the sale in one account, or the NLS key of the reason why that part could not be priced.
 *
 * Corresponds to the backend record grafioschtrader.reportviews.securityaccount.DisposalCostDetail.
 */
export interface DisposalCostDetail {
  /** Name of the security account (or cash account for the conversion into the main currency) */
  securityaccountName?: string;
  /** Cost component: COMMISSION, TAX or FX */
  part: 'COMMISSION' | 'TAX' | 'FX';
  /** Name of the matched rule, absent when the part could not be estimated */
  rule?: string;
  /** NLS key of the reason why the part is unknown, absent when a rule matched */
  reason?: string;
  /** Untranslated detail such as the country of a tax model or an error text */
  detail?: string;
  /** Estimated amount in the currency of the security, absent when unknown */
  amount?: number;
}
