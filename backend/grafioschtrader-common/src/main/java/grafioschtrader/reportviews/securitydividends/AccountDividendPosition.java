package grafioschtrader.reportviews.securitydividends;

import java.util.Map;

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.types.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Abstract base class for managing dividend and interest position calculations within an account. This class provides
 * common functionality for tracking dividend payments, interest income, tax withholdings, and position valuations
 * across different currencies.
 *
 * <p>
 * The class handles currency conversions to the main currency (MC) and maintains precision settings for different
 * currencies. It tracks both taxable and tax-free income, automatic tax withholdings, and end-of-year position
 * valuations.
 * </p>
 */
public abstract class AccountDividendPosition {

  @Schema(description = "Interest or dividend received in the main currency, i.e. the tax is deducted.")
  public double realReceivedDivInterestMC = 0.0;

  @Schema(description = "Amount of tax that is automatically withheld on interest or dividends for certain transactions.")
  public double autoPaidTax = 0.0;

  @Schema(description = "Amount of tax in the main currency that is automatically withheld on interest or dividends for certain transactions.")
  public double autoPaidTaxMC = 0.0;

  @Schema(description = "Interest or dividend including the possible tax amount.")
  public double taxableAmount = 0.0;

  @Schema(description = "Interest or dividend including the possible tax amount in the main currency.")
  public double taxableAmountMC = 0.0;

  @Schema(description = "Tax-free income in the main currency.")
  public double taxFreeIncome = 0.0;

  @Schema(description = "Value of the position held in the main currency at the end of the year.")
  public Double valueAtEndOfYearMC;

  @Schema(description = "Closing price of the security at the end of the year.")
  public double closeEndOfYear;

  @Schema(description = "Exchange rate used for currency conversion at the end of the year.")
  public Double exchangeRateEndOfYear;

  @Schema(description = """
      True when the year-end exchange rate is the official one published by the tax authority rather than the year-end
      close of the currency pair. The two differ slightly because the tax authority uses a different fixing.""")
  public Boolean exchangeRateEndOfYearKursliste;

  protected int precisionMC;
  private Map<String, Integer> currencyPrecisionMap;

  protected abstract String getPositionCurrency();

  public AccountDividendPosition(int precisionMC, Map<String, Integer> currencyPrecisionMap) {
    this.precisionMC = precisionMC;
    this.currencyPrecisionMap = currencyPrecisionMap;
  }

  public Double getValueAtEndOfYearMC() {
    return valueAtEndOfYearMC == null ? null : DataHelper.round(valueAtEndOfYearMC, precisionMC);
  }

  public double getRealReceivedDivInterestMC() {
    return DataHelper.round(realReceivedDivInterestMC, precisionMC);
  }

  public double getAutoPaidTaxMC() {
    return DataHelper.round(autoPaidTaxMC,
        currencyPrecisionMap.getOrDefault(getPositionCurrency(), BaseConstants.FID_STANDARD_FRACTION_DIGITS));
  }

  public double getTaxableAmount() {
    return DataHelper.round(taxableAmount,
        currencyPrecisionMap.getOrDefault(getPositionCurrency(), BaseConstants.FID_STANDARD_FRACTION_DIGITS));
  }

  public double getTaxableAmountMC() {
    return DataHelper.round(taxableAmountMC, precisionMC);
  }

  /**
   * Updates the tax-related calculations based on a transaction. This method processes dividend/interest transactions
   * and updates the following:
   * <ul>
   * <li>Real received dividend/interest amount in main currency</li>
   * <li>Automatically paid tax amounts (both position and main currency)</li>
   * <li>Taxable amounts for interest transactions or taxable interest</li>
   * <li>Tax-free income for non-taxable dividend transactions</li>
   * </ul>
   *
   * @param transaction  the transaction to process for tax calculations
   * @param exchangeRate the exchange rate to convert from position currency to main currency
   */
  protected void updatedTaxes(Transaction transaction, double exchangeRate) {
    realReceivedDivInterestMC += transaction.getCashaccountAmount() * exchangeRate;
    if (transaction.getTaxCost() != null) {
      autoPaidTax += transaction.getTaxCost();
      autoPaidTaxMC += transaction.getTaxCost() * exchangeRate;
    }
    if (transaction.getTransactionType() == TransactionType.INTEREST_CASHACCOUNT || transaction.isTaxableInterest()) {
      double taxAmount = transaction.getCashaccountAmount()
          + (transaction.getTaxCost() != null ? transaction.getTaxCost() : 0.0);
      taxableAmount += taxAmount;
      taxableAmountMC += taxAmount * exchangeRate;
    } else {
      taxFreeIncome += transaction.getCashaccountAmount();
    }
  }

  /**
   * Retrieves and sets the exchange rate at the end of the year for the specified currency.
   *
   * <p>
   * If the searched currency is the same as the main currency, no exchange rate is needed. Otherwise the official
   * year-end rate of the tax authority is preferred, because that is the rate the tax return has to be filed with; it
   * also covers a currency for which the tenant holds no currency pair at all. Only when no such rate exists does the
   * year-end close of the currency pair apply.
   * </p>
   *
   * @param historyquoteIdMap    map of security currency IDs to their historical quotes
   * @param dateCurrencyMap      map containing currency pair information and main currency
   * @param currencySearched     the currency code for which to find the exchange rate
   * @param officialYearEndRates official year-end rates of that year in main currency per unit, keyed by currency;
   *                             empty when the tenant is not taxed by an authority that publishes them
   */
  protected void getAndSetExchangeRateEndOfYear(Map<Integer, Historyquote> historyquoteIdMap,
      DateTransactionCurrencypairMap dateCurrencyMap, String currencySearched,
      Map<String, Double> officialYearEndRates) {
    if (dateCurrencyMap.getMainCurrency().equals(currencySearched)) {
      return;
    }
    Double officialRate = officialYearEndRates == null ? null : officialYearEndRates.get(currencySearched);
    if (officialRate != null) {
      closeEndOfYear = officialRate;
      exchangeRateEndOfYear = officialRate;
      exchangeRateEndOfYearKursliste = Boolean.TRUE;
      return;
    }
    Currencypair currencypair = dateCurrencyMap.getCurrencypairByFromCurrency(currencySearched);
    if (currencypair != null) {
      var historyquote = historyquoteIdMap.get(currencypair.getIdSecuritycurrency());
      if (historyquote != null) {
        closeEndOfYear = historyquote.getClose();
        exchangeRateEndOfYear = closeEndOfYear;
      }
    }
  }
}
