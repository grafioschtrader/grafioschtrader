package grafioschtrader.reportviews.securitydividends;

import java.util.Map;

import grafioschtrader.common.DataHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.SecurityCostPosition;

/**
 * For each year and security combination there is one of this Object.
 * 
 * @author Hugo Graf
 *
 */
public class SecurityDividendsPosition {

  public Integer idSecuritycurrency;
  /**
   * For Bond when it is bought, some interest has to be paid to the previous
   * holder.
   */
  public double realReceivedDivInterestMC = 0.0;
  /**
   * For some Interest or Dividends there can be a tax, which is taken
   * automatically
   */
  public double autoPaidTax = 0.0;
  public double autoPaidTaxMC = 0.0;
  public double taxableAmount = 0.0;

  public double taxableAmountMC = 0.0;
  public double taxFreeIncome = 0.0;
  public double unitsAtEndOfYear = 0.0;
  public Double valueAtEndOfYearMC;
  public Security security;
  public Historyquote historyquote;
  public Double exchangeRateEndOfYear;
  public int countPaidTransactions;

  public SecurityDividendsPosition(Integer idSecuritycurrency) {
    this.idSecuritycurrency = idSecuritycurrency;
  }

  public void updateAccumulateReduce(Transaction transaction, SecurityDividendsYearGroup securityDividendsYearGroup,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    if (transaction.getTransactionCost() != null && transaction.getTransactionCost() > 0.0) {
      SecurityCostPosition securityCostPosition = new SecurityCostPosition();

      transaction.calcCostTaxMaybeBasePrice(dateCurrencyMap.getMainCurrency(), securityCostPosition, dateCurrencyMap,
          false);
      securityDividendsYearGroup.securityCostGroup.sumPositionToGroupTotal(securityCostPosition);
      countPaidTransactions++;
    }
  }

  public void updateDividendPosition(Transaction transaction, DateTransactionCurrencypairMap dateCurrencyMap) {
    this.unitsAtEndOfYear = transaction.getUnits();

    if (transaction.getIdTransaction().equals(39727)) {
      System.out.println(transaction);
    }

    Double exchangeRate = DataHelper.getCurrencyExchangeRateToMainCurreny(transaction, dateCurrencyMap);

    exchangeRate = exchangeRate == null
        || transaction.getCashaccount().getCurrency().equals(dateCurrencyMap.getMainCurrency()) ? 1
            : transaction.getSecurity().getCurrency().equals(dateCurrencyMap.getMainCurrency()) ? 1 / exchangeRate
                : exchangeRate;

    realReceivedDivInterestMC += transaction.getCashaccountAmount() * exchangeRate;

    if (transaction.getTaxCost() != null) {
      autoPaidTax += transaction.getTaxCost();
      autoPaidTaxMC += transaction.getTaxCost() * exchangeRate;
    }

    if (transaction.isTaxableInterest()) {
      double taxAmount = transaction.getCashaccountAmount()
          + (transaction.getTaxCost() != null ? transaction.getTaxCost() : 0.0);
      taxableAmount += taxAmount;
      taxableAmountMC += taxAmount * exchangeRate;
    } else {
      taxFreeIncome += transaction.getCashaccountAmount();
    }

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

      if (!dateCurrencyMap.getMainCurrency().equals(security.getCurrency())) {
        Currencypair currencypair = dateCurrencyMap.getCurrencypairByFromCurrency(security.getCurrency());
        if (currencypair != null) {
          historyquote = historyquoteIdMap.get(currencypair.getIdSecuritycurrency());
          if (historyquote != null) {
            exchangeRateEndOfYear = historyquote.getClose();
          }
        }
      }
      historyquote = historyquoteIdMap.get(idSecuritycurrency);
      if (historyquote != null) {
        valueAtEndOfYearMC = historyquote.getClose() * unitsAtEndOfYear;
        if (exchangeRateEndOfYear != null) {
          valueAtEndOfYearMC = valueAtEndOfYearMC * exchangeRateEndOfYear;
        }
      }
    }

  }

}
