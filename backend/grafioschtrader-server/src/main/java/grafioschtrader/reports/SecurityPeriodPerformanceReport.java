package grafioschtrader.reports;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.common.DataHelper;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.performance.SecurityPeriodPerformance;
import grafioschtrader.reportviews.performance.SecurityPeriodPerformance.Totals;
import grafioschtrader.reportviews.performance.SecurityPeriodPerformanceRow;
import grafioschtrader.reportviews.performance.SecurityPeriodSeries;
import grafioschtrader.reportviews.performance.SecurityPeriodSeries.DailyValue;
import grafioschtrader.reportviews.performance.SecurityPeriodSeries.Flow;
import grafioschtrader.reportviews.performance.SecurityPeriodSeries.Input;
import grafioschtrader.reportviews.performance.SecurityPeriodSeries.Result;
import grafioschtrader.reportviews.performance.SecurityPeriodSeries.Span;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.service.CostBasisMethodService;
import grafioschtrader.service.GlobalparametersService;

//@formatter:off
/**
 * Period performance per security: for every security held or traded in a period of a client or a portfolio the
 * absolute gain and the time-weighted return, calculated on every request.
 *
 * <p>
 * Three sources are combined, see {@link SecurityPeriodSeries} for the calculation itself:
 * </p>
 * <ul>
 * <li>the daily values of the holding rows, valued like the period performance, so on every valued day the sum of the
 * securities equals its security total;</li>
 * <li>the flow of every transaction in security currency, read as the change of
 * {@link SecurityPositionSummary#positionFlow} that the transaction causes in one replay up to {@code dateTo};</li>
 * <li>the closing rates from each security currency into the main currency, which convert the flows.</li>
 * </ul>
 * <p>
 * Flows and holding rows are both keyed on the business date of a transaction. The queries run concurrently; the
 * replay stays on the request thread, because the position summaries and the lazily loaded transactions are bound to
 * it. Nothing is stored or cached.
 * </p>
 */
//@formatter:on
@Component
public class SecurityPeriodPerformanceReport {

  @Autowired
  private PerformanceReport performanceReport;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private PortfolioJpaRepository portfolioJpaRepository;

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @Autowired
  private SecurityPositionReplay securityPositionReplay;

  @Autowired
  private CostBasisMethodService costBasisMethodService;

  @Autowired
  private GlobalparametersService globalparametersService;

  /** What one scope contributes: its currency, accounts and the suppliers of its scope-specific queries. */
  private record Scope(Tenant tenant, String mainCurrency, List<Securityaccount> securityaccounts,
      Supplier<List<Object[]>> dailyValues, Supplier<List<Object[]>> spans,
      Supplier<List<Object[]>> foreignTransactionRates) {
  }

  /** The flows and the securities the replay of the scope has seen. */
  private record ReplayResult(Map<Integer, Security> securities, Map<Integer, List<Flow>> flows) {
  }

  /**
   * Calculates the period performance per security of a whole client in the tenant currency.
   *
   * @param idTenant  tenant of the request
   * @param localeStr user's locale for error messages
   * @param today     the client's calendar day, resolved on the request thread
   * @param dateFrom  excluded base date, a valuation day of the tenant
   * @param dateTo    last date, a valuation day of the tenant
   * @return one row per security held or traded in the period
   */
  @Transactional(readOnly = true)
  public SecurityPeriodPerformance getByTenant(Integer idTenant, String localeStr, LocalDate today,
      LocalDate dateFrom, LocalDate dateTo) throws Exception {
    performanceReport.validatePeriod(idTenant, null, localeStr, today, dateFrom, dateTo);
    Tenant tenant = tenantJpaRepository.getReferenceById(idTenant);
    List<Securityaccount> accounts = tenant.getPortfolioList().stream()
        .flatMap(portfolio -> portfolio.getSecurityaccountList().stream()).toList();
    Scope scope = new Scope(tenant, tenant.getCurrency(), accounts,
        () -> holdSecurityaccountSecurityJpaRepository.getDailySecurityValuesByTenant(idTenant, dateFrom, dateTo),
        () -> holdSecurityaccountSecurityJpaRepository.getHoldingSpansByTenant(idTenant, dateFrom, dateTo),
        () -> historyquoteJpaRepository.getHistoryquotesForAllForeignTransactionsByIdTenant(idTenant));
    return calculate(scope, today, dateFrom, dateTo);
  }

  /**
   * Calculates the period performance per security of one portfolio in the portfolio currency.
   *
   * @param idTenant    tenant of the request, which must own the portfolio
   * @param localeStr   user's locale for error messages
   * @param today       the client's calendar day, resolved on the request thread
   * @param idPortfolio the portfolio
   * @param dateFrom    excluded base date, a valuation day of the portfolio
   * @param dateTo      last date, a valuation day of the portfolio
   * @return one row per security held or traded in the period
   * @throws SecurityException if the portfolio does not belong to the tenant
   */
  @Transactional(readOnly = true)
  public SecurityPeriodPerformance getByPortfolio(Integer idTenant, String localeStr, LocalDate today,
      Integer idPortfolio, LocalDate dateFrom, LocalDate dateTo) throws Exception {
    performanceReport.validatePeriod(idTenant, idPortfolio, localeStr, today, dateFrom, dateTo);
    Portfolio portfolio = portfolioJpaRepository.findByIdTenantAndIdPortfolio(idTenant, idPortfolio);
    Tenant tenant = tenantJpaRepository.getReferenceById(idTenant);
    Scope scope = new Scope(tenant, portfolio.getCurrency(), portfolio.getSecurityaccountList(),
        () -> holdSecurityaccountSecurityJpaRepository.getDailySecurityValuesByPortfolio(idTenant, idPortfolio,
            dateFrom, dateTo),
        () -> holdSecurityaccountSecurityJpaRepository.getHoldingSpansByPortfolio(idTenant, idPortfolio, dateFrom,
            dateTo),
        () -> historyquoteJpaRepository.getHistoryquotesForAllForeignTransactionsByIdPortfolio(idPortfolio));
    return calculate(scope, today, dateFrom, dateTo);
  }

  private SecurityPeriodPerformance calculate(Scope scope, LocalDate today, LocalDate dateFrom, LocalDate dateTo) {
    final Integer idTenant = scope.tenant().getIdTenant();
    final boolean hasTradingDay = tradingDaysPlusJpaRepository.hasTradingDayBetweenUntilYesterday(dateTo, today);
    CompletableFuture<List<Object[]>> dailyValuesCF = CompletableFuture.supplyAsync(scope.dailyValues());
    CompletableFuture<List<Object[]>> spansCF = CompletableFuture.supplyAsync(scope.spans());
    CompletableFuture<List<Object[]>> foreignRatesCF = CompletableFuture.supplyAsync(scope.foreignTransactionRates());
    CompletableFuture<List<Currencypair>> pairsCF = CompletableFuture
        .supplyAsync(() -> currencypairJpaRepository.getAllCurrencypairsByTenantInPortfolioAndAccounts(idTenant));
    CompletableFuture<List<LocalDate>> tradingDaysCF = CompletableFuture.supplyAsync(() -> tradingDaysPlusJpaRepository
        .findByTradingDateBetweenOrderByTradingDate(dateFrom, dateTo).stream().map(t -> t.getTradingDate()).toList());

    DateTransactionCurrencypairMap dateCurrencyMap = new DateTransactionCurrencypairMap(scope.mainCurrency(), dateTo,
        foreignRatesCF.join(), pairsCF.join(), hasTradingDay, true, today);
    ReplayResult replay = replay(scope, dateCurrencyMap, dateFrom, dateTo);

    Map<Integer, List<Span>> spans = spans(spansCF.join());
    // Every held security has transactions in the accounts of the scope, so the replay knows each of them
    Set<Integer> population = new LinkedHashSet<>(spans.keySet());
    population.addAll(replay.flows().keySet());
    population.retainAll(replay.securities().keySet());
    return buildResult(scope.mainCurrency(), dateFrom, dateTo, tradingDaysCF.join(), population,
        dailyValues(dailyValuesCF.join()), spans, replay);
  }

  /** Calculates one row per security of the population, the totals, and sorts the rows. */
  private SecurityPeriodPerformance buildResult(String mainCurrency, LocalDate dateFrom, LocalDate dateTo,
      List<LocalDate> tradingDays, Set<Integer> population, Map<Integer, List<DailyValue>> dailyValues,
      Map<Integer, List<Span>> spans, ReplayResult replay) {
    Map<String, Map<LocalDate, Double>> rates = rates(population, replay.securities(), mainCurrency, dateFrom, dateTo);
    List<SecurityPeriodPerformanceRow> rows = new ArrayList<>();
    double[] totals = new double[5];
    int omittedRows = 0;
    int precisionMC = globalparametersService.getPrecisionForCurrency(mainCurrency);
    for (Integer idSecuritycurrency : population) {
      Security security = replay.securities().get(idSecuritycurrency);
      Result result = SecurityPeriodSeries.calculate(new Input(dateFrom, dateTo, tradingDays,
          security.isMarginInstrument(), dailyValues.getOrDefault(idSecuritycurrency, List.of()),
          spans.getOrDefault(idSecuritycurrency, List.of()), replay.flows().getOrDefault(idSecuritycurrency, List.of()),
          rateOfDay(security.getCurrency(), mainCurrency, rates)));
      rows.add(toRow(security, result, precisionMC));
      omittedRows += result.gainPeriodMC() == null ? 1 : 0;
      addToTotals(totals, result);
    }
    rows.sort(Comparator
        .comparing(SecurityPeriodPerformanceRow::gainPeriodMC, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(SecurityPeriodPerformanceRow::name, String.CASE_INSENSITIVE_ORDER));
    return new SecurityPeriodPerformance(mainCurrency, dateFrom, dateTo, rows,
        new Totals(DataHelper.round(totals[0], precisionMC), DataHelper.round(totals[1], precisionMC),
            DataHelper.round(totals[2], precisionMC), DataHelper.round(totals[3], precisionMC),
            DataHelper.round(totals[4], precisionMC)),
        omittedRows);
  }

  /**
   * Walks every security transaction of the scope booked up to {@code dateTo} and records, for each one booked after
   * {@code dateFrom}, the change of {@code positionFlow} it causes. The value is captured as a plain number, because
   * the summary keeps changing with the following transactions.
   */
  private ReplayResult replay(Scope scope, DateTransactionCurrencypairMap dateCurrencyMap, LocalDate dateFrom,
      LocalDate dateTo) {
    Map<Security, SecurityPositionSummary> summaries = new HashMap<>();
    Map<Security, Double> flowSoFar = new HashMap<>();
    Map<Integer, List<Flow>> flows = new HashMap<>();
    securityPositionReplay.replay(scope.securityaccounts(), summaries, scope.tenant().isExcludeDivTax(),
        dateCurrencyMap, costBasisMethodService.forTenant(scope.tenant().getIdTenant()),
        transaction -> !transaction.getTransactionDate().isAfter(dateTo), null,
        (transaction, summary) -> recordFlow(transaction, summary, flowSoFar, flows, dateFrom));
    Map<Integer, Security> securities = new HashMap<>();
    summaries.keySet().forEach(security -> securities.put(security.getIdSecuritycurrency(), security));
    return new ReplayResult(securities, flows);
  }

  private static void recordFlow(Transaction transaction, SecurityPositionSummary summary,
      Map<Security, Double> flowSoFar, Map<Integer, List<Flow>> flows, LocalDate dateFrom) {
    Security security = transaction.getSecurity();
    double before = flowSoFar.getOrDefault(security, 0.0);
    flowSoFar.put(security, summary.positionFlow);
    LocalDate transactionDate = transaction.getTransactionDate();
    if (transactionDate.isAfter(dateFrom)) {
      flows.computeIfAbsent(security.getIdSecuritycurrency(), id -> new ArrayList<>())
          .add(new Flow(transactionDate, summary.positionFlow - before, transaction.getIdSecurityTransfer()));
    }
  }

  private static Map<Integer, List<DailyValue>> dailyValues(List<Object[]> rows) {
    Map<Integer, List<DailyValue>> values = new HashMap<>();
    for (Object[] r : rows) {
      values.computeIfAbsent(((Number) r[0]).intValue(), id -> new ArrayList<>())
          .add(new DailyValue((LocalDate) r[1], number(r[2]), number(r[3]), number(r[4]), flag(r[5]), flag(r[6]),
              flag(r[7])));
    }
    return values;
  }

  private static Map<Integer, List<Span>> spans(List<Object[]> rows) {
    Map<Integer, List<Span>> spans = new HashMap<>();
    for (Object[] r : rows) {
      spans.computeIfAbsent(((Number) r[0]).intValue(), id -> new ArrayList<>())
          .add(new Span((LocalDate) r[1], (LocalDate) r[2]));
    }
    return spans;
  }

  /** Closes per foreign security currency and day; securities in the main currency need none. */
  private Map<String, Map<LocalDate, Double>> rates(Collection<Integer> population, Map<Integer, Security> securities,
      String mainCurrency, LocalDate dateFrom, LocalDate dateTo) {
    Set<String> currencies = new HashSet<>();
    population.forEach(id -> currencies.add(securities.get(id).getCurrency()));
    currencies.remove(mainCurrency);
    Map<String, Map<LocalDate, Double>> rates = new HashMap<>();
    if (!currencies.isEmpty()) {
      for (Object[] r : historyquoteJpaRepository.getClosesOfCurrencyPairsInPeriod(currencies, mainCurrency, dateFrom,
          dateTo)) {
        rates.computeIfAbsent((String) r[0], c -> new HashMap<>()).put((LocalDate) r[1], number(r[2]));
      }
    }
    return rates;
  }

  private static Function<LocalDate, Double> rateOfDay(String currency, String mainCurrency,
      Map<String, Map<LocalDate, Double>> rates) {
    if (currency.equals(mainCurrency)) {
      return day -> 1.0;
    }
    Map<LocalDate, Double> closes = rates.getOrDefault(currency, Map.of());
    return closes::get;
  }

  private SecurityPeriodPerformanceRow toRow(Security security, Result r, int precisionMC) {
    int precision = globalparametersService.getPrecisionForCurrency(security.getCurrency());
    return new SecurityPeriodPerformanceRow(security.getIdSecuritycurrency(), security.getName(), security.getIsin(),
        security.getCurrency(), security.isMarginInstrument(), r.holdingDays(), r.unitsStart(), r.unitsEnd(),
        round(r.valueStartMC(), precisionMC), round(r.valueEndMC(), precisionMC), round(r.inflowMC(), precisionMC),
        round(r.outflowMC(), precisionMC), round(r.gainPeriod(), precision), round(r.gainPeriodMC(), precisionMC),
        percent(r.twr()), percent(r.twrSecurityCurrency()), r.remark());
  }

  private static void addToTotals(double[] totals, Result r) {
    totals[0] += r.valueStartMC() == null ? 0 : r.valueStartMC();
    totals[1] += r.valueEndMC() == null ? 0 : r.valueEndMC();
    totals[2] += r.inflowMC() == null ? 0 : r.inflowMC();
    totals[3] += r.outflowMC() == null ? 0 : r.outflowMC();
    totals[4] += r.gainPeriodMC() == null ? 0 : r.gainPeriodMC();
  }

  private static Double round(Double value, int precision) {
    return value == null ? null : DataHelper.round(value, precision);
  }

  private static Double percent(Double ratio) {
    return ratio == null ? null : DataBusinessHelper.roundPercentage(ratio * 100);
  }

  private static double number(Object value) {
    return value == null ? 0 : ((Number) value).doubleValue();
  }

  private static boolean flag(Object value) {
    return value != null && ((Number) value).intValue() != 0;
  }
}
