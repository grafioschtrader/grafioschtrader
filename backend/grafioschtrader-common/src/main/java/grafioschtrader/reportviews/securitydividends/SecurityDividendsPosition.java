package grafioschtrader.reportviews.securitydividends;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.SecurityCostPosition;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * For each year and security combination there is one of this object.
 *
 */
public class SecurityDividendsPosition extends AccountDividendPosition {

  public Security security;
  public int countPaidTransactions;
  @Schema(description = "Number of units held at the end of the year.")
  public double unitsAtEndOfYear = 0.0;
  
  @JsonIgnore
  public Double splitFactorAfter;

  public SecurityDividendsPosition(int precisionMC, Map<String, Integer> currencyPrecisionMap) {
    super(precisionMC, currencyPrecisionMap);
  }

  public void updateAccumulateReduce(Transaction transaction, SecurityDividendsYearGroup securityDividendsYearGroup,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    if (transaction.getTransactionCost() != null && transaction.getTransactionCost() > 0.0) {
      SecurityCostPosition securityCostPosition = new SecurityCostPosition(precisionMC);
      transaction.calcCostTaxMaybeBasePrice(dateCurrencyMap.getMainCurrency(), securityCostPosition, dateCurrencyMap,
          false);
      securityDividendsYearGroup.securityCostGroup.sumPositionToGroupTotal(securityCostPosition);
      countPaidTransactions++;
    }
  }

  public void updateDividendPosition(Transaction transaction, DateTransactionCurrencypairMap dateCurrencyMap) {
    this.unitsAtEndOfYear = transaction.getUnits();
    Double exchangeRate = DataBusinessHelper.getCurrencyExchangeRateToMainCurreny(transaction, dateCurrencyMap);

    exchangeRate = exchangeRate == null
        || transaction.getCashaccount().getCurrency().equals(dateCurrencyMap.getMainCurrency()) ? 1
            : transaction.getSecurity().getCurrency().equals(dateCurrencyMap.getMainCurrency()) ? 1 / exchangeRate
                : exchangeRate;
    updatedTaxes(transaction, exchangeRate);
  }

  /**
   * Calculate the value of a security at the end of year.
   *
   * @param historyquoteIdMap
   * @param dateCurrencyMap
   */
  public void attachHistoryquoteAndCalcPositionTotal(Map<Integer, Historyquote> historyquoteIdMap,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    if (unitsAtEndOfYear > 0) {
      getAndSetExchangeRateEndOfYear(historyquoteIdMap, dateCurrencyMap, security.getCurrency());
      var historyquote = historyquoteIdMap.get(security.getIdSecuritycurrency());
      if (historyquote != null) {
        closeEndOfYear = historyquote.getClose() * (splitFactorAfter == null? 1.0: splitFactorAfter); 
        valueAtEndOfYearMC = closeEndOfYear * unitsAtEndOfYear;
        if (exchangeRateEndOfYear != null) {
          valueAtEndOfYearMC = valueAtEndOfYearMC * exchangeRateEndOfYear;
        }
      }
    }
  }

  @Override
  protected String getPositionCurrency() {
    return security.getCurrency();
  }

}
