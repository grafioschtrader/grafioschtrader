package grafioschtrader.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Limit;

import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.repository.AlgoTradingRepository;
import grafioschtrader.service.AlgoHistoricalValuationService.ClosingPrices;
import grafioschtrader.service.AlgoMeanReversionDecisionService.MarketData;
import grafioschtrader.types.CreateType;

/**
 * The only window a historical replay has on the market, and the place where "no look-ahead" is enforced rather than
 * merely intended.
 *
 * <p>
 * Every observation of the run is read once, ascending, up to the run's end date; a request for a day returns the
 * prefix of that list which ends at the requested day. A quote of a later day is therefore not filtered out somewhere
 * downstream - it is not reachable at all, whatever a decision module asks for. The live intraday quote of an
 * instrument is never read here, because this class talks to the historical repository only.
 * </p>
 *
 * <p>
 * The same instance answers both the indicator window of a decision and the closing price a valuation needs, so a day
 * cannot be decided against one set of prices and valued against another. Warm-up is what makes the two differ in
 * length: an indicator may read the observations before the opening date, while the valuation only ever looks at the
 * last one.
 * </p>
 */
public class AlgoReplayMarketData implements MarketData, ClosingPrices {

  /**
   * How many observations a decision may see. The live evaluation bounds its own reads to the same number, so an
   * indicator that needs a longer history is unavailable in a replay for exactly the same reason it is unavailable
   * live.
   */
  public static final int OBSERVATION_WINDOW = 1200;

  private final AlgoTradingRepository data;
  private final LocalDate horizonEnd;
  private final int loadLimit;
  private final Map<Integer, List<Historyquote>> cache = new HashMap<>();
  private java.util.function.Function<LocalDate, Map<String, Double>> receivables = _ -> Map.of();
  private java.util.function.Function<LocalDate, Map<String, Double>> custodyLiabilities = _ -> Map.of();

  public void setCustodyLiabilities(java.util.function.Function<LocalDate, Map<String, Double>> liabilities) {
    this.custodyLiabilities = liabilities;
  }

  private AlgoReplayInputs.Snapshot inputs;
  private final Map<Integer, Map<Integer, List<Securitysplit>>> splitMaps = new HashMap<>();

  /** Installs the immutable allocation and exclusion policy captured before this worker started. */
  public void setInputs(AlgoReplayInputs.Snapshot inputs) {
    this.inputs = inputs;
  }

  @Override
  public AlgoReplayAllocation allocation() {
    return inputs == null ? null : inputs.allocation();
  }

  /**
   * The splits captured with the run, built once per instrument for the whole replay. An instrument the snapshot does
   * not know is left to the database.
   */
  @Override
  public Map<Integer, List<Securitysplit>> splitMap(Integer idSecuritycurrency) {
    if (inputs == null || !inputs.instruments().containsKey(idSecuritycurrency)) {
      return null;
    }
    return splitMaps.computeIfAbsent(idSecuritycurrency, id -> {
      List<Securitysplit> list = inputs.instruments().get(id).splits().stream()
          .map(split -> new Securitysplit(id, split.date(), split.from(), split.to(), CreateType.ADD_MODIFIED_USER))
          .toList();
      return list.isEmpty() ? Map.of() : Map.of(id, list);
    });
  }

  @Override
  public boolean tradingExcluded(grafioschtrader.entities.Security security) {
    return inputs != null && inputs.instruments().containsKey(security.getId()) ? inputs.excluded(security.getId())
        : security.isSimulationTradingExcluded();
  }

  /** A forced liquidation must have an observation on its execution date, not a carried earlier close. */
  public Double exactClose(Integer security, LocalDate date) {
    var observations = history(security, date);
    return observations.isEmpty() || !observations.getLast().getDate().equals(date) ? null
        : observations.getLast().getClose();
  }

  /** Attaches this run's income ledger, without exposing unpaid income as available cash. */
  public void setReceivables(java.util.function.Function<LocalDate, Map<String, Double>> receivables) {
    this.receivables = receivables;
  }

  @Override
  public Map<String, Double> receivables(LocalDate asOf) {
    Map<String, Double> combined = new HashMap<>(receivables.apply(asOf));
    custodyLiabilities.apply(asOf).forEach((currency, amount) -> combined.merge(currency, amount, Double::sum));
    return combined;
  }

  /**
   * @param data        the historical repository, which is the only price source of a replay
   * @param openingDate the immutable opening date of the simulation environment, which is the first day that has to be
   *                    valued and therefore the oldest day the window must still reach
   * @param horizonEnd  the last day of the run; nothing after it is ever loaded
   */
  public AlgoReplayMarketData(AlgoTradingRepository data, LocalDate openingDate, LocalDate horizonEnd) {
    this.data = data;
    this.horizonEnd = horizonEnd;
    this.loadLimit = OBSERVATION_WINDOW + spanOf(openingDate, horizonEnd);
  }

  /**
   * How many observations the run itself can consume, counted in calendar days rather than in trading days.
   *
   * <p>
   * The number of days a run evaluates is not the number of rows its instruments hold over the same period: a run walks
   * the trading calendar of the application, roughly 252 days a year, while a currency pair carries a row on every
   * calendar day - the weekend rows being carried forward - and so does a crypto currency. Sizing the window by trading
   * days therefore falls about 113 rows short for every year of run length, which on a long run silently pushes the
   * opening years out of the window: the valuation of those days finds no exchange rate at or before them and drops
   * every position and every cash balance in a foreign currency, and the days are reported as not priced. Calendar days
   * are the upper bound for any instrument, because none of them holds more than one row per day.
   * </p>
   *
   * @param openingDate the opening date of the environment
   * @param horizonEnd  the last day of the run
   * @return the number of days the run spans, both ends included, and never negative
   */
  private static int spanOf(LocalDate openingDate, LocalDate horizonEnd) {
    return (int) Math.max(0, ChronoUnit.DAYS.between(openingDate, horizonEnd)) + 1;
  }

  @Override
  public List<Historyquote> history(Integer security, LocalDate through) {
    if (through == null || through.isAfter(horizonEnd)) {
      // Not a data problem but a programming error: something asked the replay for a day outside its own horizon.
      throw new IllegalArgumentException("A replay cannot observe " + through + " beyond its horizon " + horizonEnd);
    }
    List<Historyquote> all = cache.computeIfAbsent(security, this::load);
    int end = upperBound(all, through);
    return all.subList(Math.max(0, end - OBSERVATION_WINDOW), end);
  }

  @Override
  public Double close(Integer idSecuritycurrency, LocalDate asOf) {
    List<Historyquote> observations = history(idSecuritycurrency, asOf);
    return observations.isEmpty() ? null : observations.getLast().getClose();
  }

  @Override
  public Long volume(Integer idSecuritycurrency, LocalDate asOf) {
    List<Historyquote> observations = history(idSecuritycurrency, asOf);
    return observations.isEmpty() ? null : observations.getLast().getVolume();
  }

  private List<Historyquote> load(Integer idSecuritycurrency) {
    List<Historyquote> descending = new ArrayList<>(data.history(idSecuritycurrency, horizonEnd, Limit.of(loadLimit)));
    Collections.reverse(descending);
    return descending;
  }

  /** Index of the first observation after the given day, so the prefix before it is everything that may be seen. */
  private int upperBound(List<Historyquote> ascending, LocalDate through) {
    int low = 0;
    int high = ascending.size();
    while (low < high) {
      int middle = (low + high) >>> 1;
      if (ascending.get(middle).getDate().isAfter(through)) {
        high = middle;
      } else {
        low = middle + 1;
      }
    }
    return low;
  }
}
