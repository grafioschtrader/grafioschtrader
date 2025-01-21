package grafioschtrader.reportviews.securitydividends;

import java.util.Map;

import grafiosch.common.DataHelper;
import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.types.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

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

  public double closeEndOfYear;
  public Double exchangeRateEndOfYear;

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
        currencyPrecisionMap.getOrDefault(getPositionCurrency(), GlobalConstants.FID_STANDARD_FRACTION_DIGITS));
  }

  public double getTaxableAmount() {
    return DataHelper.round(taxableAmount,
        currencyPrecisionMap.getOrDefault(getPositionCurrency(), GlobalConstants.FID_STANDARD_FRACTION_DIGITS));
  }

  public double getTaxableAmountMC() {
    return DataHelper.round(taxableAmountMC, precisionMC);
  }

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

  protected void getAndSetExchangeRateEndOfYear(Map<Integer, Historyquote> historyquoteIdMap,
      DateTransactionCurrencypairMap dateCurrencyMap, String currencySearched) {
    if (!dateCurrencyMap.getMainCurrency().equals(currencySearched)) {
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
}
