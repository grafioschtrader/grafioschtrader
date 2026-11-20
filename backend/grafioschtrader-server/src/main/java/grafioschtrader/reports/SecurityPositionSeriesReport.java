package grafioschtrader.reports;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.instrument.CostBasisMethods;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.TransactionsMarginOpenUnits;
import grafioschtrader.reportviews.performance.SecurityPositionSeries;
import grafioschtrader.reportviews.performance.SecurityPositionSeries.ConsumedLot;
import grafioschtrader.reportviews.performance.SecurityPositionSeries.DailyValue;
import grafioschtrader.reportviews.performance.SecurityPositionSeries.Event;
import grafioschtrader.reportviews.performance.SecurityPositionSeries.Input;
import grafioschtrader.reportviews.performance.SecurityPositionSeries.MarginPosition;
import grafioschtrader.reportviews.performance.SecurityPositionSeriesResult;
import grafioschtrader.reportviews.performance.SecurityPositionSeriesResult.Phase;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityaccountJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.service.CostBasisMethodService;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.SecurityPositionSeriesRemark;
import grafioschtrader.types.TransactionType;

//@formatter:off
/**
 * Supplies the security position chart: the holding phases of one security in the scope of the view the chart was
 * opened from, and the value, invested capital, results, returns and drawdown of one phase.
 *
 * <p>
 * The scope is a tenant, a portfolio or one security account; accounts outside it never enter. Only the transactions
 * of the security are loaded. The replay runs once per security account with its own summary map, because one map
 * across accounts would mix them in the order of the accounts and not in time. It runs in the currency of the security,
 * so a missing historical exchange rate cannot abort it; {@link SecurityPositionSeries} converts into the main currency
 * itself. Nothing is stored or cached.
 * </p>
 */
//@formatter:on
@Component
public class SecurityPositionSeriesReport {

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private PortfolioJpaRepository portfolioJpaRepository;

  @Autowired
  private SecurityaccountJpaRepository securityaccountJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityJpaRepository;

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private SecurityPositionReplay securityPositionReplay;

  @Autowired
  private CostBasisMethodService costBasisMethodService;

  @Autowired
  private GlobalparametersService globalparametersService;

  /** The accounts, the main currency and the display name of the scope; tenant scope when both ids are null. */
  private record Scope(Tenant tenant, List<Securityaccount> accounts, String mainCurrency, String name,
      boolean tenantScope) {
  }

  /** Values of an account's summary after its previous transaction, for the deltas of the next one. */
  private static final class Previous {
    double costBase;
    double flow;
    double income;
    /** Units of each margin position at its opening, by the opening transaction. */
    final Map<Integer, Double> openedUnits = new HashMap<>();
  }

  /**
   * Calculates the phases and the series of one phase.
   *
   * @param idTenant           tenant of the request
   * @param idSecuritycurrency the security
   * @param idPortfolio        portfolio of the scope, or null
   * @param idSecurityaccount  security account of the scope, or null; at most one of the two may be set
   * @param phaseFrom          first day of the requested phase, null for the newest
   * @param today              the client's calendar day, resolved on the request thread
   * @return the phases and the series
   * @throws SecurityException      if the portfolio, the account or the security does not belong to the tenant
   * @throws DataViolationException if both scope parameters are set
   */
  @Transactional(readOnly = true)
  public SecurityPositionSeriesResult getSeries(Integer idTenant, Integer idSecuritycurrency, Integer idPortfolio,
      Integer idSecurityaccount, LocalDate phaseFrom, LocalDate today) {
    if (idPortfolio != null && idSecurityaccount != null) {
      throw new DataViolationException("report.chart.scope", "gt.report.chart.scope.invalid", null);
    }
    Scope scope = scope(idTenant, idPortfolio, idSecurityaccount);
    Security security = securityJpaRepository.findById(idSecuritycurrency)
        .orElseThrow(() -> new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH));
    boolean margin = security.isMarginInstrument();
    List<Event> events = scope.accounts().isEmpty() ? List.of() : events(scope, security, today);
    List<Phase> phases = SecurityPositionSeries.phases(events);
    Phase phase = SecurityPositionSeries.choose(phases, phaseFrom);
    List<DailyValue> dailyValues = phase == null ? List.of()
        : margin ? marginDailyValues(security, scope.mainCurrency(), phase, today)
            : dailyValues(scope, idSecuritycurrency, phase, today);
    Input input = new Input(idSecuritycurrency, security.getName(), security.getCurrency(), scope.mainCurrency(),
        scope.name(), margin, globalparametersService.getPrecisionForCurrency(security.getCurrency()),
        globalparametersService.getPrecisionForCurrency(scope.mainCurrency()), events, phases, phase, dailyValues,
        phase == null ? Set.of() : sessions(security, phase, today), today);
    return phase == null ? SecurityPositionSeries.empty(input, SecurityPositionSeriesRemark.NO_HOLDING)
        : SecurityPositionSeries.calculate(input);
  }

  private Scope scope(Integer idTenant, Integer idPortfolio, Integer idSecurityaccount) {
    Tenant tenant = tenantJpaRepository.getReferenceById(idTenant);
    if (idSecurityaccount != null) {
      Securityaccount account = securityaccountJpaRepository.findByIdSecuritycashAccountAndIdTenant(idSecurityaccount,
          idTenant);
      if (account == null) {
        throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
      }
      return new Scope(tenant, List.of(account), account.getPortfolio().getCurrency(), account.getName(), false);
    }
    if (idPortfolio != null) {
      Portfolio portfolio = portfolioJpaRepository.findByIdTenantAndIdPortfolio(idTenant, idPortfolio);
      if (portfolio == null) {
        throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
      }
      return new Scope(tenant, securityaccountJpaRepository.findByPortfolio_IdPortfolioAndIdTenant(idPortfolio,
          idTenant), portfolio.getCurrency(), portfolio.getName(), false);
    }
    return new Scope(tenant, securityaccountJpaRepository.findByIdTenant(idTenant), tenant.getCurrency(),
        tenant.getTenantName(), true);
  }

  /** Replays every account of the scope in the currency of the security and returns the events by date. */
  private List<Event> events(Scope scope, Security security, LocalDate today) {
    List<Integer> ids = scope.accounts().stream().map(Securityaccount::getIdSecuritycashAccount).toList();
    Map<Integer, List<Transaction>> byAccount = transactionJpaRepository
        .findByIdTenantAndSecurityAccountsIdSecurity(scope.tenant().getIdTenant(), ids,
            security.getIdSecuritycurrency())
        .stream().collect(Collectors.groupingBy(Transaction::getIdSecurityaccount));
    DateTransactionCurrencypairMap securityCurrencyMap = new DateTransactionCurrencypairMap(security.getCurrency(),
        today, new ArrayList<>(), new ArrayList<>(), false, true, today);
    CostBasisMethods costBasisMethods = costBasisMethodService.forTenant(scope.tenant().getIdTenant());
    List<Event> events = new ArrayList<>();
    for (Map.Entry<Integer, List<Transaction>> entry : byAccount.entrySet()) {
      Previous previous = new Previous();
      securityPositionReplay.replayAccount(entry.getKey(), entry.getValue(), new HashMap<>(),
          scope.tenant().isExcludeDivTax(), securityCurrencyMap, costBasisMethods,
          (transaction, summary) -> events.add(event(transaction, summary, previous, scope.mainCurrency())));
    }
    events.sort(Comparator.comparing(Event::date));
    return events;
  }

  private static Event event(Transaction transaction, SecurityPositionSummary summary, Previous previous,
      String mainCurrency) {
    if (transaction.getSecurity().isMarginInstrument()) {
      return marginEvent(transaction, summary, previous, mainCurrency);
    }
    boolean sale = transaction.getTransactionType() == TransactionType.REDUCE;
    List<ConsumedLot> lots = sale && summary.transactionConsumedLots != null
        ? summary.transactionConsumedLots.stream().map(l -> new ConsumedLot(l.idTransaction, l.cost)).toList()
        : null;
    Event event = new Event(transaction.getTransactionDate(), transaction.getIdSecurityaccount(),
        transaction.getTransactionType(), transaction.getIdTransaction(), transaction.getIdSecurityTransfer(),
        transaction.getUnits(), summary.units, previous.costBase, summary.adjustedCostBase,
        summary.positionFlow - previous.flow, summary.incomeGainLossSecurity - previous.income,
        sale ? value(summary.transactionGainLoss) : 0, sale ? value(summary.transactionCostBase) : 0, lots,
        bookedRate(transaction, mainCurrency), null, false,
        transaction.getTransactionType() == TransactionType.DIVIDEND ? transaction.getExDate() : null);
    previous.costBase = summary.adjustedCostBase;
    previous.flow = summary.positionFlow;
    previous.income = summary.incomeGainLossSecurity;
    return event;
  }

  /**
   * An event of a margin instrument. The open positions are copied from the summary's tracked positions; their running
   * counters change sign and basis during closes, so the entry value is taken from the opening transaction: its net
   * price times the share of its units still open. The result of an opening (its costs) or a close goes into
   * {@code gainLoss}, a finance cost into {@code incomeSC}.
   */
  private static Event marginEvent(Transaction transaction, SecurityPositionSummary summary, Previous previous,
      String mainCurrency) {
    List<MarginPosition> positions = new ArrayList<>();
    for (TransactionsMarginOpenUnits open : summary.getTransactionsMarginOpenUnits()) {
      double units = Math.abs(open.openUnits);
      if (units > SecurityPositionSeries.FLAT_UNITS) {
        Transaction opening = open.openTransaction;
        double openedUnits = previous.openedUnits.computeIfAbsent(opening.getIdTransaction(), _ -> units);
        positions.add(new MarginPosition(opening.getIdTransaction(),
            opening.getTransactionType() != TransactionType.ACCUMULATE, units,
            Math.abs(opening.getSeucritiesNetPrice()) * units / openedUnits, opening.getValuePerPoint()));
      }
    }
    double result = value(summary.transactionGainLoss);
    boolean financeCost = transaction.getTransactionType() == TransactionType.FINANCE_COST;
    return new Event(transaction.getTransactionDate(), transaction.getIdSecurityaccount(),
        transaction.getTransactionType(), transaction.getIdTransaction(), transaction.getIdSecurityTransfer(),
        transaction.getUnits(), positions.stream().mapToDouble(MarginPosition::openUnits).sum(), 0, 0, 0,
        financeCost ? result : 0, financeCost ? 0 : result, 0, null, bookedRate(transaction, mainCurrency), positions,
        transaction.isMarginOpenPosition(), null);
  }

  /**
   * The rate of a transaction into the main currency that the transaction itself documents: 1 for a security in the
   * main currency, whose stored rate is the cross rate of the cash account; the booked rate when the cash account is in
   * the main currency; otherwise null, and the closing rate of the day applies.
   */
  private static Double bookedRate(Transaction transaction, String mainCurrency) {
    if (transaction.getSecurity().getCurrency().equals(mainCurrency)) {
      return 1.0;
    }
    if (transaction.getCashaccount() != null && mainCurrency.equals(transaction.getCashaccount().getCurrency())) {
      return transaction.getCurrencyExRate();
    }
    return null;
  }

  private static double value(Double value) {
    return value == null ? 0 : value;
  }

  private List<DailyValue> dailyValues(Scope scope, Integer idSecuritycurrency, Phase phase, LocalDate today) {
    LocalDate to = phase.to() != null ? phase.to() : today;
    List<Object[]> rows = scope.tenantScope()
        ? holdSecurityaccountSecurityJpaRepository.getDailyValuesOfSecurityByTenant(scope.tenant().getIdTenant(),
            idSecuritycurrency, phase.from(), to)
        : holdSecurityaccountSecurityJpaRepository.getDailyValuesOfSecurityBySecurityaccounts(
            scope.tenant().getIdTenant(),
            scope.accounts().stream().map(Securityaccount::getIdSecuritycashAccount).toList(), idSecuritycurrency,
            phase.from(), to);
    List<DailyValue> values = new ArrayList<>(rows.size());
    for (Object[] r : rows) {
      values.add(new DailyValue(toLocalDate(r[0]), number(r[1]), number(r[2]), nullableNumber(r[3]),
          nullableNumber(r[4]), flag(r[5]), flag(r[6])));
    }
    return values;
  }

  /**
   * Daily closes and rates of a margin instrument. Its holding rows keep one signed unit count and one average price
   * per account, so a long and a short position open in the same account cancel there; the value is therefore formed
   * from the open positions of the events, and a day needs only the close and the rate into the main currency.
   */
  private List<DailyValue> marginDailyValues(Security security, String mainCurrency, Phase phase, LocalDate today) {
    LocalDate to = phase.to() != null ? phase.to() : today;
    Map<LocalDate, Double> rates = closingRates(security.getCurrency(), mainCurrency, phase.from(), to);
    List<DailyValue> values = new ArrayList<>();
    for (Historyquote quote : historyquoteJpaRepository
        .findByIdSecuritycurrencyAndDateBetweenOrderByDate(security.getIdSecuritycurrency(), phase.from(), to)) {
      Double rate = security.getCurrency().equals(mainCurrency) ? Double.valueOf(1) : rates.get(quote.getDate());
      values.add(new DailyValue(quote.getDate(), 0, 0, quote.getClose(), rate, false, false));
    }
    return values;
  }

  /** Closing rates from the security currency into the main currency, from a pair of either direction. */
  private Map<LocalDate, Double> closingRates(String currency, String mainCurrency, LocalDate from, LocalDate to) {
    Map<LocalDate, Double> rates = new HashMap<>();
    if (currency.equals(mainCurrency)) {
      return rates;
    }
    for (Object[] r : historyquoteJpaRepository.getClosesOfCurrencyPairsInPeriod(List.of(currency), mainCurrency, from,
        to)) {
      rates.put(toLocalDate(r[1]), number(r[2]));
    }
    if (rates.isEmpty()) {
      for (Object[] r : historyquoteJpaRepository.getClosesOfCurrencyPairsInPeriod(List.of(mainCurrency), currency,
          from, to)) {
        double close = number(r[2]);
        if (close != 0) {
          rates.put(toLocalDate(r[1]), 1 / close);
        }
      }
    }
    return rates;
  }

  private Set<LocalDate> sessions(Security security, Phase phase, LocalDate today) {
    LocalDate to = phase.to() != null ? phase.to() : today;
    return tradingDaysPlusJpaRepository
        .getSessionsByStockexchange(List.of(security.getStockexchange().getIdStockexchange()), phase.from(), to)
        .stream().map(s -> s.getTradingDate()).collect(Collectors.toSet());
  }

  private static LocalDate toLocalDate(Object value) {
    return value instanceof java.sql.Date date ? date.toLocalDate() : (LocalDate) value;
  }

  private static double number(Object value) {
    return value == null ? 0 : ((Number) value).doubleValue();
  }

  private static Double nullableNumber(Object value) {
    return value == null ? null : ((Number) value).doubleValue();
  }

  private static boolean flag(Object value) {
    if (value instanceof Boolean b) {
      return b;
    }
    return value != null && ((Number) value).intValue() != 0;
  }
}
