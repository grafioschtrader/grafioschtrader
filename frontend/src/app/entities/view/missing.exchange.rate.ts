/**
 * A currency of a cash account that could not be converted into the main currency, because no exchange rate was
 * available for the reporting date.
 *
 * The affected account is still listed with its own balance, but it contributes to none of the main currency totals.
 * The client turns this into a warning so the user can tell an incomplete total from a wrong one.
 */
export interface MissingExchangeRate {
  /** Currency that could not be converted, ISO 4217. */
  fromCurrency: string;
  /** Main currency of the tenant or portfolio the conversion targeted, ISO 4217. */
  toCurrency: string;
  /** Date the exchange rate was required for. */
  date: string;
  /** Id of the currency pair, null when no currency pair exists at all for this combination. */
  idCurrencypair: number;
}
