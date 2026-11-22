package grafioschtrader.reportviews.performance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

import grafiosch.common.DataHelper;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.common.MoneyDrawdown;
import grafioschtrader.common.MoneyDrawdown.Step;
import grafioschtrader.reportviews.performance.SecurityPositionSeriesResult.CurrencySeries;
import grafioschtrader.reportviews.performance.SecurityPositionSeriesResult.DrawdownEpisode;
import grafioschtrader.reportviews.performance.SecurityPositionSeriesResult.Phase;
import grafioschtrader.reportviews.performance.SecurityPositionSeriesResult.PositionEvent;
import grafioschtrader.reportviews.performance.SecurityPositionSeriesResult.UnderwaterRun;
import grafioschtrader.types.SecurityActionRole;
import grafioschtrader.types.SecurityPositionSeriesRemark;
import grafioschtrader.types.TransactionType;

//@formatter:off
/**
 * Builds the holding phases of one security in one scope and the series of the security position chart for one of
 * them. The input comes from the transaction replay of every security account of the scope, walked in the currency of
 * the security, and from the daily values of the holding rows. The class is a pure function of its input; it reads no
 * repository and holds no state, which is what lets every case be fixed with hand-calculated numbers.
 *
 * <ul>
 * <li>A <b>phase</b> starts on the first business date whose end-of-day units, summed over the accounts, are not flat
 * after a flat day, and ends on the next flat day. Income booked after the end of a phase and before the next one
 * belongs to the ended phase.</li>
 * <li>A <b>point</b> is a valued day of the phase, plus the closing day of a closed phase with value 0. A day with
 * units but without a usable value is left out; it sets {@code NOT_VALUED_DAYS} only when the stock exchange of the
 * security had a session that day before today.</li>
 * <li>An event belongs to the first point on or after its date. Events after the last point stay in the event list
 * but not in the series.</li>
 * <li>{@code invested} is the sum of the cost bases the replay keeps per account. Its main currency counterpart
 * converts each purchase with its own rate and removes the same historical amount on a sale, so the realized result
 * in main currency contains the currency result on the cost.</li>
 * <li>Flows are kept gross for the drawdown; only the two legs of one security transfer on one point are netted, and
 * the sale and repurchase of a spin-off parent.</li>
 * <li>The three legs of a spin-off move cost without a realized result. The parent sale removes the historical main
 * currency amount of the moved cost from {@code invested} and leaves the purchases; the child purchase adds the same
 * amount, which {@link #spinOffTransfer} derives from the parent's events, and counts it as the child's purchases.</li>
 * </ul>
 */
//@formatter:on
public final class SecurityPositionSeries {

  /** Units below this absolute amount count as no units. */
  public static final double FLAT_UNITS = 1e-9;

  private SecurityPositionSeries() {
  }

  /**
   * A purchase lot a FIFO or LIFO sale consumed.
   *
   * @param idTransaction       the purchase transaction; for a lot inherited through a spin-off the child purchase
   * @param originIdTransaction the original parent purchase of an inherited lot, null for an ordinary lot
   * @param cost                cost of the consumed units in the currency of the security
   */
  public record ConsumedLot(Integer idTransaction, Integer originIdTransaction, double cost) {

    public ConsumedLot(Integer idTransaction, double cost) {
      this(idTransaction, null, cost);
    }
  }

  /**
   * One lot part a spin-off leg moved: for the parent sale the cost one parent lot handed to the child, for the child
   * purchase one inherited lot.
   *
   * @param idTransaction       the purchase of the lot; for an inherited lot the child purchase
   * @param originIdTransaction the original purchase of an inherited lot, null for an ordinary lot
   * @param cost                the moved cost in the currency of the security of the event
   * @param investedMC          main currency amount of an inherited lot, null for the parts of the parent sale
   */
  public record SpinOffLot(Integer idTransaction, Integer originIdTransaction, double cost, Double investedMC) {

    /** The original purchase the part goes back to: the origin of an inherited lot, otherwise the purchase itself. */
    public Integer rootTransaction() {
      return originIdTransaction != null ? originIdTransaction : idTransaction;
    }
  }

  /**
   * The spin-off side of an event, present only for the three legs of a spin-off. The legs move cost between parent and
   * child; they are neither purchases nor sales in the purchase and realized figures of the chart.
   *
   * @param role                which leg the event is
   * @param idSecurityActionApp the application of the spin-off the leg belongs to
   * @param lots                the moved lot parts, empty for an account on average cost
   * @param investedMC          for the child purchase the main currency amount the parent handed over, null when it is
   *                            unknown; null for the parent legs, whose amount the chart derives itself
   */
  public record SpinOff(SecurityActionRole role, Integer idSecurityActionApp, List<SpinOffLot> lots,
      Double investedMC) {
  }

  /**
   * Main currency amount of the cost a spin-off moved out of one parent account.
   *
   * @param investedMC        the whole amount
   * @param investedMCByRoot  the amount per original purchase, empty for an account on average cost
   * @param complete          false when a rate was missing on the way, so the amount is too low
   */
  public record SpinOffTransfer(double investedMC, Map<Integer, Double> investedMCByRoot, boolean complete) {
  }

  /**
   * One transaction of the replay, captured right after it was processed. The account values are the state of the
   * account's summary after the transaction; the deltas are its changes caused by the transaction.
   *
   * @param date               business date of the transaction
   * @param idSecurityaccount  the security account
   * @param type               the transaction type
   * @param idTransaction      the transaction
   * @param idSecurityTransfer the security transfer the transaction belongs to, null for an ordinary transaction
   * @param units              units of the transaction, null for one without units
   * @param unitsAfter         units of the account after the transaction, on the split basis of the report date
   * @param costBaseBefore     cost basis of the account before the transaction
   * @param costBaseAfter      cost basis of the account after the transaction
   * @param flowSC             change of the position flow: positive into the position, negative out of it
   * @param incomeSC           change of the income of the account
   * @param gainLoss           realized result of a sale, 0 for other types
   * @param costBaseRemoved    cost a sale removed from the cost basis, 0 for other types
   * @param consumedLots       lots a FIFO or LIFO sale consumed, null otherwise
   * @param bookedRate         rate into the main currency known from the transaction itself, 1 for a security in the
   *                           main currency; null when the rate of the point applies
   * @param marginPositions    for a margin instrument the open positions of the account after the transaction, null for
   *                           any other security. Margin events carry the result of an opening or a close in
   *                           {@code gainLoss} and a finance cost in {@code incomeSC}; the cost basis and flow fields
   *                           are not read.
   * @param marginOpening      whether the transaction opens a margin position
   * @param exDate             ex-date of a dividend, null when unknown or for other types. The price falls on the
   *                           ex-date while the dividend is booked on its payment date, so the drawdown takes the
   *                           outflow on the ex-date; income and results stay on the payment date.
   * @param spinOff            the spin-off side of a spin-off leg, null for every other transaction
   */
  public record Event(LocalDate date, Integer idSecurityaccount, TransactionType type, Integer idTransaction,
      Integer idSecurityTransfer, Double units, double unitsAfter, double costBaseBefore, double costBaseAfter,
      double flowSC, double incomeSC, double gainLoss, double costBaseRemoved, List<ConsumedLot> consumedLots,
      Double bookedRate, List<MarginPosition> marginPositions, boolean marginOpening, LocalDate exDate,
      SpinOff spinOff) {

    public Event(LocalDate date, Integer idSecurityaccount, TransactionType type, Integer idTransaction,
        Integer idSecurityTransfer, Double units, double unitsAfter, double costBaseBefore, double costBaseAfter,
        double flowSC, double incomeSC, double gainLoss, double costBaseRemoved, List<ConsumedLot> consumedLots,
        Double bookedRate, List<MarginPosition> marginPositions, boolean marginOpening, LocalDate exDate) {
      this(date, idSecurityaccount, type, idTransaction, idSecurityTransfer, units, unitsAfter, costBaseBefore,
          costBaseAfter, flowSC, incomeSC, gainLoss, costBaseRemoved, consumedLots, bookedRate, marginPositions,
          marginOpening, exDate, null);
    }

    /** A copy with another spin-off side, used to add the main currency amount of a child purchase. */
    public Event withSpinOff(SpinOff newSpinOff) {
      return new Event(date, idSecurityaccount, type, idTransaction, idSecurityTransfer, units, unitsAfter,
          costBaseBefore, costBaseAfter, flowSC, incomeSC, gainLoss, costBaseRemoved, consumedLots, bookedRate,
          marginPositions, marginOpening, exDate, newSpinOff);
    }

    boolean isSpinOffRole(SecurityActionRole role) {
      return spinOff != null && spinOff.role() == role;
    }

    boolean isPurchase() {
      return type == TransactionType.ACCUMULATE;
    }

    boolean isSale() {
      return type == TransactionType.REDUCE;
    }

    boolean isMargin() {
      return marginPositions != null;
    }
  }

  /**
   * One open position of a margin instrument (CFD, Forex). Its entry value is the value of the units still open at the
   * opening price, without costs; for a long position the value is units * value per point * close, for a short
   * position the entry value plus the open result, so a falling price raises it.
   *
   * @param idOpenTransaction the opening transaction
   * @param shortPosition     whether the position was opened by a sale
   * @param openUnits         units still open, as an absolute amount on the split basis of the price history
   * @param entry             entry value of the open units
   * @param valuePerPoint     value of one point of the price per unit
   */
  public record MarginPosition(Integer idOpenTransaction, boolean shortPosition, double openUnits, double entry,
      double valuePerPoint) {

    double openResult(double close) {
      double market = openUnits * valuePerPoint * close;
      return shortPosition ? entry - market : market - entry;
    }
  }

  /**
   * Value of the position on one trading day, summed over the holding rows of the scope.
   *
   * @param date          the trading day
   * @param unitsAsTraded units held as traded
   * @param valueSC       value in the currency of the security
   * @param close         the close or the period price the value uses, null when absent
   * @param rateMC        closing rate into the main currency, 1 without currency pair, null when it is missing
   * @param negativeUnits whether a row of that day has negative units
   * @param notValued     whether a row of that day has no usable price
   */
  public record DailyValue(LocalDate date, double unitsAsTraded, double valueSC, Double close, Double rateMC,
      boolean negativeUnits, boolean notValued) {
  }

  /**
   * Everything the series of one phase need.
   *
   * @param idSecuritycurrency the security
   * @param securityName       its name
   * @param securityCurrency   its currency
   * @param mainCurrency       the main currency of the scope
   * @param scopeName          name of the tenant, portfolio or account
   * @param margin             whether the security is a margin instrument; its daily values carry only the close and
   *                           the rate, the value is formed from the open positions of the events
   * @param precisionSC        rounding precision of amounts in the currency of the security
   * @param precisionMC        rounding precision of amounts in the main currency
   * @param events             all events of the scope in ascending date order
   * @param phases             all phases from {@link #phases(List)}
   * @param phase              the phase to calculate
   * @param dailyValues        daily values of the phase
   * @param sessions           session days of the security's stock exchange within the phase
   * @param today              the client's calendar day
   */
  public record Input(Integer idSecuritycurrency, String securityName, String securityCurrency, String mainCurrency,
      String scopeName, boolean margin, int precisionSC, int precisionMC, List<Event> events, List<Phase> phases,
      Phase phase, List<DailyValue> dailyValues, Set<LocalDate> sessions, LocalDate today) {

    boolean hasMain() {
      return !securityCurrency.equals(mainCurrency);
    }
  }

  /** A phase while it is collected, with its result growing until it is closed. */
  private static final class PhaseBuilder {
    final LocalDate from;
    LocalDate to;
    double resultSC;

    PhaseBuilder(LocalDate from) {
      this.from = from;
    }
  }

  /**
   * Derives the holding phases from the events of all accounts of the scope.
   *
   * @param events the events in ascending date order
   * @return the phases, newest first
   */
  public static List<Phase> phases(List<Event> events) {
    List<PhaseBuilder> builders = new ArrayList<>();
    Map<Integer, Double> units = new HashMap<>();
    PhaseBuilder open = null;
    for (Map.Entry<LocalDate, List<Event>> day : byDate(events).entrySet()) {
      double realized = 0;
      double income = 0;
      for (Event event : day.getValue()) {
        units.put(event.idSecurityaccount(), event.unitsAfter());
        realized += event.isSale() || event.isMargin() ? event.gainLoss() : 0;
        income += event.incomeSC();
      }
      boolean flat = isFlat(units);
      if (open == null && !flat) {
        open = new PhaseBuilder(day.getKey());
        builders.add(open);
      } else if (open == null && !builders.isEmpty()) {
        builders.getLast().resultSC += income;
        continue;
      }
      if (open != null) {
        open.resultSC += realized + income;
        if (flat) {
          open.to = day.getKey();
          open = null;
        }
      }
    }
    List<Phase> phases = new ArrayList<>();
    for (PhaseBuilder b : builders) {
      phases.add(new Phase(b.from, b.to, b.to == null, b.to == null ? null : b.resultSC));
    }
    Collections.reverse(phases);
    return phases;
  }

  /**
   * Picks the requested phase or the newest one.
   *
   * @param phases        the phases, newest first
   * @param requestedFrom first day of the requested phase, null for the newest
   * @return the phase, null when there is none
   */
  public static Phase choose(List<Phase> phases, LocalDate requestedFrom) {
    if (phases.isEmpty()) {
      return null;
    }
    return phases.stream().filter(p -> p.from().equals(requestedFrom)).findFirst().orElse(phases.getFirst());
  }

  /**
   * A result without series, for a scope without a phase or an instrument the chart does not support.
   *
   * @param input  identification of the security and the scope; events, phase and daily values are not read
   * @param remark the reason
   * @return the empty result
   */
  public static SecurityPositionSeriesResult empty(Input input, SecurityPositionSeriesRemark remark) {
    return new SecurityPositionSeriesResult(input.idSecuritycurrency(), input.securityName(),
        input.securityCurrency(), input.mainCurrency(), input.scopeName(), input.margin(), input.phases(), null, null,
        null, List.of(remark), List.of(), List.of(), null, null, List.of());
  }

  /**
   * Calculates the series of the phase of the input.
   *
   * @param input events, daily values and the phase
   * @return the series, rounded for output
   */
  public static SecurityPositionSeriesResult calculate(Input input) {
    Calculation calc = new Calculation(input);
    calc.run();
    return calc.result();
  }

  /**
   * Main currency amount of the cost a spin-off moved out of one account of the parent. The events of that account are
   * walked with the rules of the chart, purchases at their rate and sales by their consumed lots or proportionally,
   * up to the parent sale of the spin-off. The child chart starts its inherited lots with this amount, so the invested
   * capital of parent and child together stays the same across the action date and no currency result is realized.
   *
   * @param accountEvents       the events of one account of the parent, in the order of the replay
   * @param idSecurityActionApp the application of the spin-off
   * @param rateOf              rate of an event into the main currency, null when it is unknown
   * @return the moved amount, incomplete when the parent sale is missing or a rate was unknown
   */
  public static SpinOffTransfer spinOffTransfer(List<Event> accountEvents, Integer idSecurityActionApp,
      Function<Event, Double> rateOf) {
    AccountState account = new AccountState();
    for (Event e : accountEvents) {
      if (e.isMargin()) {
        continue;
      }
      if (e.isSpinOffRole(SecurityActionRole.SPIN_OFF_SALE)
          && idSecurityActionApp.equals(e.spinOff().idSecurityActionApp())) {
        return account.spinOffSale(e);
      }
      Double rate = rateOf.apply(e);
      account.broken |= rate == null;
      account.applyCost(e, rate == null ? 0 : rate);
    }
    return new SpinOffTransfer(0, Map.of(), false);
  }

  static boolean isFlat(Map<Integer, Double> unitsByAccount) {
    double sum = 0;
    for (double units : unitsByAccount.values()) {
      sum += units;
    }
    return Math.abs(sum) <= FLAT_UNITS;
  }

  static NavigableMap<LocalDate, List<Event>> byDate(List<Event> events) {
    NavigableMap<LocalDate, List<Event>> byDate = new TreeMap<>();
    events.forEach(e -> byDate.computeIfAbsent(e.date(), _ -> new ArrayList<>()).add(e));
    return byDate;
  }

  /**
   * State of one security account while the phase is walked. For a margin instrument {@code costBase} and
   * {@code investedMC} hold the entry values of its open positions.
   */
  private static final class AccountState {
    double costBase;
    double investedMC;
    /** Main currency rate of each purchase lot, keyed like the lots of the cost ledger. */
    final Map<LotKey, Double> purchaseRates = new HashMap<>();
    List<MarginPosition> positions = List.of();
    /** A rate of a lot or of an event was unknown, so {@link #investedMC} is incomplete. */
    boolean broken;

    /**
     * Applies the cost side of an event that is no margin event: a purchase adds its cost at its rate, a sale removes
     * the historical amount of what it consumed, a spin-off leg moves cost without purchase or realized result.
     *
     * @return the cost a purchase added, 0 for every other event
     */
    double applyCost(Event e, double rate) {
      double purchased = 0;
      if (e.spinOff() != null) {
        if (e.spinOff().role() == SecurityActionRole.SPIN_OFF_SALE) {
          spinOffSale(e);
        } else if (e.spinOff().role() == SecurityActionRole.SPIN_OFF_CHILD) {
          spinOffChild(e);
        }
      } else if (e.isPurchase()) {
        purchased = e.costBaseAfter() - e.costBaseBefore();
        investedMC += purchased * rate;
        purchaseRates.put(new LotKey(e.idTransaction(), null), rate);
      } else if (e.isSale()) {
        investedMC -= removedBySale(e);
      }
      costBase = e.costBaseAfter();
      return purchased;
    }

    /** Main currency amount a sale removes: its consumed lots at their purchase rates, otherwise proportionally. */
    double removedBySale(Event e) {
      if (e.consumedLots() == null) {
        return proportional(e.costBaseRemoved(), e.costBaseBefore());
      }
      double removed = 0;
      for (ConsumedLot lot : e.consumedLots()) {
        if (lot.cost() != 0) {
          removed += lot.cost() * rateOf(new LotKey(lot.idTransaction(), lot.originIdTransaction()));
        }
      }
      return removed;
    }

    /**
     * Removes the main currency amount of the cost the parent handed to the child: the moved part of each lot at its
     * purchase rate, or for average cost the proportional share of the invested capital.
     */
    SpinOffTransfer spinOffSale(Event e) {
      List<SpinOffLot> lots = e.spinOff().lots();
      Map<Integer, Double> byRoot = new HashMap<>();
      double removed = 0;
      if (lots.isEmpty()) {
        removed = proportional(e.costBaseRemoved(), e.costBaseBefore());
      } else {
        for (SpinOffLot lot : lots) {
          if (lot.cost() != 0) {
            double part = lot.cost() * rateOf(new LotKey(lot.idTransaction(), lot.originIdTransaction()));
            byRoot.merge(lot.rootTransaction(), part, Double::sum);
            removed += part;
          }
        }
      }
      investedMC -= removed;
      costBase = e.costBaseAfter();
      return new SpinOffTransfer(removed, byRoot, !broken);
    }

    /** Adds the main currency amount the parent handed over and remembers the rate of every inherited lot. */
    void spinOffChild(Event e) {
      SpinOff spinOff = e.spinOff();
      if (spinOff.investedMC() == null) {
        broken = true;
      } else {
        investedMC += spinOff.investedMC();
      }
      for (SpinOffLot lot : spinOff.lots()) {
        if (lot.cost() != 0 && lot.investedMC() != null) {
          purchaseRates.put(new LotKey(lot.idTransaction(), lot.originIdTransaction()), lot.investedMC() / lot.cost());
        }
      }
    }

    private double proportional(double removedCost, double costBefore) {
      return costBefore > 0 ? investedMC * removedCost / costBefore : 0;
    }

    private double rateOf(LotKey key) {
      Double rate = purchaseRates.get(key);
      broken |= rate == null;
      return rate == null ? 0 : rate;
    }
  }

  /**
   * Identity of a purchase lot: the purchase, plus the original purchase for a lot inherited through a spin-off, whose
   * parts all carry the same child purchase.
   */
  private record LotKey(Integer idTransaction, Integer originIdTransaction) {
  }

  /** Running sums and the output columns of one currency. */
  private static final class Column {
    double purchases;
    double income;
    double realized;
    Double firstClose;
    Double maxClose;
    final List<Double> value = new ArrayList<>();
    final List<Double> invested = new ArrayList<>();
    final List<Double> purchasesOut = new ArrayList<>();
    final List<Double> incomeOut = new ArrayList<>();
    final List<Double> realizedOut = new ArrayList<>();
    final List<Double> openResult = new ArrayList<>();
    final List<Double> totalResult = new ArrayList<>();
    final List<Double> openPerformance = new ArrayList<>();
    final List<Double> totalPerformance = new ArrayList<>();
    final List<Double> priceChange = new ArrayList<>();
    final List<Double> priceDrawdown = new ArrayList<>();
    final List<Step> steps = new ArrayList<>();
  }

  /** The walk over the points of one phase. */
  private static final class Calculation {
    private final Input input;
    private final Phase phase;
    private final LocalDate lastDay;
    private final Map<LocalDate, DailyValue> values = new HashMap<>();
    private final Map<Integer, AccountState> accounts = new HashMap<>();
    private final NavigableMap<LocalDate, Double> unitsAtClose = new TreeMap<>();
    private final List<Event> phaseEvents = new ArrayList<>();
    private final List<Event> trailingIncome = new ArrayList<>();
    /** Dividends whose drawdown outflow falls on their ex-date, ordered by it. */
    private final List<Event> exDateFlows = new ArrayList<>();
    private int nextExDateFlow;
    private final Set<SecurityPositionSeriesRemark> remarks = EnumSet.noneOf(SecurityPositionSeriesRemark.class);
    private final List<LocalDate> points = new ArrayList<>();
    private final List<Double> unitsOut = new ArrayList<>();
    private final Column sc = new Column();
    private final Column mc = new Column();
    private boolean mcBroken;
    private Double lastRate;
    private int nextEvent;

    Calculation(Input input) {
      this.input = input;
      this.phase = input.phase();
      this.lastDay = phase.to() != null ? phase.to() : input.today();
      input.dailyValues().forEach(dv -> values.put(dv.date(), dv));
    }

    void run() {
      splitEvents();
      collectPoints();
      for (int i = 0; i < points.size(); i++) {
        LocalDate point = points.get(i);
        boolean closing = point.equals(phase.to());
        DailyValue dv = values.get(point);
        if (dv != null && dv.rateMC() != null) {
          lastRate = dv.rateMC();
        }
        Map<Object, double[]> flows = applyEventsUntil(point);
        if (closing) {
          trailingIncome.forEach(e -> applyIncome(e, rateOf(e)));
        }
        addPoint(point, closing ? null : dv, flows);
      }
    }

    /** Applies the events before the phase to the account states and sorts the rest into phase and trailing. */
    private void splitEvents() {
      LocalDate nextFrom = nextPhaseFrom();
      Map<Integer, Double> units = new HashMap<>();
      for (Map.Entry<LocalDate, List<Event>> day : byDate(input.events()).entrySet()) {
        LocalDate date = day.getKey();
        for (Event e : day.getValue()) {
          if (date.isBefore(phase.from())) {
            account(e).costBase = e.costBaseAfter();
          } else if (!date.isAfter(lastDay)) {
            phaseEvents.add(e);
          } else if (phase.to() != null && date.isBefore(nextFrom) && e.incomeSC() != 0 && !e.isPurchase()
              && !e.isSale()) {
            trailingIncome.add(e);
          }
          units.put(e.idSecurityaccount(), e.unitsAfter());
        }
        unitsAtClose.put(date, units.values().stream().mapToDouble(Double::doubleValue).sum());
      }
      markInternalTransfers();
      if (phaseEvents.stream().anyMatch(e -> e.spinOff() != null)) {
        remarks.add(SecurityPositionSeriesRemark.SPIN_OFF);
      }
      phaseEvents.stream().filter(this::flowsOnExDate).forEach(exDateFlows::add);
      trailingIncome.stream().filter(this::flowsOnExDate).forEach(exDateFlows::add);
      exDateFlows.sort(Comparator.comparing(Event::exDate));
    }

    /**
     * Whether the drawdown takes the outflow of a dividend on its ex-date: the ex-date is known, lies before the
     * payment date and within the phase. The price falls on the ex-date, so the outflow there keeps the drop from
     * counting as a decline between ex-date and payment.
     */
    private boolean flowsOnExDate(Event e) {
      return !e.isMargin() && e.exDate() != null && e.exDate().isBefore(e.date())
          && !e.exDate().isBefore(phase.from()) && !e.exDate().isAfter(lastDay);
    }

    private LocalDate nextPhaseFrom() {
      return input.phases().stream().map(Phase::from).filter(f -> f.isAfter(phase.from()))
          .min(Comparator.naturalOrder()).orElse(LocalDate.MAX);
    }

    private void markInternalTransfers() {
      Set<Integer> seen = new HashSet<>();
      for (Event e : phaseEvents) {
        if (e.idSecurityTransfer() != null && !seen.add(e.idSecurityTransfer())) {
          remarks.add(SecurityPositionSeriesRemark.INTERNAL_TRANSFER);
        }
      }
    }

    /** Valued days of the phase plus its closing day, and the remarks for the days left out. */
    private void collectPoints() {
      TreeSet<LocalDate> days = new TreeSet<>();
      for (DailyValue dv : input.dailyValues()) {
        LocalDate day = dv.date();
        if (day.isBefore(phase.from()) || day.isAfter(lastDay) || isFlatOn(day)) {
          continue;
        }
        if (dv.negativeUnits()) {
          remarks.add(SecurityPositionSeriesRemark.NEGATIVE_UNITS_DAYS);
        } else if (!dv.notValued()) {
          days.add(day);
        }
      }
      if (phase.to() != null) {
        days.add(phase.to());
      }
      for (LocalDate session : input.sessions()) {
        if (!session.isBefore(phase.from()) && session.isBefore(input.today()) && !session.isAfter(lastDay)
            && !session.equals(phase.to()) && !isFlatOn(session) && !days.contains(session)) {
          remarks.add(SecurityPositionSeriesRemark.NOT_VALUED_DAYS);
        }
      }
      points.addAll(days);
    }

    private boolean isFlatOn(LocalDate day) {
      Map.Entry<LocalDate, Double> entry = unitsAtClose.floorEntry(day);
      return entry == null || Math.abs(entry.getValue()) <= FLAT_UNITS;
    }

    private AccountState account(Event e) {
      return accounts.computeIfAbsent(e.idSecurityaccount(), _ -> new AccountState());
    }

    /**
     * Rate of an event into the main currency: the rate known from the transaction, otherwise the closing rate of its
     * point, or of the last valued day before it when the point has none (the closing day of a phase has no row).
     */
    private Double rateOf(Event e) {
      return e.bookedRate() != null ? e.bookedRate() : lastRate;
    }

    /** Applies the not yet applied phase events up to the point and returns their flows, keyed for netting. */
    private Map<Object, double[]> applyEventsUntil(LocalDate point) {
      Map<Object, double[]> flows = new LinkedHashMap<>();
      while (nextExDateFlow < exDateFlows.size() && !exDateFlows.get(nextExDateFlow).exDate().isAfter(point)) {
        Event e = exDateFlows.get(nextExDateFlow++);
        Double rate = rateOf(e);
        mcBroken |= rate == null;
        flows.put("x" + nextExDateFlow, new double[] { e.flowSC(), e.flowSC() * (rate == null ? 0 : rate) });
      }
      while (nextEvent < phaseEvents.size() && !phaseEvents.get(nextEvent).date().isAfter(point)) {
        Event e = phaseEvents.get(nextEvent++);
        Double rate = rateOf(e);
        mcBroken |= rate == null;
        double r = rate == null ? 0 : rate;
        double[] eventFlow;
        if (e.isMargin()) {
          eventFlow = applyMargin(e, r);
        } else {
          applyCost(e, r);
          eventFlow = new double[] { e.flowSC(), e.flowSC() * r };
        }
        applyIncome(e, rate);
        if (flowsOnExDate(e)) {
          // Its outflow was taken on the ex-date
          continue;
        }
        Object key = flowKey(e);
        double[] flow = flows.computeIfAbsent(key, _ -> new double[2]);
        flow[0] += eventFlow[0];
        flow[1] += eventFlow[1];
      }
      return flows;
    }

    /**
     * Key under which the flow of an event is netted: the two legs of a security transfer, and the sale and repurchase
     * of a spin-off parent, which together move only the market value of the child out of the position. Every other
     * flow stays apart.
     */
    private Object flowKey(Event e) {
      if (e.idSecurityTransfer() != null) {
        return e.idSecurityTransfer();
      }
      if (e.isSpinOffRole(SecurityActionRole.SPIN_OFF_SALE)
          || e.isSpinOffRole(SecurityActionRole.SPIN_OFF_REPURCHASE)) {
        return "s" + e.spinOff().idSecurityActionApp() + "/" + e.idSecurityaccount();
      }
      return "e" + nextEvent;
    }

    /**
     * Applies an event of a margin instrument: the open positions of the account are replaced by those after the
     * event, an opening adds its entry value to the purchases, the result of an opening (its costs) or a close is
     * realized. The flow is the change of the entry values less the realized result and the finance cost, so a close
     * moves the released entry value and its result out of the position and a cost goes in without raising the value.
     *
     * @return the flow in security and in main currency
     */
    private double[] applyMargin(Event e, double rate) {
      AccountState account = account(e);
      double entryBefore = account.costBase;
      double entryBeforeMC = account.investedMC;
      if (e.marginOpening()) {
        account.purchaseRates.put(new LotKey(e.idTransaction(), null), rate);
      }
      account.positions = e.marginPositions();
      account.costBase = account.positions.stream().mapToDouble(MarginPosition::entry).sum();
      account.investedMC = 0;
      for (MarginPosition position : account.positions) {
        Double positionRate = account.purchaseRates.get(new LotKey(position.idOpenTransaction(), null));
        mcBroken |= positionRate == null;
        account.investedMC += position.entry() * (positionRate == null ? 0 : positionRate);
      }
      if (e.marginOpening()) {
        sc.purchases += account.costBase - entryBefore;
        mc.purchases += account.investedMC - entryBeforeMC;
      }
      sc.realized += e.gainLoss();
      mc.realized += e.gainLoss() * rate;
      double cash = e.gainLoss() + e.incomeSC();
      return new double[] { account.costBase - entryBefore - cash, account.investedMC - entryBeforeMC - cash * rate };
    }

    /**
     * Applies the cost side of an event to its account and the running sums. A spin-off leg only moves invested
     * capital: the parent sale removes the historical main currency amount of the moved cost, the child purchase adds
     * the same amount, and none of them realizes a result. The parent keeps its purchases; the child counts its
     * inherited cost as purchases at that historical amount, so its total performance has a base.
     */
    private void applyCost(Event e, double rate) {
      AccountState account = account(e);
      double investedBefore = account.investedMC;
      if (e.isSale() && e.spinOff() == null) {
        account.applyCost(e, rate);
        sc.realized += e.gainLoss();
        mc.realized += (e.gainLoss() + e.costBaseRemoved()) * rate - (investedBefore - account.investedMC);
      } else if (e.isSpinOffRole(SecurityActionRole.SPIN_OFF_CHILD)) {
        account.applyCost(e, rate);
        sc.purchases += e.costBaseAfter() - e.costBaseBefore();
        mc.purchases += account.investedMC - investedBefore;
      } else {
        double purchased = account.applyCost(e, rate);
        sc.purchases += purchased;
        mc.purchases += purchased * rate;
      }
      mcBroken |= account.broken;
    }

    private void applyIncome(Event e, Double rate) {
      sc.income += e.incomeSC();
      if (rate == null) {
        mcBroken = true;
      } else {
        mc.income += e.incomeSC() * rate;
      }
    }

    private void addPoint(LocalDate point, DailyValue dv, Map<Object, double[]> flows) {
      Double rate = dv == null ? lastRate : dv.rateMC();
      if (dv != null && rate == null && input.hasMain()) {
        remarks.add(SecurityPositionSeriesRemark.MISSING_FX);
      }
      double investedSC = accounts.values().stream().mapToDouble(a -> a.costBase).sum();
      double investedMC = accounts.values().stream().mapToDouble(a -> a.investedMC).sum();
      Double close = dv == null ? null : dv.close();
      double valueSC;
      Double valueMC;
      if (input.margin()) {
        double openResult = close == null ? 0 : marginOpenResult(close);
        valueSC = investedSC + openResult;
        valueMC = rate == null ? null : investedMC + openResult * rate;
        unitsOut.add(accounts.values().stream().flatMap(a -> a.positions.stream())
            .mapToDouble(MarginPosition::openUnits).sum());
      } else {
        valueSC = dv == null ? 0 : dv.valueSC();
        valueMC = dv == null ? Double.valueOf(0) : (rate == null ? null : valueSC * rate);
        unitsOut.add(dv == null ? 0 : dv.unitsAsTraded());
      }
      addColumnPoint(sc, point, valueSC, investedSC, close, flows, 0, false);
      addColumnPoint(mc, point, valueMC, investedMC, close == null || rate == null ? null : close * rate, flows, 1,
          mcBroken);
    }

    private double marginOpenResult(double close) {
      return accounts.values().stream().flatMap(a -> a.positions.stream()).mapToDouble(p -> p.openResult(close))
          .sum();
    }

    private void addColumnPoint(Column col, LocalDate point, Double value, double invested, Double close,
        Map<Object, double[]> flows, int currency, boolean broken) {
      col.value.add(value);
      col.invested.add(broken ? null : invested);
      col.purchasesOut.add(broken ? null : col.purchases);
      col.incomeOut.add(broken ? null : col.income);
      col.realizedOut.add(broken ? null : col.realized);
      boolean valued = value != null && !broken;
      col.openResult.add(valued ? value - invested : null);
      col.totalResult.add(valued ? value - invested + col.income + col.realized : null);
      col.openPerformance.add(valued && invested > FLAT_UNITS ? (value - invested) / invested : null);
      col.totalPerformance.add(valued && col.purchases > 0
          ? (value - invested + col.income + col.realized) / col.purchases : null);
      addPrice(col, close);
      double in = 0;
      double out = 0;
      for (double[] flow : flows.values()) {
        in += Math.max(0, flow[currency]);
        out += Math.max(0, -flow[currency]);
      }
      col.steps.add(new Step(point, broken ? null : value, in, out));
    }

    private static void addPrice(Column col, Double close) {
      if (close == null) {
        col.priceChange.add(null);
        col.priceDrawdown.add(null);
        return;
      }
      if (col.firstClose == null) {
        col.firstClose = close;
      }
      col.maxClose = col.maxClose == null ? close : Math.max(col.maxClose, close);
      col.priceChange.add(close / col.firstClose - 1);
      col.priceDrawdown.add(close / col.maxClose - 1);
    }

    SecurityPositionSeriesResult result() {
      if (mcBroken && input.hasMain()) {
        remarks.add(SecurityPositionSeriesRemark.MISSING_FX);
      }
      List<String> dates = points.stream().map(LocalDate::toString).toList();
      CurrencySeries security = series(sc, input.securityCurrency(), input.precisionSC());
      CurrencySeries main = input.hasMain() ? series(mc, input.mainCurrency(), input.precisionMC()) : null;
      return new SecurityPositionSeriesResult(input.idSecuritycurrency(), input.securityName(),
          input.securityCurrency(), input.mainCurrency(), input.scopeName(), input.margin(), input.phases(),
          phase.from(), phase.to(),
          points.isEmpty() ? null : points.getLast(), new ArrayList<>(remarks), dates, unitsOut, security, main,
          events());
    }

    private static CurrencySeries series(Column col, String currency, int precision) {
      MoneyDrawdown.Result dd = MoneyDrawdown.of(col.steps);
      MoneyDrawdown.Episode e = dd.maxDrawdown();
      MoneyDrawdown.Underwater u = dd.longestUnderwater();
      return new CurrencySeries(currency, amounts(col.value, precision), amounts(col.invested, precision),
          amounts(col.purchasesOut, precision), amounts(col.incomeOut, precision), amounts(col.realizedOut, precision),
          amounts(col.openResult, precision), amounts(col.totalResult, precision), percents(col.openPerformance),
          percents(col.totalPerformance), percents(dd.drawdown()),
          percents(col.priceChange), percents(col.priceDrawdown),
          new DrawdownEpisode(percent(e.depth()), e.peakDate(), e.troughDate(), e.recoveryDate(), e.durationDays()),
          u == null ? null : new UnderwaterRun(u.from(), u.to(), u.durationDays()), percent(dd.current()));
    }

    private List<PositionEvent> events() {
      List<PositionEvent> events = new ArrayList<>();
      for (Event e : phaseEvents) {
        events.add(toPositionEvent(e));
      }
      trailingIncome.forEach(e -> events.add(toPositionEvent(e)));
      return events;
    }

    private PositionEvent toPositionEvent(Event e) {
      double amount;
      if (e.isMargin()) {
        amount = marginAmount(e);
      } else if (e.isPurchase()) {
        amount = e.costBaseAfter() - e.costBaseBefore();
      } else if (e.isSale()) {
        amount = e.gainLoss() + e.costBaseRemoved();
      } else if (e.incomeSC() != 0) {
        amount = e.incomeSC();
      } else {
        amount = Math.abs(e.flowSC());
      }
      return new PositionEvent(e.date(), e.type(), e.units(), DataHelper.round(amount, input.precisionSC()),
          e.idSecurityTransfer() != null);
    }

    /** Entry value of an opening, the result of a close, the amount of a finance cost. */
    private static double marginAmount(Event e) {
      if (e.marginOpening()) {
        return e.marginPositions().stream().filter(p -> p.idOpenTransaction().equals(e.idTransaction()))
            .mapToDouble(MarginPosition::entry).sum();
      }
      return e.incomeSC() != 0 ? e.incomeSC() : e.gainLoss();
    }

    private static List<Double> amounts(List<Double> values, int precision) {
      return values.stream().map(v -> v == null ? null : DataHelper.round(v, precision)).toList();
    }

    private static List<Double> percents(List<Double> ratios) {
      return ratios.stream().map(Calculation::percent).toList();
    }

    private static Double percent(Double ratio) {
      return ratio == null ? null : DataBusinessHelper.roundPercentage(ratio * 100);
    }
  }
}
