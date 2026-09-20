package grafioschtrader.reports;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.dto.IHeldInstrumentIntraday;
import grafioschtrader.dto.ISecuritycurrencyIdDateClose;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.reportviews.dashboard.HoldingMoversPayload.Branch;
import grafioschtrader.reportviews.dashboard.HoldingMoversPayload.BranchType;
import grafioschtrader.reportviews.dashboard.HoldingMoversPayload.Movers;
import grafioschtrader.reportviews.dashboard.HoldingMoversPayload.Row;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TradingDaysMinusJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;

/**
 * Ranks the instruments a tenant holds by how far they moved over a single session.
 *
 * <p>
 * The population is read from {@code hold_securityaccount_security}, so no transaction is replayed: what this report
 * needs from a position is its size, and that is what the holding periods record. Prices come from the closing history
 * and, for the intraday branch, from the last snapshot a connector delivered. The report never fetches a price - a
 * dashboard read has no business reaching out to a quote provider - so the intraday branch states how old its snapshot
 * is instead of presenting it as current.
 * </p>
 *
 * <p>
 * Where a market was shut or a quote had not arrived, the closing lookup yields the newest price at or before the date.
 * That keeps the instrument in the ranking, but its move then spans more than the branch does, so the row carries the
 * dates actually used and is marked as a substitute rather than counted as a one-session move.
 * </p>
 */
@Component
public class HoldingMoversReport {

  /** Which end of the ranking a card shows. A move of exactly zero belongs to neither end. */
  public enum Direction {
    WINNERS, LOSERS
  }

  /** No market holds a session on the current day - an ordinary weekend or holiday, not a fault. */
  private static final String REASON_NO_SESSION_TODAY = "DASHBOARD_MOVERS_NO_SESSION_TODAY";

  /** The trading calendar does not reach back far enough to name the two sessions a closing branch compares. */
  private static final String REASON_NO_CALENDAR = "DASHBOARD_MOVERS_NO_SESSION";

  private final HoldSecurityaccountSecurityJpaRepository holdRepository;
  private final HistoryquoteJpaRepository historyquoteJpaRepository;
  private final TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;
  private final TradingDaysMinusJpaRepository tradingDaysMinusJpaRepository;
  private final TenantJpaRepository tenantJpaRepository;

  public HoldingMoversReport(HoldSecurityaccountSecurityJpaRepository holdRepository,
      HistoryquoteJpaRepository historyquoteJpaRepository, TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository,
      TradingDaysMinusJpaRepository tradingDaysMinusJpaRepository, TenantJpaRepository tenantJpaRepository) {
    this.holdRepository = holdRepository;
    this.historyquoteJpaRepository = historyquoteJpaRepository;
    this.tradingDaysPlusJpaRepository = tradingDaysPlusJpaRepository;
    this.tradingDaysMinusJpaRepository = tradingDaysMinusJpaRepository;
    this.tenantJpaRepository = tenantJpaRepository;
  }

  /** Everything one call needs to look up twice, so three branches over overlapping holdings cost one read each. */
  private final class Context {
    private final Integer idTenant;
    private final Map<LocalDate, List<IHeldInstrumentIntraday>> holdings = new HashMap<>();
    private final Map<LocalDate, Map<Integer, ISecuritycurrencyIdDateClose>> closes = new HashMap<>();
    private final Map<LocalDate, Integer> margin = new HashMap<>();
    private final Set<Integer> ids = new LinkedHashSet<>();

    private Context(Integer idTenant) {
      this.idTenant = idTenant;
    }

    /** Held instruments at a date, read once per date because the three branches largely overlap. */
    private List<IHeldInstrumentIntraday> held(LocalDate date) {
      return holdings.computeIfAbsent(date, key -> {
        List<IHeldInstrumentIntraday> read = holdRepository.getHeldInstrumentsWithIntradayByTenant(idTenant, key);
        read.forEach(instrument -> {
          ids.add(instrument.getIdSecuritycurrency());
          if (instrument.getIdCurrencypairTenant() != null) {
            ids.add(instrument.getIdCurrencypairTenant());
          }
        });
        return read;
      });
    }

    /**
     * Closing prices of every instrument and currency pair the whole call may need, in one statement per date. The
     * query takes the newest price at or before the date, so a shut market yields an older price rather than no row.
     */
    private Map<Integer, ISecuritycurrencyIdDateClose> closes(LocalDate date) {
      if (date == null || ids.isEmpty()) {
        return Map.of();
      }
      return closes.computeIfAbsent(date, key -> {
        Map<Integer, ISecuritycurrencyIdDateClose> byId = new HashMap<>();
        historyquoteJpaRepository.getIdDateCloseByIdsAndDate(new ArrayList<>(ids), key)
            .forEach(close -> byId.put(close.getIdSecuritycurrency(), close));
        return byId;
      });
    }

    private int marginCount(LocalDate date) {
      return margin.computeIfAbsent(date, key -> holdRepository.countHeldMarginInstrumentsByTenant(idTenant, key));
    }
  }

  /**
   * Builds one card: three branches, each ranked twice, over the instruments held at the branch date.
   *
   * @param idTenant   the tenant whose holdings are ranked; all of its portfolios are included
   * @param topN       rows per ordering per branch
   * @param chosenDate session of the third branch. A date that is not a trading day, or one after the last completed
   *                   session, is moved back to the nearest session at or before it; null selects the session before
   *                   the last one
   * @param direction  whether the card shows the top or the bottom of the ranking
   * @return the card payload, with every amount in the tenant currency
   */
  public Movers getMovers(Integer idTenant, int topN, LocalDate chosenDate, Direction direction) {
    String currency = tenantJpaRepository.getReferenceById(idTenant).getCurrency();
    LocalDate today = LocalDate.now();
    LocalDate lastSession = sessionBefore(today);
    LocalDate chosen = chosenDate == null ? sessionBefore(lastSession) : sessionAtOrBefore(chosenDate);
    if (chosen != null && lastSession != null && chosen.isAfter(lastSession)) {
      chosen = lastSession;
    }

    Context context = new Context(idTenant);
    // Read every population first, so the closing lookups below cover all three branches with one query per date.
    Stream.of(today, lastSession, chosen).filter(Objects::nonNull).distinct().forEach(context::held);

    List<Branch> branches = new ArrayList<>();
    branches.add(intradayBranch(context, topN, direction, today, lastSession));
    branches.add(
        closingBranch(context, topN, direction, BranchType.LAST_TRADING_DAY, lastSession, sessionBefore(lastSession)));
    branches.add(closingBranch(context, topN, direction, BranchType.CHOSEN_DATE, chosen, sessionBefore(chosen)));
    return new Movers(currency, topN, branches);
  }

  /** Latest session at or before a date, or null when the calendar does not reach back that far. */
  private LocalDate sessionAtOrBefore(LocalDate date) {
    return date == null ? null : sessionBefore(date.plusDays(1));
  }

  /** Latest session strictly before a date, the closing boundary the end-of-day reports already use. */
  private LocalDate sessionBefore(LocalDate date) {
    if (date == null) {
      return null;
    }
    TradingDaysPlus session = tradingDaysPlusJpaRepository.findTopByTradingDateLessThanOrderByTradingDateDesc(date);
    return session == null ? null : session.getTradingDate();
  }

  /**
   * Builds the branch covering the current day, from whatever intraday snapshot the application holds.
   *
   * <p>
   * An instrument only belongs here when its market actually holds a session today. Outside a session the connectors
   * keep reporting the last completed one - the intraday update runs every calendar day and knows nothing about the
   * trading calendar - so without this gate a Sunday would show the previous Friday twice, once labelled Intraday and
   * once as the last trading day. {@code trading_days_plus} contains weekdays only, so its single lookup settles every
   * weekend and global holiday before any exchange is consulted.
   * </p>
   *
   * <p>
   * A snapshot older than today is kept but marked: dropping it would shorten the list on exactly those instruments
   * whose data is worst, and say nothing about why.
   * </p>
   */
  private Branch intradayBranch(Context context, int topN, Direction direction, LocalDate today,
      LocalDate lastSession) {
    List<IHeldInstrumentIntraday> held = context.held(today);
    if (!tradingDaysPlusJpaRepository.existsById(today)) {
      return new Branch(BranchType.INTRADAY, null, null, null, 0, 0, held.size(), context.marginCount(today),
          REASON_NO_SESSION_TODAY, List.of(), List.of());
    }
    Set<Integer> shut = exchangesWithoutSession(held, today);
    Map<Integer, ISecuritycurrencyIdDateClose> rates = context.closes(today);
    Map<Integer, ISecuritycurrencyIdDateClose> previousCloses = context.closes(lastSession);

    List<Row> rows = new ArrayList<>();
    int omitted = 0;
    for (IHeldInstrumentIntraday instrument : held) {
      Double rate = rateFor(instrument, rates);
      Double previous = instrument.getIdStockexchange() == null || shut.contains(instrument.getIdStockexchange()) ? null
          : previousPrice(instrument, previousCloses);
      if (instrument.getSLast() == null || previous == null || previous == 0d || rate == null) {
        omitted++;
        continue;
      }
      LocalDateTime snapshot = instrument.getSTimestamp();
      boolean stale = snapshot == null || !snapshot.toLocalDate().isEqual(today);
      rows.add(row(instrument, instrument.getSLast(), previous, rate, snapshot == null ? null : snapshot.toLocalDate(),
          null, stale));
    }
    LocalDateTime asOf = held.stream().map(IHeldInstrumentIntraday::getSTimestamp).filter(Objects::nonNull)
        .min(LocalDateTime::compareTo).orElse(null);
    return branch(BranchType.INTRADAY, null, null, asOf, rows, omitted, context, today, topN, direction);
  }

  /** Builds a branch comparing two closing prices. A null date means the calendar does not reach that session. */
  private Branch closingBranch(Context context, int topN, Direction direction, BranchType type, LocalDate date,
      LocalDate previousDate) {
    if (date == null || previousDate == null) {
      return new Branch(type, date, previousDate, null, 0, 0, 0, 0, REASON_NO_CALENDAR, List.of(), List.of());
    }
    List<IHeldInstrumentIntraday> held = context.held(date);
    Map<Integer, ISecuritycurrencyIdDateClose> closes = context.closes(date);
    Map<Integer, ISecuritycurrencyIdDateClose> previousCloses = context.closes(previousDate);

    List<Row> rows = new ArrayList<>();
    int omitted = 0;
    for (IHeldInstrumentIntraday instrument : held) {
      ISecuritycurrencyIdDateClose close = closes.get(instrument.getIdSecuritycurrency());
      ISecuritycurrencyIdDateClose previous = previousCloses.get(instrument.getIdSecuritycurrency());
      Double rate = rateFor(instrument, closes);
      if (close == null || previous == null || previous.getClose() == 0d || rate == null
          || close.getDate().equals(previous.getDate())) {
        omitted++;
        continue;
      }
      boolean substitute = !close.getDate().equals(date) || !previous.getDate().equals(previousDate);
      rows.add(row(instrument, close.getClose(), previous.getClose(), rate, close.getDate(), previous.getDate(),
          substitute));
    }
    return branch(type, date, previousDate, null, rows, omitted, context, date, topN, direction);
  }

  /** Of the exchanges behind a population, the ones holding no session on a date. Empty when they all trade. */
  private Set<Integer> exchangesWithoutSession(List<IHeldInstrumentIntraday> held, LocalDate date) {
    List<Integer> exchanges = held.stream().map(IHeldInstrumentIntraday::getIdStockexchange).filter(Objects::nonNull)
        .distinct().toList();
    return exchanges.isEmpty() ? Set.of()
        : new HashSet<>(tradingDaysMinusJpaRepository.getIdStockexchangeWithoutSessionOnDate(exchanges, date));
  }

  /**
   * The price an intraday snapshot moved away from.
   *
   * <p>
   * It is derived from the change the connector reports, never read from {@code s_prev_close}: a third of the
   * connectors never write that column, and nothing clears a field a connector omits, so it drifts further from the
   * last price with every tick. Deriving it here also guarantees the percentage on the card is the connector's own
   * figure, the one the watchlist already shows. When a connector reports no change at all, the last completed
   * session's close stands in - the same fallback the watchlist uses.
   * </p>
   */
  private Double previousPrice(IHeldInstrumentIntraday instrument,
      Map<Integer, ISecuritycurrencyIdDateClose> previousCloses) {
    Double last = instrument.getSLast();
    Double changePercentage = instrument.getSChangePercentage();
    if (last != null && changePercentage != null && changePercentage != -100d) {
      return last / (1 + changePercentage / 100);
    }
    ISecuritycurrencyIdDateClose close = previousCloses.get(instrument.getIdSecuritycurrency());
    return close == null ? null : close.getClose();
  }

  /** Rate from the instrument currency to the tenant currency; one when the instrument already is in that currency. */
  private Double rateFor(IHeldInstrumentIntraday instrument, Map<Integer, ISecuritycurrencyIdDateClose> closes) {
    if (instrument.getIdCurrencypairTenant() == null) {
      return 1d;
    }
    ISecuritycurrencyIdDateClose rate = closes.get(instrument.getIdCurrencypairTenant());
    return rate == null ? null : rate.getClose();
  }

  /** Turns one held instrument and its two prices into a ranked row. */
  private Row row(IHeldInstrumentIntraday instrument, double price, double previousPrice, double rate,
      LocalDate usedDate, LocalDate usedPreviousDate, boolean substitute) {
    double changePercentage = (price / previousPrice - 1) * 100;
    double amountMC = instrument.getUnits() * (price - previousPrice) * rate;
    return new Row(instrument.getIdSecuritycurrency(), instrument.getName(), instrument.getCurrency(),
        instrument.getUnits(), price, previousPrice, changePercentage, amountMC, usedDate, usedPreviousDate,
        substitute);
  }

  /** Orders the rows of a branch twice and cuts both orderings to the requested length. */
  private Branch branch(BranchType type, LocalDate date, LocalDate previousDate, LocalDateTime asOf, List<Row> rows,
      int omitted, Context context, LocalDate holdingDate, int topN, Direction direction) {
    int substitutes = (int) rows.stream().filter(Row::substitute).count();
    return new Branch(type, date, previousDate, asOf, rows.size(), substitutes, omitted,
        context.marginCount(holdingDate), null, rank(rows, Row::changePercentage, topN, direction),
        rank(rows, Row::amountMC, topN, direction));
  }

  /**
   * Keeps the rows that moved in the direction of the card and returns the most extreme of them. Ties break by
   * instrument, so the same data always produces the same list.
   */
  private List<Row> rank(List<Row> rows, ToDoubleFunction<Row> key, int topN, Direction direction) {
    boolean winners = direction == Direction.WINNERS;
    Comparator<Row> byKey = Comparator.comparingDouble(key);
    return rows.stream().filter(row -> winners ? key.applyAsDouble(row) > 0 : key.applyAsDouble(row) < 0)
        .sorted((winners ? byKey.reversed() : byKey).thenComparing(Row::idSecuritycurrency,
            Comparator.nullsLast(Comparator.naturalOrder())))
        .limit(topN).map(this::roundedOutput).toList();
  }

  /** Rounds only after filtering and ranking, preserving the order of moves that display the same percentage. */
  private Row roundedOutput(Row row) {
    return new Row(row.idSecuritycurrency(), row.name(), row.currency(), row.units(), row.price(), row.previousPrice(),
        DataBusinessHelper.roundPercentage(row.changePercentage()), row.amountMC(), row.usedDate(),
        row.usedPreviousDate(), row.substitute());
  }
}
