package grafioschtrader.reportviews.securitydividends;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import grafiosch.common.DataHelper;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.MapGroup;
import grafioschtrader.reportviews.SecurityCostGroup;
import grafioschtrader.types.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a single year within the dividends report, containing all dividend and interest data for that calendar
 * year.
 * 
 * <p>
 * This class aggregates all financial transactions, positions, and valuations for a specific year, including dividend
 * income, interest earnings, fees, taxes, and year-end portfolio valuations. It maintains both security positions and
 * cash account positions, with all monetary values converted to the tenant's main currency.
 * </p>
 */
@Schema(description = "Represents a single year's dividend and interest data within the comprehensive dividends report")
public class SecurityDividendsYearGroup extends MapGroup<Integer, SecurityDividendsPosition> {

  @Schema(description = "The calendar year this group represents")
  public Integer year;

  @Schema(description = "Total market value of all security positions at the end of the year in main currency")
  public double valueAtEndOfYearMC;

  @Schema(description = "Total interest earned on cash accounts during the year in main currency")
  public double yearInterestMC;

  @Schema(description = "Total fees paid during the year in main currency")
  public double yearFeeMC;

  @Schema(description = "Total automatically withheld taxes on dividends and interest during the year in main currency")
  public double yearAutoPaidTaxMC;

  @Schema(description = "Total taxable dividend and interest income during the year in main currency")
  public double yearTaxableAmountMC;

  @Schema(description = "Count of transactions that incurred fees during the year")
  public int yearCountPaidTransactions;

  @Schema(description = "Total net dividend and interest income actually received during the year in main currency")
  public double yearRealReceivedDivInterestMC;

  @Schema(description = "Security cost information and transaction fee analysis for the year")
  public SecurityCostGroup securityCostGroup;

  @Schema(description = "Total ICTax tax value in CHF across all security positions for this year")
  public double yearIctaxTotalTaxValueChf;

  @Schema(description = "Total finance costs for all margin positions during the year in main currency")
  public double yearFinanceCostMC;

  @Schema(description = "Total ICTax payment income in CHF across all security positions for this year")
  public double yearIctaxTotalPaymentValueChf;

  /** Map of cash account positions by account ID */
  private Map<Integer, CashAccountPosition> cashaccountGroupMap = new HashMap<>();

  /** Precision for main currency calculations */
  protected int precisionMC;

  /** Map of currency precision settings by currency code */
  private Map<String, Integer> currencyPrecisionMap;

  /**
   * Creates a new year group for the dividends report.
   * 
   * @param year                 the calendar year this group represents
   * @param precisionMC          decimal precision for main currency calculations
   * @param currencyPrecisionMap map of precision settings by currency code
   */
  public SecurityDividendsYearGroup(Integer year, int precisionMC, Map<String, Integer> currencyPrecisionMap) {
    super(new HashMap<>());
    this.year = year;
    this.precisionMC = precisionMC;
    this.currencyPrecisionMap = currencyPrecisionMap;
    securityCostGroup = new SecurityCostGroup(precisionMC);
  }

  /**
   * Calculates total dividend and interest amounts by summing individual security position values.
   */
  public void calcDivInterest() {
    groupMap.values().forEach(securityDividendsPosition -> {
      if (securityDividendsPosition.valueAtEndOfYearMC != null) {
        valueAtEndOfYearMC += securityDividendsPosition.valueAtEndOfYearMC;
      }
    });
  }

  @Override
  protected SecurityDividendsPosition createInstance(Integer key) {
    return new SecurityDividendsPosition(precisionMC, currencyPrecisionMap);
  }

  public List<SecurityDividendsPosition> getSecurityDividendsPositions() {
    return groupMap.values().stream().sorted((x, y) -> x.security.getName().compareTo(y.security.getName()))
        .collect(Collectors.toList());
  }

  /**
   * Returns all cash account positions for this year, sorted alphabetically by account name.
   * 
   * @return list of cash account positions sorted by name
   */
  public List<CashAccountPosition> getCashAccountPositions() {
    return cashaccountGroupMap.values().stream()
        .sorted((x, y) -> x.cashaccount.getName().compareTo(y.cashaccount.getName())).collect(Collectors.toList());
  }

  /**
   * Gets or creates a security dividend position for the specified security within this year.
   * 
   * @param security          the security to get or create a position for
   * @param securitysplitList list of security splits for calculating split factors
   * @return the security dividend position for the specified security
   */
  public SecurityDividendsPosition getOrCreateSecurityDividendsPosition(Security security,
      List<Securitysplit> securitysplitList) {
    SecurityDividendsPosition securityDividendsPosition = this.getOrCreateGroup(security.getIdSecuritycurrency());
    securityDividendsPosition.security = security;
    if (securitysplitList != null && securityDividendsPosition.splitFactorAfter == null) {
      securityDividendsPosition.splitFactorAfter = Securitysplit.calcSplitFatorForFromDate(securitysplitList,
          LocalDate.of(year, 12, 31));
    }
    return securityDividendsPosition;
  }

  /**
   * Gets or creates a cash account position for the specified cash account within this year.
   * 
   * @param cashaccount the cash account to get or create a position for
   * @return the cash account position for the specified account
   */
  public CashAccountPosition getOrCreateAccountDividendPosition(Cashaccount cashaccount) {
    return cashaccountGroupMap.computeIfAbsent(cashaccount.getIdSecuritycashAccount(),
        k -> new CashAccountPosition(cashaccount, precisionMC, currencyPrecisionMap));
  }

  /**
   * Attaches historical quotes for year-end valuations and calculates position totals.
   * 
   * <p>
   * This method processes both security positions and cash account positions, applying year-end exchange rates for
   * accurate portfolio valuations and aggregating annual totals for dividends, interest, taxes, and transaction costs.
   * </p>
   * 
   * @param historyquoteYearIdMap map of historical quotes by year for portfolio valuations
   * @param dateCurrencyMap       currency conversion data for calculations
   */
  public void attachHistoryquoteAndCalcPositionTotal(Map<Integer, Map<Integer, Historyquote>> historyquoteYearIdMap,
      DateTransactionCurrencypairMap dateCurrencyMap) {

    groupMap.values().forEach(securityDividendsPosition -> {
      securityDividendsPosition.attachHistoryquoteAndCalcPositionTotal(historyquoteYearIdMap.get(year),
          dateCurrencyMap);
      yearCountPaidTransactions += securityDividendsPosition.countPaidTransactions;
      yearRealReceivedDivInterestMC += securityDividendsPosition.realReceivedDivInterestMC;
      yearAutoPaidTaxMC += securityDividendsPosition.autoPaidTaxMC;
      yearTaxableAmountMC += securityDividendsPosition.taxableAmountMC;
    });
    distributeMarginDataToCashAccounts(historyquoteYearIdMap.get(year), dateCurrencyMap);
    cashaccountGroupMap.values().forEach(cashAccountPosition -> {
      cashAccountPosition.attachHistoryquoteAndCalcPositionTotal(historyquoteYearIdMap.get(year), dateCurrencyMap);
    });
    cashaccountGroupMap.values().forEach(cap -> {
      cap.cashBalancePlusMarginMC = cap.cashBalanceMC + cap.marginEarningsMC + cap.hypotheticalFinanceCostMC;
    });
    securityCostGroup.calcAverages(yearCountPaidTransactions);
  }

  /**
   * Fills the year with open security positions that had no transactions during this year.
   * 
   * <p>
   * This method ensures that securities with open positions appear in the year group even when no buy/sell or dividend
   * transactions occurred. It applies security split adjustments to maintain accurate unit counts across the year.
   * </p>
   * 
   * @param unitsCounterBySecurityMap map of current unit holdings by security
   * @param securitysplitMap          map of security splits for position adjustments
   */
  public void fillYearWithOpenPositions(Map<Integer, UnitsCounter> unitsCounterBySecurityMap,
      final Map<Integer, List<Securitysplit>> securitysplitMap,
      final Map<Integer, Map<Integer, MarginTracker>> marginOpenTransaction) {
    for (Map.Entry<Integer, UnitsCounter> entry : unitsCounterBySecurityMap.entrySet()) {
      SecurityDividendsPosition securityDividendsPosition = groupMap.get(entry.getKey());
      if (securityDividendsPosition == null && entry.getValue().units != 0) {
        securityDividendsPosition = this.getOrCreateSecurityDividendsPosition(entry.getValue().security,
            securitysplitMap.get(entry.getValue().security.getIdSecuritycurrency()));
      }
      if (securityDividendsPosition != null) {
        securityDividendsPosition.unitsAtEndOfYear = entry.getValue().units;
        securityDividendsPosition.unitsCounter = entry.getValue();
        if (securityDividendsPosition.security.isMarginInstrument()) {
          Map<Integer, MarginTracker> securityMarginOpen = marginOpenTransaction
              .get(securityDividendsPosition.security.getId());
          securityDividendsPosition.marginOpenPositions = securityMarginOpen;
        }
      }
    }
  }

  /**
   * Adds interest income to the yearly total.
   * 
   * @param interestMC interest amount in main currency to add
   */
  public void addInterest(Double interestMC) {
    if (interestMC != null) {
      yearInterestMC += interestMC;
    }
  }

  public double getValueAtEndOfYearMC() {
    return DataHelper.round(valueAtEndOfYearMC, precisionMC);
  }

  public double getYearInterestMC() {
    return DataHelper.round(yearInterestMC, precisionMC);
  }

  public double getYearFeeMC() {
    return DataHelper.round(yearFeeMC, precisionMC);
  }

  public double getYearAutoPaidTaxMC() {
    return DataHelper.round(yearAutoPaidTaxMC, precisionMC);

  }

  public double getYearIctaxTotalTaxValueChf() {
    return DataHelper.round(yearIctaxTotalTaxValueChf, precisionMC) ;
  }

  public double getYearIctaxTotalPaymentValueChf() {
    return DataHelper.round(yearIctaxTotalPaymentValueChf, precisionMC) ;
  }

  public double getYearTaxableAmountMC() {
    return DataHelper.round(yearTaxableAmountMC, precisionMC);
  }

  public double getYearRealReceivedDivInterestMC() {
    return DataHelper.round(yearRealReceivedDivInterestMC, precisionMC);
  }

  public double getYearFinanceCostMC() {
    return DataHelper.round(yearFinanceCostMC, precisionMC);
  }

  /**
   * Distributes margin earnings and hypothetical finance costs from open margin positions to their corresponding cash
   * account positions. This allows users to see margin-related income aggregated at the cash account level for tax
   * reporting.
   *
   * @param historyquoteIdMap map of security currency IDs to their year-end historical quotes
   * @param dateCurrencyMap   currency pair mapping for exchange rate lookups
   */
  private void distributeMarginDataToCashAccounts(Map<Integer, Historyquote> historyquoteIdMap,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    LocalDate endDate = LocalDate.now().getYear() == year ? LocalDate.now() : LocalDate.of(year, 12, 31);

    for (SecurityDividendsPosition sdp : groupMap.values()) {
      if (sdp.marginOpenPositions == null || sdp.marginOpenPositions.isEmpty()) {
        continue;
      }
      Double exchangeRate = sdp.exchangeRateEndOfYear;
      double exRate = exchangeRate != null ? exchangeRate : 1.0;
      Historyquote hq = historyquoteIdMap != null ? historyquoteIdMap.get(sdp.security.getIdSecuritycurrency()) : null;

      for (MarginTracker mt : sdp.marginOpenPositions.values()) {
        int cashAccountId = mt.transaction.getCashaccount().getIdSecuritycashAccount();
        CashAccountPosition cap = cashaccountGroupMap.get(cashAccountId);
        if (cap == null) {
          continue;
        }

        // Margin earnings: per-position unrealized P&L
        if (hq != null) {
          double shortFactor = mt.transaction.getTransactionType() == TransactionType.ACCUMULATE ? 1 : -1;
          double adjustedOpenPrice = mt.transaction.getQuotation() / mt.splitFactorSinceOpen;
          double unrealizedPL = (hq.getClose() - adjustedOpenPrice) * Math.abs(mt.openUnits) * shortFactor
              * mt.transaction.getValuePerPoint();
          cap.marginEarningsMC += unrealizedPL * exRate;
        }

        // Hypothetical finance cost
        if (mt.transaction.getAssetInvestmentValue1() != null && mt.transaction.getAssetInvestmentValue1() != 0.0) {
          long daysRemaining = ChronoUnit.DAYS.between(mt.lastFinanceCostDate, endDate);
          if (daysRemaining > 0) {
            double dailyCost = mt.transaction.getAssetInvestmentValue1();
            double units = Math.abs(mt.openUnits);
            cap.hypotheticalFinanceCostMC -= dailyCost * units * daysRemaining * exRate;
          }
        }
      }
    }
  }

  public static class MarginTracker {
    public Transaction transaction;
    public double openUnits;
    public double splitFactorSinceOpen = 1.0;
    public LocalDate lastFinanceCostDate;

    public MarginTracker(Transaction transaction, double openUnits) {
      this.transaction = transaction;
      this.openUnits = openUnits;
      this.lastFinanceCostDate = transaction.getTransactionTime().toLocalDate();
    }

    public void applySplitFactor(double factor) {
      openUnits *= factor;
      splitFactorSinceOpen *= factor;
    }

    @Override
    public String toString() {
      return "MarginTracker [openUnits=" + openUnits + ", splitFactorSinceOpen=" + splitFactorSinceOpen + "]";
    }
  }
}
