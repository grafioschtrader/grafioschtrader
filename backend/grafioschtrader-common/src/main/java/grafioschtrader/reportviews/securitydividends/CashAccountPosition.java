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

  public double feeCashAccount = 0.0;
  @Schema(description = "Account costs in the currency of the tenant.")
  public double feeCashAccountMC = 0.0;

  public double feeSecurityAccount = 0.0;
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

  public void updateInterestPosition(Transaction transaction, SecurityDividendsYearGroup securityDividendsYearGroup,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    double exchangeRate = transaction.getExchangeRateOnCurrency(dateCurrencyMap.getMainCurrency(), dateCurrencyMap);
    updatedTaxes(transaction, exchangeRate);

  }

  public void updateFeePosition(Transaction transaction, SecurityDividendsYearGroup securityDividendsYearGroup,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    double exchangeRate = transaction.getExchangeRateOnCurrency(dateCurrencyMap.getMainCurrency(), dateCurrencyMap);
    if(transaction.getIdSecurityaccount() == null) {
      feeCashAccount += transaction.getCashaccountAmount() * -1;
      feeCashAccountMC += transaction.getCashaccountAmount() * exchangeRate * -1;
    } else {
      feeSecurityAccount += transaction.getCashaccountAmount() * -1;
      feeSecurityAccountMC += transaction.getCashaccountAmount() * exchangeRate * -1;
    }
  }


  public void attachHistoryquoteAndCalcPositionTotal(Map<Integer, Historyquote> historyquoteIdMap,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    if (cashBalance != 0.0) {
      getAndSetExchangeRateEndOfYear(historyquoteIdMap, dateCurrencyMap, cashaccount.getCurrency());
      cashBalanceMC = exchangeRateEndOfYear == null ? cashBalance : cashBalance * exchangeRateEndOfYear;
    }

  }
}
