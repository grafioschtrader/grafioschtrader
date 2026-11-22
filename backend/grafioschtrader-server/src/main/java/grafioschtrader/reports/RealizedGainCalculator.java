package grafioschtrader.reports;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.instrument.CostBasisMethods;
import grafioschtrader.instrument.SecurityCalcService;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.realizedgain.RealizedGainGrandTotal;
import grafioschtrader.reportviews.realizedgain.RealizedGainPosition;
import grafioschtrader.reportviews.realizedgain.RealizedGainYearGroup;
import grafioschtrader.reportviews.transaction.SecurityTransactionPosition;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.types.CostBasisMethod;
import grafioschtrader.types.TransactionType;

/**
 * Turns the transactions of a tenant into the realized gains per year. It calls the transaction calculation once for
 * every security, so the gain of a sale is the one every other view of the same cost basis method shows, and it only
 * selects, converts and groups the sales that calculation produced. It holds no state between calls and loads nothing.
 *
 * <p>
 * The security account filter is applied after the calculation. With FIFO and LIFO every security account keeps its own
 * ledger, so filtering earlier would not matter. With average cost all security accounts of a security share one pool,
 * and filtering earlier would change the gain of a sale.
 * </p>
 */
public class RealizedGainCalculator {

  private final SecurityCalcService securityCalcService;
  private final String mainCurrency;
  private final int precisionMC;
  private final Map<String, Integer> currencyPrecision;

  /**
   * @param securityCalcService the transaction calculation of a security
   * @param mainCurrency        main currency of the tenant
   * @param currencyPrecision   decimal places per currency, as the report views take them
   * @param precisionMC         decimal places of the main currency
   */
  public RealizedGainCalculator(SecurityCalcService securityCalcService, String mainCurrency,
      Map<String, Integer> currencyPrecision, int precisionMC) {
    this.securityCalcService = securityCalcService;
    this.mainCurrency = mainCurrency;
    this.currencyPrecision = currencyPrecision;
    this.precisionMC = precisionMC;
  }

  /**
   * Calculates the realized gains of all given securities.
   *
   * @param transactionsBySecurity the transactions of every security of the tenant, each list with all of its security
   *                               accounts
   * @param securitySplitMap       the splits by security id
   * @param untilDate              the last day whose transactions count, normally today
   * @param dateCurrencyMap        the exchange rates to the main currency
   * @param costBasisMethods       the methods the calculation applies
   * @param excludeDivTax          the tenant setting of the tax on dividends, passed on to the calculation
   * @param idsSecurityaccount     the security accounts whose sales are reported, null for all
   * @return the years, the newest first
   */
  public RealizedGainGrandTotal calculate(Map<Security, List<Transaction>> transactionsBySecurity,
      Map<Integer, List<Securitysplit>> securitySplitMap, LocalDate untilDate,
      DateTransactionCurrencypairMap dateCurrencyMap, CostBasisMethods costBasisMethods, boolean excludeDivTax,
      Set<Integer> idsSecurityaccount) {
    final Map<Integer, RealizedGainYearGroup> yearMap = new TreeMap<>(Comparator.reverseOrder());
    final Set<CostBasisMethod> usedMethods = EnumSet.noneOf(CostBasisMethod.class);
    var spinOff = securityCalcService.spinOffContext(transactionsBySecurity.values().stream().flatMap(List::stream)
        .findFirst().map(Transaction::getIdTenant).orElse(null), null);
    for (Map.Entry<Security, List<Transaction>> entry : transactionsBySecurity.entrySet()) {
      final SecurityTransactionSummary summary = new SecurityTransactionSummary(entry.getKey(), mainCurrency,
          currencyPrecision);
      securityCalcService.calcTransactions(entry.getKey(), excludeDivTax, summary, securitySplitMap, entry.getValue(),
          untilDate, dateCurrencyMap, costBasisMethods, spinOff);
      for (SecurityTransactionPosition position : summary.transactionPositionList) {
        if (isSale(position) && isSelected(idsSecurityaccount, position.transaction)) {
          final RealizedGainPosition row = toRow(position, dateCurrencyMap);
          yearMap.computeIfAbsent(row.transactionTime.getYear(), RealizedGainYearGroup::new).add(row);
          if (row.costBasisMethod != null) {
            usedMethods.add(row.costBasisMethod);
          }
        }
      }
    }
    return buildGrandTotal(yearMap, appliedMethod(usedMethods, costBasisMethods));
  }

  private RealizedGainGrandTotal buildGrandTotal(Map<Integer, RealizedGainYearGroup> yearMap, String appliedMethod) {
    final RealizedGainGrandTotal grandTotal = new RealizedGainGrandTotal(mainCurrency, appliedMethod);
    for (RealizedGainYearGroup yearGroup : yearMap.values()) {
      yearGroup.positions.sort(Comparator.comparing((RealizedGainPosition p) -> p.transactionTime)
          .thenComparing(p -> p.idTransaction, Comparator.nullsLast(Comparator.naturalOrder())));
      yearGroup.finish(precisionMC);
      grandTotal.add(yearGroup, precisionMC);
    }
    return grandTotal;
  }

  private static boolean isSelected(Set<Integer> idsSecurityaccount, Transaction transaction) {
    return idsSecurityaccount == null || idsSecurityaccount.contains(transaction.getIdSecurityaccount());
  }

  private static String appliedMethod(Set<CostBasisMethod> usedMethods, CostBasisMethods costBasisMethods) {
    if (usedMethods.size() > 1) {
      return RealizedGainGrandTotal.MIXED_METHODS;
    }
    return (usedMethods.isEmpty() ? costBasisMethods.tenantMethod() : usedMethods.iterator().next()).name();
  }

  /**
   * A sale of a security is a REDUCE. The close of a margin position is a purchase or a sale that is connected to its
   * opening. The synthetic accrued interest rows of the calculation and every income are no sales.
   */
  private static boolean isSale(SecurityTransactionPosition position) {
    final Transaction transaction = position.transaction;
    if (transaction.getSecurityActionRole() != null || position.transactionGainLoss == null
        || transaction.getTransactionType() == TransactionType.ACCRUED_INTEREST) {
      return false;
    }
    return transaction.getSecurity().isMarginInstrument() ? transaction.isMarginClosePosition()
        : transaction.getTransactionType() == TransactionType.REDUCE;
  }

  private RealizedGainPosition toRow(SecurityTransactionPosition position, DateTransactionCurrencypairMap currencyMap) {
    final Transaction transaction = position.transaction;
    final RealizedGainPosition row = new RealizedGainPosition();
    row.idTransaction = transaction.getIdTransaction();
    row.security = transaction.getSecurity();
    row.securityCurrency = transaction.getSecurity().getCurrency();
    row.idSecurityaccount = transaction.getIdSecurityaccount();
    row.transactionTime = transaction.getTransactionTime();
    row.units = transaction.getUnits();
    row.gainLoss = position.transactionGainLoss;
    row.gainLossPercentage = position.transactionGainLossPercentage;
    row.costBase = position.transactionCostBase;
    row.proceeds = row.costBase == null ? null : DataHelper.round(row.costBase + row.gainLoss, precisionOf(row));
    row.costBasisMethod = position.costBasisMethod;
    row.consumedLots = position.consumedLots;
    row.costBasisIncomplete = position.costBasisIncomplete;
    row.systemBooking = transaction.getIdSecurityTransfer() != null || transaction.getIdSecurityActionApp() != null;
    row.exchangeRate = saleRate(transaction, currencyMap);
    if (row.exchangeRate != null) {
      row.gainLossMC = DataHelper.round(row.gainLoss * row.exchangeRate, precisionMC);
    }
    return row;
  }

  private int precisionOf(RealizedGainPosition row) {
    return currencyPrecision.getOrDefault(row.securityCurrency, BaseConstants.FID_STANDARD_FRACTION_DIGITS);
  }

  /**
   * The rate from the currency of the security into the main currency on the day of the sale. A security in the main
   * currency has the rate 1. Settled over a cash account in the main currency, the booked rate of the transaction
   * counts. In every other case the closing rate of the security currency on the day counts, never the rate of the cash
   * account currency: that would convert an amount of another currency. The shared helper of the calculation returns
   * nothing or exactly that cash account rate for such sales and is therefore not used.
   *
   * @return the rate, null when none exists
   */
  static Double saleRate(Transaction transaction, DateTransactionCurrencypairMap currencyMap) {
    final String securityCurrency = transaction.getSecurity().getCurrency();
    if (securityCurrency.equals(currencyMap.getMainCurrency())) {
      return 1.0;
    }
    if (transaction.getCashaccount().getCurrency().equals(currencyMap.getMainCurrency())
        && transaction.getIdCurrencypair() != null && transaction.getCurrencyExRate() != null) {
      return transaction.getCurrencyExRate();
    }
    return currencyMap.getPriceByDateAndFromCurrency(transaction.getTransactionDate(), securityCurrency, false);
  }
}
