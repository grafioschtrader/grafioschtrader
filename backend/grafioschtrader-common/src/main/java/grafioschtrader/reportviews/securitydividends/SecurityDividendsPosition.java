package grafioschtrader.reportviews.securitydividends;

import java.util.HashMap;
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
 * Represents a security position within a specific year of the dividend report.
 * 
 * <p>
 * This class tracks all dividend-related data for a single security within a calendar year, including dividend income,
 * transaction costs, unit holdings, and year-end valuations. Each instance represents one security within one year
 * group of the comprehensive dividend report.
 * </p>
 * 
 * <p>
 * The position tracks both dividend transactions and buy/sell activities that affect the unit count. It handles
 * currency conversions, security splits, and year-end portfolio valuations to provide accurate reporting in the
 * tenant's main currency.
 * </p>
 */
@Schema(description = """
    Represents a security position for a specific year in the dividend report, containing all dividend income,
    transaction costs, and holdings data for that security-year combination""")
public class SecurityDividendsPosition extends AccountDividendPosition {

  @Schema(description = "The security entity this position represents")
  public Security security;

  @Schema(description = "Number of buy and sell transactions where transaction fees have been paid during the year")
  public int countPaidTransactions;

  @Schema(description = "Number of units/shares held at the end of the year after all transactions and stock splits")
  public double unitsAtEndOfYear = 0.0;

  /**
   * Indicates whether at least one accumulate (buy) or reduce (sell) transaction occurred during the year.
   * 
   * <p>
   * This flag is crucial for correct calculation of units at year-end. When true, the unit count is maintained through
   * transaction processing. When false, units must be calculated by applying stock split factors to the previous year's
   * ending units, as no direct transactions modified the position during this year.
   * </p>
   */
  @JsonIgnore
  public boolean hasAccumulateReduce = false;

  /**
   * Split adjustment factor for historical prices from the end of the year.
   * 
   * <p>
   * Historical year-end prices are adjusted by subsequent stock splits to maintain accurate valuation calculations.
   * This factor represents the cumulative split adjustments that must be applied to the original year-end price to get
   * the equivalent value in current terms.
   * </p>
   */
  @JsonIgnore
  public Double splitFactorAfter;


  
  /**
   * Creates a new security dividend position for the specified precision settings.
   * 
   * @param precisionMC          decimal precision for main currency calculations
   * @param currencyPrecisionMap map of precision settings by currency code
   */
  public SecurityDividendsPosition(int precisionMC, Map<String, Integer> currencyPrecisionMap) {
    super(precisionMC, currencyPrecisionMap);
  }

 
  
  
  /**
   * Updates the position with accumulate (buy) or reduce (sell) transaction data.
   * 
   * <p>
   * This method processes buy and sell transactions, calculating transaction costs and fees in the main currency. It
   * marks the position as having direct transactions and accumulates cost information at the year group level for
   * comprehensive reporting.
   * </p>
   * 
   * @param transaction                the buy or sell transaction to process
   * @param securityDividendsYearGroup the year group to update with cost information
   * @param dateCurrencyMap            currency conversion data for calculations
   */
  public void updateAccumulateReduce(Transaction transaction, SecurityDividendsYearGroup securityDividendsYearGroup,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    hasAccumulateReduce = true;
    if (transaction.getTransactionCost() != null && transaction.getTransactionCost() > 0.0) {
      SecurityCostPosition securityCostPosition = new SecurityCostPosition(precisionMC);
      transaction.calcCostTaxMaybeBasePrice(dateCurrencyMap.getMainCurrency(), securityCostPosition, dateCurrencyMap,
          false);
      securityDividendsYearGroup.securityCostGroup.sumPositionToGroupTotal(securityCostPosition);
      countPaidTransactions++;
    }
  }

  /**
   * Updates the position with dividend transaction data.
   * 
   * <p>
   * This method processes dividend payments, recording the unit count at the time of the dividend and calculating
   * currency conversions for accurate tax and income reporting. It determines the appropriate exchange rate based on
   * the transaction's cash account and security currencies.
   * </p>
   * 
   * @param transaction     the dividend transaction to process
   * @param dateCurrencyMap currency conversion data for calculations
   */
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
   * Calculates the market value of the security position at year-end using historical quotes.
   * 
   * <p>
   * This method determines the year-end portfolio valuation by applying historical
   * closing prices, stock split adjustments, and currency conversions. The calculation
   * only applies to positions with units held at year-end, ensuring accurate
   * portfolio valuations for reporting purposes.
   * </p>
   * 
   * <p>
   * The method handles:
   * </p>
   * <ul>
   * <li>Historical price lookup for the security</li>
   * <li>Stock split factor adjustments for price accuracy</li>
   * <li>Currency conversion to the tenant's main currency</li>
   * <li>Final position valuation calculation</li>
   * </ul>
   * 
   * @param historyquoteIdMap map of historical quotes by security ID for year-end prices
   * @param dateCurrencyMap currency conversion data for multi-currency calculations
   */
  public void attachHistoryquoteAndCalcPositionTotal(Map<Integer, Historyquote> historyquoteIdMap,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    if (unitsAtEndOfYear > 0) {
      getAndSetExchangeRateEndOfYear(historyquoteIdMap, dateCurrencyMap, security.getCurrency());
      var historyquote = historyquoteIdMap.get(security.getIdSecuritycurrency());
      if (historyquote != null) {
        closeEndOfYear = historyquote.getClose() * (splitFactorAfter == null ? 1.0 : splitFactorAfter);
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
