package grafioschtrader.service;

import java.time.LocalDate;
import java.util.*;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.config.ExpressionConfiguration;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import grafioschtrader.dto.CustodyFeeConfig;
import grafioschtrader.dto.CustodyFeeConfig.*;
import grafioschtrader.dto.CustodyFeeConfig.Collection;
import grafioschtrader.dto.CustodyOpeningState;
import grafioschtrader.dto.FeeModelConfig;
import grafioschtrader.dto.FeeRule;

/**
 * One account's custody calculation. No database access: quotes and booking are supplied by the replay. Estimates never
 * consume credits, and committed fills are identified so retries cannot spend an allowance twice.
 */
public final class CustodyFeeEngine {
  /**
   * One configuration for every expression. {@code new Expression(text)} builds the default configuration anew on each
   * call, including an instance of every built-in function whose parameters are read by reflection, which a replay
   * evaluating these formulas thousands of times paid for every time. The configuration is never modified here.
   */
  private static final ExpressionConfiguration EVALEX = ExpressionConfiguration.defaultConfiguration();
  public record Holding(double value, String instrument, String assetclass, String isin, String currency, String mic) {
  }

  public record Bill(LocalDate date, String currency, double net, double vat, String details) {
    public double total() {
      return net + vat;
    }
  }

  private final CustodyFeeConfig config;
  private final LocalDate opening;
  private final Map<LocalDate, Double> spent = new HashMap<>();
  private final Map<LocalDate, LocalDate> lastCreditDates = new HashMap<>();
  private final Set<String> committed = new HashSet<>();
  private final Map<Integer, Double> billed = new HashMap<>();
  private final NavigableMap<LocalDate, Double> liabilities = new TreeMap<>();
  private final Double openingCredits;
  /** Chargeable exchanges per billing cycle start: held there or traded there during the cycle. */
  private final Map<LocalDate, Set<String>> exchanges = new HashMap<>();
  /** Chargeable exchanges of the last known holdings; a new cycle starts with them, as those positions carry over. */
  private Set<String> heldExchanges = new HashSet<>();
  private final Set<String> tradedFills = new HashSet<>();
  private boolean settlementRequired;
  private double accrued;
  private LocalDate lastObservation;
  private LocalDate lastBill;

  public CustodyFeeEngine(CustodyFeeConfig config, LocalDate opening, LocalDate end, CustodyOpeningState initial) {
    this.config = config;
    this.opening = opening;
    List<String> errors = validate(config);
    if (!errors.isEmpty())
      throw failure(String.join("; ", errors));
    Period first = period(opening.plusDays(1));
    String currency = first.currency();
    Period previousPeriod = first;
    for (LocalDate date = opening.plusDays(1); !date.isAfter(end); date = date.plusDays(1)) {
      Period p = period(date);
      settlementRequired |= !isZero(p);
      if (p != previousPeriod && !date.equals(cycleStart(p, date)) && !(isZero(p) && isZero(previousPeriod)))
        throw failure("Tariff changes inside a billing cycle require an explicit split-cycle account model");
      previousPeriod = p;
      if (!Objects.equals(currency, p.currency()))
        throw failure("Fee currency changes require a new simulation");
    }
    // Every opening balance is optional; a missing one counts as zero. A paid advance charge is already in opening cash.
    if (initial != null) {
      for (Double value : Arrays.asList(initial.accruedFees(), initial.remainingCredits(), initial.billedThisYear()))
        if (value != null && (!Double.isFinite(value) || value < 0))
          throw failure("Invalid opening balance");
      if (number(initial.remainingCredits()) > number(first.creditAmount()))
        throw failure("Opening credits exceed allowance");
      accrued = number(initial.accruedFees());
      billed.put(opening.getYear(), number(initial.billedThisYear()));
    }
    openingCredits = initial == null ? null : number(initial.remainingCredits());
    liabilities.put(opening, accrued * (1 + number(first.vatRate())));
  }

  public static CustodyFeeConfig parse(String yaml) {
    if (yaml == null || yaml.isBlank())
      return null;
    try {
      grafioschtrader.common.StrictYaml.validate(yaml);
      return new YAMLMapper().readValue(yaml, FeeModelConfig.class).getCustody();
    } catch (Exception e) {
      throw failure("Invalid fee model: " + e.getMessage());
    }
  }

  /** Structural and expression validation also used by the ordinary model editor. */
  public static List<String> validate(CustodyFeeConfig config) {
    List<String> errors = new ArrayList<>();
    if (config == null)
      return errors;
    try {
      if (config.periods() == null || config.periods().isEmpty())
        throw failure("Custody periods are required");
      LocalDate previous = null;
      for (Period p : config.periods().stream().sorted(Comparator.comparing(Period::validFrom)).toList()) {
        LocalDate from = LocalDate.parse(p.validFrom());
        LocalDate to = p.validTo() == null ? LocalDate.MAX : LocalDate.parse(p.validTo());
        if (to.isBefore(from) || previous != null && !from.isAfter(previous))
          throw failure("Invalid or overlapping custody date range");
        previous = to;
        if (p.status() == null || p.source() == null || p.source().isBlank())
          throw failure("Status and source required");
        if (p.status() != Status.VERIFIED && (p.note() == null || p.note().isBlank()))
          throw failure("Assumed/unresolved periods require an explanation");
        if (p.status() == Status.UNRESOLVED)
          continue;
        if (p.currency() == null || !p.currency().matches("[A-Z]{3}") || p.valuation() == null || p.collection() == null
            || p.billingMonths() == null || !Set.of(1, 3, 6, 12).contains(p.billingMonths()) || p.amount() == null
            || p.dayCount() == null)
          throw failure("Incomplete custody schedule");
        if (p.valuation() == Valuation.MONTHLY
            && (p.observationDay() == null || p.observationDay() < 0 || p.observationDay() > 31))
          throw failure("Monthly observation day required (0 = month end)");
        if (p.billingDay() == null || p.billingDay() < 0 || p.billingDay() > 31)
          throw failure("Billing day required (0 = calendar boundary)");
        if (p.collection() == Collection.START && p.valuation() != Valuation.NONE)
          throw failure("Advance billing requires a fixed amount");
        if (p.closingPolicy() == ClosingPolicy.ACCRUED
            && (p.valuation() == Valuation.NONE || p.valuation() == Valuation.PERIOD_END)
            || p.closingPolicy() == ClosingPolicy.FULL_PERIOD && p.valuation() != Valuation.NONE
                && p.valuation() != Valuation.PERIOD_END)
          throw failure("Closing policy does not match valuation mode");
        for (Double v : Arrays.asList(p.minimum(), p.maximum(), p.annualCap(), p.vatRate(), p.creditAmount()))
          if (v != null && (!Double.isFinite(v) || v < 0))
            throw failure("Amounts must be finite and non-negative");
        if (p.minimum() != null && p.maximum() != null && p.minimum() > p.maximum())
          throw failure("Minimum exceeds maximum");
        if (number(p.creditAmount()) > 0
            && (p.collection() != Collection.START || p.billingDay() != 0 || p.creditEligibility() == null))
          throw failure("Credits require advance billing and eligibility");
        new Expression(p.amount(), EVALEX).validate();
        if (p.valuation() == Valuation.NONE)
          evaluate(p.amount(), amountVariables(0, 0, 0, 0));
        if (p.creditEligibility() != null)
          new Expression(p.creditEligibility(), EVALEX).validate();
        if (p.exchangeCondition() != null)
          new Expression(p.exchangeCondition(), EVALEX).validate();
        if (p.valueRules() != null)
          for (FeeRule rule : p.valueRules()) {
            new Expression(rule.getCondition(), EVALEX).validate();
            new Expression(rule.getExpression(), EVALEX).validate();
          }
      }
    } catch (Exception e) {
      errors.add("Custody: " + e.getMessage());
    }
    return errors;
  }

  public Period period(LocalDate date) {
    Period p = config.periods().stream()
        .filter(v -> !date.isBefore(LocalDate.parse(v.validFrom()))
            && (v.validTo() == null || !date.isAfter(LocalDate.parse(v.validTo()))))
        .findFirst().orElseThrow(() -> failure("No custody tariff for " + date));
    if (p.status() == Status.UNRESOLVED)
      throw failure("Unresolved custody tariff on " + date + ": " + p.note());
    return p;
  }

  public String currency(LocalDate date) {
    return period(date.isAfter(opening) ? date : opening.plusDays(1)).currency();
  }

  public boolean requiresSettlement() {
    return settlementRequired;
  }

  public boolean observes(LocalDate date) {
    Period p = period(date);
    return switch (p.valuation()) {
    case NONE -> p.collection() == Collection.END;
    case DAILY -> true;
    case MONTHLY -> date.getDayOfMonth() == day(date, p.observationDay());
    case PERIOD_END -> date.equals(cycleEnd(p, date));
    };
  }

  /** Accrues the annual expression on an observed closing portfolio, before settlement. */
  public void observe(LocalDate date, List<Holding> holdings) {
    observe(date, holdings, false);
  }

  public void observe(LocalDate date, List<Holding> holdings, boolean closing) {
    if ((!observes(date) && !(closing && period(date).valuation() == Valuation.PERIOD_END))
        || date.equals(lastObservation))
      return;
    if (lastObservation != null && date.isBefore(lastObservation))
      throw failure("Observations must be chronological");
    Period p = period(date);
    double accountValue = holdings.stream().mapToDouble(Holding::value).sum();
    Set<String> cycleExchanges = cycleExchanges(p, date);
    Set<String> held = new HashSet<>();
    double assetValue = 0;
    for (Holding holding : holdings) {
      Map<String, Object> vars = variables(holding, accountValue);
      if (chargeableExchange(p, vars))
        held.add(holding.mic());
      double value = holding.value();
      if (p.valueRules() != null) {
        boolean matched = false;
        for (FeeRule rule : p.valueRules())
          if (condition(rule.getCondition(), vars)) {
            value = evaluate(rule.getExpression(), vars);
            matched = true;
            break;
          }
        if (!matched)
          throw failure("No custody value rule for " + holding.isin());
      }
      assetValue += value;
    }
    cycleExchanges.addAll(held);
    heldExchanges = held;
    double annual = evaluate(p.amount(),
        amountVariables(assetValue, accountValue, cycleExchanges.size(), holdings.size()));
    double factor = switch (p.valuation()) {
    case DAILY -> 1.0 / (p.dayCount() == DayCount.ACTUAL_360 ? 360
        : p.dayCount() == DayCount.ACTUAL_ACTUAL ? date.lengthOfYear() : 365);
    case MONTHLY -> 1.0 / 12;
    case PERIOD_END -> p.billingMonths() / 12.0;
    case NONE -> 1.0 / java.time.temporal.ChronoUnit.DAYS.between(cycleStart(p, date), cycleEnd(p, date).plusDays(1));
    };
    if (p.valuation() == Valuation.PERIOD_END)
      accrued = annual * factor;
    else
      accrued += annual * factor;
    lastObservation = date;
    double elapsed = java.time.temporal.ChronoUnit.DAYS.between(cycleStart(p, date), date.plusDays(1));
    double duration = java.time.temporal.ChronoUnit.DAYS.between(cycleStart(p, date), cycleEnd(p, date).plusDays(1));
    double liability = Math.max(accrued, number(p.minimum()) * elapsed / duration);
    if (p.maximum() != null)
      liability = Math.min(liability, p.maximum());
    if (p.annualCap() != null)
      liability = Math.min(liability, Math.max(0, p.annualCap() - billed.getOrDefault(date.getYear(), 0.0)));
    liabilities.put(date, liability * (1 + number(p.vatRate())));
  }

  public boolean bills(LocalDate date, boolean beforeOrders) {
    Period p = period(date);
    if (beforeOrders != (p.collection() == Collection.START))
      return false;
    LocalDate boundary = beforeOrders ? cycleStart(p, date) : cycleEnd(p, date);
    LocalDate due = p.billingDay() == 0 ? boundary : boundary.withDayOfMonth(day(boundary, p.billingDay()));
    return date.equals(due) && !date.equals(lastBill);
  }

  /** Builds a bill without mutation: the caller commits it only after the normal transaction write succeeds. */
  public Bill bill(LocalDate date, int precision) {
    return bill(date, precision, false);
  }

  /** Mid-cycle closure must follow an explicit contractual convention. */
  public void validateClosing(LocalDate date) {
    Period p = period(date);
    if (!isZero(p) && p.collection() == Collection.END && !date.equals(cycleEnd(p, date)) && p.closingPolicy() == null)
      throw failure("Mid-period account closure requires closingPolicy");
  }

  public Bill bill(LocalDate date, int precision, boolean closing) {
    Period p = period(date);
    if (closing)
      validateClosing(date);
    double factor = closing && p.closingPolicy() == ClosingPolicy.PRORATED
        ? (double) java.time.temporal.ChronoUnit.DAYS.between(cycleStart(p, date), date.plusDays(1))
            / java.time.temporal.ChronoUnit.DAYS.between(cycleStart(p, date), cycleEnd(p, date).plusDays(1))
        : 1;
    double net = p.valuation() == Valuation.NONE ? evaluate(p.amount(), amountVariables(0, 0, 0, 0)) : accrued;
    if (p.valuation() == Valuation.NONE || p.valuation() == Valuation.PERIOD_END)
      net *= factor;
    net = Math.max(number(p.minimum()) * factor, net);
    if (p.maximum() != null)
      net = Math.min(net, p.maximum() * factor);
    if (p.annualCap() != null)
      net = Math.min(net, Math.max(0, p.annualCap() - billed.getOrDefault(date.getYear(), 0.0)));
    net = round(net, precision);
    double vat = round(net * number(p.vatRate()), precision);
    return new Bill(date, p.currency(), net, vat, p.validFrom() + "; " + p.status() + "; " + p.valuation() + "; net="
        + net + "; VAT=" + vat + "; " + p.source() + (p.note() == null ? "" : "; " + p.note()));
  }

  public void settled(Bill bill) {
    accrued = 0;
    lastBill = bill.date();
    billed.merge(bill.date().getYear(), bill.net(), Double::sum);
    liabilities.put(bill.date(), 0.0);
  }

  public boolean settledOn(LocalDate date) {
    return date.equals(lastBill);
  }

  public double liability(LocalDate date) {
    var entry = liabilities.floorEntry(date);
    return entry == null ? 0 : entry.getValue();
  }

  /** Credit preview in fee currency. Taxes and other transaction charges must never be passed here. */
  public double credit(LocalDate date, double commission, Map<String, Object> trade) {
    Period p = period(date);
    if (number(p.creditAmount()) == 0 || !condition(p.creditEligibility(), trade))
      return 0;
    LocalDate cycle = cycleStart(p, date);
    if (lastCreditDates.containsKey(cycle) && date.isBefore(lastCreditDates.get(cycle)))
      throw failure("Commission-credit fills must be processed chronologically within a billing period");
    double allowance = openingCredits != null && cycle.equals(cycleStart(period(opening.plusDays(1)), opening))
        ? openingCredits
        : p.creditAmount();
    return Math.max(0, Math.min(commission, allowance - spent.getOrDefault(cycle, 0.0)));
  }

  public void consume(LocalDate date, String fillId, double credit) {
    if (committed.add(fillId)) {
      spent.merge(cycleStart(period(date), date), credit, Double::sum);
      lastCreditDates.merge(cycleStart(period(date), date), date, (a, b) -> a.isAfter(b) ? a : b);
    }
  }

  /**
   * The positions held when the replay opens. They count as held exchanges of the opening cycle even if they are sold
   * before its first observation. Must be called before any {@link #traded} of the opening cycle.
   */
  public void carryIn(List<Holding> holdings) {
    Period p = period(opening.plusDays(1));
    Set<String> held = new HashSet<>();
    for (Holding holding : holdings)
      if (chargeableExchange(p, variables(holding, 0)))
        held.add(holding.mic());
    heldExchanges = held;
  }

  /**
   * Adds the exchange of a booked trade to its billing cycle, so a per-exchange fee is due even for an exchange that
   * holds nothing at the observation. A trade on or before the opening only counts when it lies in the opening cycle.
   *
   * @param date   the trade day
   * @param fillId the identity of the fill; a repeated one is ignored
   * @param trade  mic, instrument, assetclass, isin and currency of the traded instrument
   */
  public void traded(LocalDate date, String fillId, Map<String, Object> trade) {
    LocalDate effective = date.isAfter(opening) ? date : opening.plusDays(1);
    Period p = period(effective);
    if (p.valuation() == Valuation.NONE || !cycleStart(p, date).equals(cycleStart(p, effective))
        || !tradedFills.add(fillId))
      return;
    Map<String, Object> vars = new HashMap<>(trade);
    vars.putIfAbsent("positionValue", 0);
    vars.putIfAbsent("accountValue", 0);
    if (chargeableExchange(p, vars))
      cycleExchanges(p, effective).add((String) vars.get("mic"));
  }

  private Set<String> cycleExchanges(Period p, LocalDate date) {
    return exchanges.computeIfAbsent(cycleStart(p, date), _ -> new HashSet<>(heldExchanges));
  }

  private static boolean chargeableExchange(Period p, Map<String, Object> vars) {
    Object mic = vars.get("mic");
    return mic instanceof String m && !m.isBlank()
        && (p.exchangeCondition() == null || condition(p.exchangeCondition(), vars));
  }

  private static Map<String, Object> amountVariables(double assetValue, double accountValue, int exchangeCount,
      int positionCount) {
    return Map.of("assetValue", assetValue, "accountValue", accountValue, "exchangeCount", exchangeCount,
        "positionCount", positionCount);
  }

  private static Map<String, Object> variables(Holding h, double accountValue) {
    return Map.of("positionValue", h.value(), "accountValue", accountValue, "instrument", h.instrument(), "assetclass",
        h.assetclass(), "isin", h.isin(), "currency", h.currency(), "mic", h.mic() == null ? "" : h.mic());
  }

  private static boolean condition(String expression, Map<String, Object> variables) {
    try {
      return new Expression(expression, EVALEX).withValues(variables).evaluate().getBooleanValue();
    } catch (Exception e) {
      throw failure("Condition: " + e.getMessage());
    }
  }

  private static double evaluate(String expression, Map<String, Object> variables) {
    try {
      double value = new Expression(expression, EVALEX).withValues(variables).evaluate().getNumberValue().doubleValue();
      if (!Double.isFinite(value) || value < 0)
        throw failure("Invalid custody amount");
      return value;
    } catch (Exception e) {
      throw failure("Expression: " + e.getMessage());
    }
  }

  public static LocalDate cycleStart(Period p, LocalDate date) {
    return LocalDate.of(date.getYear(), ((date.getMonthValue() - 1) / p.billingMonths()) * p.billingMonths() + 1, 1);
  }

  private static LocalDate cycleEnd(Period p, LocalDate date) {
    return cycleStart(p, date).plusMonths(p.billingMonths()).minusDays(1);
  }

  private static int day(LocalDate date, int configured) {
    return configured == 0 ? date.lengthOfMonth() : Math.min(configured, date.lengthOfMonth());
  }

  private static boolean isZero(Period p) {
    return ("0".equals(p.amount()) || "0.0".equals(p.amount())) && number(p.minimum()) == 0
        && number(p.creditAmount()) == 0;
  }

  private static double number(Double value) {
    return value == null ? 0 : value;
  }

  private static double round(double value, int precision) {
    return grafiosch.common.DataHelper.round(value, precision);
  }

  private static IllegalArgumentException failure(String message) {
    return new IllegalArgumentException("REPLAY_CUSTODY_FAILED: " + message);
  }
}
