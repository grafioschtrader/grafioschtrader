package grafioschtrader.reports.dataverification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.instrument.SecurityCalcService;
import grafioschtrader.reports.ReportHelper;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.IPositionCloseOnLatestPrice;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.TransactionType;

/**
 * Read-only evidence run for the currency gain/loss rework described in
 * {@code specification/Currency_Gain_Loss_Attribution.md}.
 *
 * <p>
 * The test does not assert; it prints a report that is meant to be read. It walks every security position of every
 * tenant of the live database at several reporting dates, replays the monetary flows of each position independently of
 * the position accumulators, and checks the attribution against that replay.
 * </p>
 *
 * <p>
 * The run that motivated the rework is recorded in §7.1 of the specification: 1783 of 4571 positions violated the
 * identity below, for 11.5 million francs of absolute error. The same harness now verifies the replacement.
 * </p>
 *
 * <h4>What each check answers</h4>
 * <ol>
 * <li><b>A — replay agreement.</b> Does {@code B}, {@code S}, {@code D}, {@code V} as enumerated here reproduce the
 * {@code gainLossSecurity} the production calculation arrives at? It uses no exchange rate at all, so it validates the
 * flow enumeration on its own; a deviation is a finding about the replay, not about the model, and the later checks
 * are meaningless without it.</li>
 * <li><b>B — the identity.</b> {@code gainLossSecurityMC + gainLossCurrencyMC} against the true main currency result.
 * The left side comes from {@code SecurityGeneralCalc}, the right side from the replay, so this is a genuine
 * cross-check of the implementation and not an algebraic tautology.</li>
 * <li><b>C — per-transaction shares.</b> They must sum to the position total.</li>
 * <li><b>D — main currency instruments.</b> A security denominated in the main currency must report exactly zero.
 * This is the guard against the ambiguous rate helper of specification §4.9.</li>
 * <li><b>E — non-finite values.</b> The replaced calculation divided by an accumulator that reached zero; the
 * replacement has no division, so this must stay at zero.</li>
 * </ol>
 *
 * <p>
 * <b>{@code @ActiveProfiles("prod")} is not optional.</b> Without an explicit profile the context boots the test
 * bootstrap and {@code V1__schema.sql} drops every table of the target database. Nothing here opens a write path.
 * </p>
 */
@SpringBootTest(classes = GTforTest.class)
@ActiveProfiles("prod")
@Transactional
class CurrencyGainLossAttributionTest {

  /** Reporting dates to sweep. A single recent date plus a few older ones exposes the rate-date mismatch. */
  private static final List<LocalDate> REPORT_DATES = List.of(LocalDate.now().minusDays(1),
      LocalDate.of(2024, 6, 28), LocalDate.of(2022, 6, 30), LocalDate.of(2020, 6, 30));

  /** Below this many main currency units a deviation is rounding, not model error. */
  private static final double TOLERANCE = 0.05;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private SecurityCalcService securityCalcService;

  @Test
  void reportCurrencyGainLossAttribution() {
    final Census census = new Census();
    for (final Tenant tenant : tenantJpaRepository.findAll()) {
      for (final LocalDate reportDate : REPORT_DATES) {
        reportTenant(tenant, reportDate, census);
      }
    }
    census.print();
  }

  private void reportTenant(final Tenant tenant, final LocalDate reportDate, final Census census) {
    final Map<Integer, List<Transaction>> transactionsBySecurity = loadTransactionsBySecurity(tenant.getIdTenant());
    if (transactionsBySecurity.isEmpty()) {
      return;
    }
    final DateTransactionCurrencypairMap dateCurrencyMap = buildDateCurrencyMap(tenant, reportDate);
    ReportHelper.loadUntilDateHistoryquotes(tenant.getIdTenant(), historyquoteJpaRepository, dateCurrencyMap);

    for (final Map.Entry<Integer, List<Transaction>> entry : transactionsBySecurity.entrySet()) {
      final List<Transaction> transactions = entry.getValue();
      final Security security = transactions.getFirst().getSecurity();
      if (transactions.getFirst().getTransactionTime().toLocalDate().isAfter(reportDate)) {
        continue;
      }
      try {
        reportPosition(tenant, security, transactions, dateCurrencyMap, reportDate, census);
      } catch (final RuntimeException e) {
        census.failed++;
        System.err.printf("%s %s %s -> %s: %s%n", reportDate, tenant.getIdTenant(), label(security),
            e.getClass().getSimpleName(), e.getMessage());
      }
    }
  }

  private void reportPosition(final Tenant tenant, final Security security, final List<Transaction> transactions,
      final DateTransactionCurrencypairMap dateCurrencyMap, final LocalDate reportDate, final Census census) {

    final Map<Integer, List<Securitysplit>> splitMap = securitysplitJpaRepository
        .getSecuritysplitMapByIdSecuritycurrency(security.getIdSecuritycurrency());
    final SecurityTransactionSummary summary = new SecurityTransactionSummary(security, tenant.getCurrency(),
        globalparametersService.getCurrencyPrecision());

    securityCalcService.calcTransactions(security, tenant.isExcludeDivTax(), summary, splitMap, transactions,
        reportDate, dateCurrencyMap);
    final SecurityPositionSummary sps = summary.securityPositionSummary;
    if (sps.units == 0.0 && summary.transactionPositionList.isEmpty()) {
      return;
    }

    // Close the open position exactly as SecruityTransactionsReport does, so the unrealised leg is present.
    securityJpaRepository.calcGainLossBasedOnDateOrNewestPrice(sps,
        (IPositionCloseOnLatestPrice<Security, SecurityPositionSummary>) (positionSummary,
            lastPrice) -> securityCalcService.createHypotheticalSellTransaction(positionSummary, lastPrice, splitMap,
                dateCurrencyMap, summary),
        reportDate);
    if (sps.priceMissing) {
      census.priceMissing++;
      return;
    }

    // calcMainCurrency zeroes valueSecurity for pseudo instruments, so capture it first.
    final double valueSecurity = sps.valueSecurity;

    final Double reportRate = reportRate(security.getCurrency(), dateCurrencyMap);
    if (reportRate == null) {
      census.noReportRate++;
      return;
    }
    sps.calcMainCurrency(reportRate);
    summary.applyReportRate(reportRate);

    final Replay replay = replay(transactions, tenant.isExcludeDivTax(), dateCurrencyMap, reportDate);
    if (replay.unhandled > 0 || security.isMarginInstrument()) {
      // A margin position closes an individual lot against its opening quotation, so its cash flow is not the
      // buy/sell pair this replay models. FINANCE_COST likewise falls through the general switch.
      census.notReplayed++;
      census.marginWithFx += sps.gainLossCurrencyMC != 0.0 ? 1 : 0;
      return;
    }

    census.total++;
    if (replay.mainCurrencyFlowsWithForeignRate > 0) {
      census.mainCurrencyRateAbuse++;
    }

    // A — does the replay see the same flows the production calculation saw?
    final double replayedGainLossSecurity = replay.proceeds + replay.income + valueSecurity - replay.cost;
    final double deviationA = replayedGainLossSecurity - sps.gainLossSecurity;
    if (Math.abs(deviationA) > TOLERANCE) {
      census.replayMismatch++;
      census.countMismatchCause(security, transactions, splitMap);
      census.report(reportDate, tenant, security, "A replay", deviationA,
          "prod=%.2f replay=%.2f | B=%.2f S=%.2f D=%.2f V=%.2f units=%.4f acb=%.2f tx=%d cause=%s".formatted(
              sps.gainLossSecurity, replayedGainLossSecurity, replay.cost, replay.proceeds, replay.income,
              valueSecurity, sps.units, sps.adjustedCostBase, transactions.size(),
              mismatchCause(security, transactions, splitMap)));
      return;
    }

    final double netInvestedMC = replay.costMC - replay.proceedsMC - replay.incomeMC;
    final double trueResult = valueSecurity * reportRate - netInvestedMC;

    // B — the production attribution must close the identity exactly. Independent of the implementation: the left
    // side comes out of SecurityGeneralCalc, the right side out of the replay above.
    final double deviationB = sps.gainLossSecurityMC + sps.gainLossCurrencyMC - trueResult;
    if (Math.abs(deviationB) > TOLERANCE) {
      census.identityBroken++;
      census.sumAbsError += Math.abs(deviationB);
      census.maxAbsError = Math.max(census.maxAbsError, Math.abs(deviationB));
      census.report(reportDate, tenant, security, "B identity", deviationB,
          "gainLossSecurityMC=%.2f gainLossCurrencyMC=%.2f true=%.2f".formatted(sps.gainLossSecurityMC,
              sps.gainLossCurrencyMC, trueResult));
    }

    // C — the per-transaction shares must add up to the position total.
    final double perTransactionSum = summary.transactionPositionList.stream()
        .map(position -> position.transactionGainLossCurrencyMC).filter(java.util.Objects::nonNull)
        .mapToDouble(Double::doubleValue).sum();
    final double deviationC = perTransactionSum - sps.gainLossCurrencyMC;
    if (Math.abs(deviationC) > TOLERANCE) {
      census.perTransactionBroken++;
      census.report(reportDate, tenant, security, "C perTx", deviationC,
          "sum=%.2f position=%.2f".formatted(perTransactionSum, sps.gainLossCurrencyMC));
    }

    // D — a security denominated in the main currency can have no currency result at all.
    if (security.getCurrency().equals(tenant.getCurrency()) && sps.gainLossCurrencyMC != 0.0) {
      census.mainCurrencyNonZero++;
      census.report(reportDate, tenant, security, "D mainCcy", sps.gainLossCurrencyMC, "");
    }

    if (!Double.isFinite(sps.gainLossCurrencyMC)) {
      census.nonFinite++;
      census.report(reportDate, tenant, security, "E nonfinite", sps.gainLossCurrencyMC, "");
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Independent replay
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * Sums the monetary flows of a position straight from the transaction rows, without using any of the position
   * accumulators of the production calculation.
   *
   * <p>
   * Split factors deliberately play no part: a split changes the unit count, never the cash that was paid or received,
   * and every amount here is a cash amount. Accrued interest is synthesised the same way
   * {@code SecurityGeneralCalc.createAccruedInterestPostion()} does, because those rows exist only at calculation time
   * and would otherwise be missing from the income leg.
   * </p>
   */
  private Replay replay(final List<Transaction> transactions, final boolean excludeDivTaxcost,
      final DateTransactionCurrencypairMap dateCurrencyMap, final LocalDate untilDate) {
    final Replay replay = new Replay();
    for (final Transaction transaction : transactions) {
      // calcTransactions() stops at the first transaction after the reporting date rather than filtering, so a later
      // row ends the walk even if an earlier-dated one follows. Mirror that exactly or the sums cannot be compared.
      if (transaction.getTransactionTime().toLocalDate().isAfter(untilDate)) {
        break;
      }
      final Double rate = DataBusinessHelper.getCurrencyExchangeRateToMainCurreny(transaction, dateCurrencyMap);
      // A security already denominated in the main currency has a rate of 1.0 by definition. The helper returns the
      // transaction's cash-account cross rate instead whenever a currency pair is present, so a CHF instrument paying
      // into a JPY account comes back with 85.65. Count it, then use the rate the definition demands.
      if (transaction.getSecurity().getCurrency().equals(dateCurrencyMap.getMainCurrency()) && rate != null
          && rate != 1.0) {
        replay.mainCurrencyFlowsWithForeignRate++;
      }
      final double r = rate == null || transaction.getSecurity().getCurrency().equals(dateCurrencyMap.getMainCurrency())
          ? 1.0
          : rate;
      final double netPrice = transaction.getSeucritiesNetPrice();
      final double taxCost = transaction.getTaxCost() != null ? transaction.getTaxCost() : 0.0;
      final double transactionCost = transaction.getTransactionCost() != null ? transaction.getTransactionCost() : 0.0;

      switch (transaction.getTransactionType()) {
      case ACCUMULATE -> {
        replay.cost += netPrice + taxCost + transactionCost;
        replay.costMC += (netPrice + taxCost + transactionCost) * r;
        addAccruedInterest(transaction, r, replay);
      }
      case REDUCE -> {
        replay.proceeds += netPrice - taxCost - transactionCost;
        replay.proceedsMC += (netPrice - taxCost - transactionCost) * r;
        addAccruedInterest(transaction, r, replay);
      }
      case DIVIDEND -> {
        final double net = netPrice - (excludeDivTaxcost ? 0.0 : taxCost) - transactionCost;
        replay.income += net;
        replay.incomeMC += net * r;
      }
      case ACCRUED_INTEREST -> {
        // createCalcTransactionPos() populates ctp.taxCost / ctp.transactionCost only for ACCUMULATE, REDUCE,
        // HYPOTHETICAL_SELL and DIVIDEND, so an accrued-interest row reaches calcDividend with both at zero.
        replay.income += netPrice;
        replay.incomeMC += netPrice * r;
      }
      default -> replay.unhandled++;
      }
    }
    return replay;
  }

  /**
   * Mirrors {@code SecurityGeneralCalc.createAccruedInterestTransaction()}: the synthetic row carries
   * {@code assetInvestmentValue1}, negative when interest was paid on a purchase, and no tax or cost of its own.
   */
  private void addAccruedInterest(final Transaction transaction, final double rate, final Replay replay) {
    final AssetclassType categoryType = transaction.getSecurity().getAssetClass().getCategoryType();
    if (categoryType != AssetclassType.FIXED_INCOME && categoryType != AssetclassType.CONVERTIBLE_BOND) {
      return;
    }
    if (transaction.getAssetInvestmentValue1() == null || transaction.getAssetInvestmentValue1() == 0.0) {
      return;
    }
    final double amount = transaction.getAssetInvestmentValue1()
        * (transaction.getTransactionType() == TransactionType.ACCUMULATE ? -1.0 : 1.0);
    replay.income += amount;
    replay.incomeMC += amount * rate;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Context helpers
  // ---------------------------------------------------------------------------------------------------------------

  private Map<Integer, List<Transaction>> loadTransactionsBySecurity(final Integer idTenant) {
    final Map<Integer, List<Transaction>> bySecurity = new LinkedHashMap<>();
    transactionJpaRepository.findByIdTenantOrderByTransactionTimeDesc(idTenant).stream()
        .filter(transaction -> transaction.getSecurity() != null)
        .sorted(Comparator.comparing(Transaction::getTransactionTime))
        .forEach(transaction -> bySecurity
            .computeIfAbsent(transaction.getSecurity().getIdSecuritycurrency(), _ -> new ArrayList<>())
            .add(transaction));
    return bySecurity;
  }

  private DateTransactionCurrencypairMap buildDateCurrencyMap(final Tenant tenant, final LocalDate reportDate) {
    final List<Object[]> dateTransactionCurrency = historyquoteJpaRepository
        .getHistoryquotesForAllForeignTransactionsByIdTenant(tenant.getIdTenant());
    final List<Currencypair> currencypairs = currencypairJpaRepository
        .getAllCurrencypairsByTenantInPortfolioAndAccounts(tenant.getIdTenant());
    return new DateTransactionCurrencypairMap(tenant.getCurrency(), reportDate, dateTransactionCurrency, currencypairs,
        tradingDaysPlusJpaRepository.hasTradingDayBetweenUntilYesterday(reportDate));
  }

  /**
   * The reporting rate, following {@code SecurityCashaccountGroupByCurrencyBaseReport.getCurrencyExChangeRate()} but
   * returning null instead of throwing, so one unpriced currency pair does not end the sweep.
   */
  private Double reportRate(final String currency, final DateTransactionCurrencypairMap dateCurrencyMap) {
    if (currency.equals(dateCurrencyMap.getMainCurrency())) {
      return 1.0;
    }
    final Currencypair currencypair = dateCurrencyMap.getCurrencypairByFromCurrency(currency);
    if (currencypair == null) {
      return null;
    }
    if (dateCurrencyMap.isUntilDateEqualNowOrAfterOrInActualWeekend()) {
      return currencypair.getSLast();
    }
    final Double exact = dateCurrencyMap.getExactDateAndFromCurrency(dateCurrencyMap.getUntilDate(), currency);
    return exact != null ? exact : currencypair.getSLast();
  }

  private static String label(final Security security) {
    return "%s/%s (%d)".formatted(security.getName(), security.getCurrency(), security.getIdSecuritycurrency());
  }

  /**
   * Names the plausible reasons why the replay of a position disagrees with {@code gainLossSecurity}, so the 216
   * deviations can be read as a small number of classes rather than as a list.
   */
  private static String mismatchCause(final Security security, final List<Transaction> transactions,
      final Map<Integer, List<Securitysplit>> splitMap) {
    final List<String> causes = new ArrayList<>();
    if (security.isMarginInstrument()) {
      causes.add("margin");
    }
    final List<Securitysplit> splits = splitMap.get(security.getIdSecuritycurrency());
    if (splits != null && !splits.isEmpty()) {
      causes.add("splits=" + splits.size());
    }
    final AssetclassType categoryType = security.getAssetClass().getCategoryType();
    if (categoryType == AssetclassType.FIXED_INCOME || categoryType == AssetclassType.CONVERTIBLE_BOND) {
      causes.add("bond");
    }
    if (transactions.stream().anyMatch(t -> t.getAssetInvestmentValue1() != null
        && t.getAssetInvestmentValue1() != 0.0)) {
      causes.add("accruedInterest");
    }
    if (transactions.stream().anyMatch(t -> t.getTransactionType() == TransactionType.HYPOTHETICAL_BUY
        || t.getTransactionType() == TransactionType.HYPOTHETICAL_SELL)) {
      causes.add("hypotheticalStored");
    }
    if (transactions.stream().anyMatch(t -> t.getIdSecurityTransfer() != null)) {
      causes.add("securityTransfer");
    }
    return causes.isEmpty() ? "none" : String.join("+", causes);
  }

  // ---------------------------------------------------------------------------------------------------------------

  /** Rate-weighted monetary flows of one position, in security currency and in main currency. */
  private static final class Replay {
    double cost;
    double costMC;
    double proceeds;
    double proceedsMC;
    double income;
    double incomeMC;
    /** Transaction types this replay does not model, above all the margin cash-flow shape. */
    int unhandled;
    /** Flows of a main-currency instrument for which the rate helper returned a foreign cross rate. */
    int mainCurrencyFlowsWithForeignRate;
  }

  private final class Census {
    int total;
    int failed;
    int priceMissing;
    int noReportRate;
    int notReplayed;
    int marginWithFx;
    int replayMismatch;
    int identityBroken;
    int perTransactionBroken;
    int mainCurrencyNonZero;
    int nonFinite;
    int mainCurrencyRateAbuse;
    double sumAbsError;
    double maxAbsError;
    final Map<String, Integer> mismatchCauses = new LinkedHashMap<>();

    void countMismatchCause(final Security security, final List<Transaction> transactions,
        final Map<Integer, List<Securitysplit>> splitMap) {
      mismatchCauses.merge(mismatchCause(security, transactions, splitMap), 1, Integer::sum);
    }

    void report(final LocalDate reportDate, final Tenant tenant, final Security security, final String check,
        final double deviation, final String detail) {
      System.out.printf("%-10s t%-3d %-8s %+14.2f  %-60s %s%n", reportDate, tenant.getIdTenant(), check, deviation,
          label(security), detail);
    }

    void print() {
      System.out.println("""

          =========================================================================
          Currency gain/loss attribution — evidence run
          =========================================================================""");
      System.out.printf("positions evaluated                              %6d%n", total);
      System.out.printf("  skipped, no price at reporting date            %6d%n", priceMissing);
      System.out.printf("  skipped, no reporting exchange rate            %6d%n", noReportRate);
      System.out.printf("  skipped, flow shape not replayed (margin etc.) %6d%n", notReplayed);
      System.out.printf("     of those, now reporting a currency result %6d%n", marginWithFx);
      System.out.printf("  threw                                          %6d%n", failed);
      System.out.println();
      System.out.printf("A  replay disagrees with gainLossSecurity        %6d%n", replayMismatch);
      mismatchCauses.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
          .forEach(e -> System.out.printf("     %-40s   %6d%n", e.getKey(), e.getValue()));
      System.out.printf("B  attribution breaks the identity              %6d   <- must be 0%n", identityBroken);
      System.out.printf("     sum of absolute error (main currency)      %14.2f%n", sumAbsError);
      System.out.printf("     largest single error                       %14.2f%n", maxAbsError);
      System.out.printf("C  per-transaction shares do not add up         %6d   <- must be 0%n",
          perTransactionBroken);
      System.out.printf("D  main-ccy instrument with non-zero FX         %6d   <- must be 0%n", mainCurrencyNonZero);
      System.out.printf("E  FX is NaN or Infinite                        %6d   <- must be 0%n", nonFinite);
      System.out.println();
      System.out.printf("F  positions exposed to the ambiguous rate      %6d   (guarded, see spec 4.9)%n",
          mainCurrencyRateAbuse);
      System.out.println("=========================================================================");
    }
  }
}
