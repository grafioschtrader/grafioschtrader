package grafioschtrader.reportviews.securitydividends;

import java.util.Map;

import grafiosch.common.DataHelper;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    There is one instance per year and cash account.
    This contains some account-relevant data that is useful for completing a tax return, for example.""")
public class CashAccountPosition extends AccountDividendPosition {

  @Schema(description = "The cash account to which this instance refers.")
  public Cashaccount cashaccount;
 
  @Schema(description = "Account balance at the end of the year in the account currency.")
  public double cashBalance = 0.0;
  
  @Schema(description = "Account balance at the end of the year in the tenant's main currency.")
  public double cashBalanceMC = 0.0;

  @Schema(description = "Total fees charged directly to this cash account in the account currency.")
  public double feeCashAccount = 0.0;
  
  @Schema(description = "Account costs in the currency of the tenant.")
  public double feeCashAccountMC = 0.0;

  @Schema(description = "Total fees for security transactions that are charged to this cash account in the account currency.")
  public double feeSecurityAccount = 0.0;
  
  @Schema(description = "Total fees for security transactions that are charged to this cash account in the main currency.")
  public double feeSecurityAccountMC = 0.0;

  public CashAccountPosition(Cashaccount cashaccount, int precisionMC, Map<String, Integer> currencyPrecisionMap) {
    super(precisionMC, currencyPrecisionMap);
    this.cashaccount = cashaccount;
  }

  @Override
  protected String getPositionCurrency() {
    return cashaccount.getCurrency();
  }

  public double getCashBalance() {
    return DataHelper.round(cashBalance, precisionMC);
  }

  public double getCashBalanceMC() {
    return DataHelper.round(cashBalanceMC, precisionMC);
  }

  public double getFeeCashAccountMC() {
    return DataHelper.round(feeCashAccountMC, precisionMC);
  }

  public double getFeeSecurityAccountMC() {
    return DataHelper.round(feeSecurityAccountMC, precisionMC);
  }

  /**
   * Updates the interest position based on an interest transaction.
   * This method processes interest income transactions and updates the inherited
   * tax-related calculations from the parent class.
   * 
   * <p>The method calculates the appropriate exchange rate for the transaction
   * and delegates to the parent class's {@link #updatedTaxes(Transaction, double)}
   * method to handle the interest income and tax calculations.</p>
   * 
   * @param transaction the interest transaction to process
   * @param securityDividendsYearGroup the year group context for this transaction
   * @param dateCurrencyMap currency mapping and date information for exchange rate calculations
   */
  public void updateInterestPosition(Transaction transaction, SecurityDividendsYearGroup securityDividendsYearGroup,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    double exchangeRate = transaction.getExchangeRateOnCurrency(dateCurrencyMap.getMainCurrency(), dateCurrencyMap);
    updatedTaxes(transaction, exchangeRate);

  }

  /**
   * Updates the fee position based on a fee transaction.
   * This method categorizes fees into two types based on whether they are related
   * to security transactions or are pure cash account fees.
   * 
   * <p>Fee classification:</p>
   * <ul>
   *   <li><strong>Cash Account Fees:</strong> When transaction.getIdSecurityaccount() is null,
   *       indicating fees charged directly to the cash account</li>
   *   <li><strong>Security Account Fees:</strong> When transaction.getIdSecurityaccount() is not null,
   *       indicating fees for security transactions charged to this cash account</li>
   * </ul>
   * 
   * <p>Note: The method multiplies amounts by -1 because fee transactions typically
   * have negative cash account amounts, and we want to track fees as positive values.</p>
   * 
   * @param transaction the fee transaction to process
   * @param securityDividendsYearGroup the year group context for this transaction
   * @param dateCurrencyMap currency mapping and date information for exchange rate calculations
   */
  public void updateFeePosition(Transaction transaction, SecurityDividendsYearGroup securityDividendsYearGroup,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    double exchangeRate = transaction.getExchangeRateOnCurrency(dateCurrencyMap.getMainCurrency(), dateCurrencyMap);
    if (transaction.getIdSecurityaccount() == null) {
      feeCashAccount += transaction.getCashaccountAmount() * -1;
      feeCashAccountMC += transaction.getCashaccountAmount() * exchangeRate * -1;
    } else {
      feeSecurityAccount += transaction.getCashaccountAmount() * -1;
      feeSecurityAccountMC += transaction.getCashaccountAmount() * exchangeRate * -1;
    }
  }

  /**
   * Attaches historical quotes and calculates the cash position total in main currency.
   * This method is called to finalize the cash account position calculations at year-end.
   *
   * @param historyquoteIdMap map of security currency IDs to their historical quotes
   * @param dateCurrencyMap currency pair mapping and date information for exchange rate lookups
   */
  public void attachHistoryquoteAndCalcPositionTotal(Map<Integer, Historyquote> historyquoteIdMap,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    if (cashBalance != 0.0) {
      getAndSetExchangeRateEndOfYear(historyquoteIdMap, dateCurrencyMap, cashaccount.getCurrency());
      cashBalanceMC = exchangeRateEndOfYear == null ? cashBalance : cashBalance * exchangeRateEndOfYear;
    }

  }
}
