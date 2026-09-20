package grafioschtrader.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.data.EvaluationValue;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.algo.strategy.model.alerts.AbsoluteValuePriceAlert;
import grafioschtrader.algo.strategy.model.alerts.AlertConfigAdapter;
import grafioschtrader.algo.strategy.model.alerts.ExpressionAlert;
import grafioschtrader.algo.strategy.model.alerts.HoldingGainLosePercentAlert;
import grafioschtrader.algo.strategy.model.alerts.MaCrossingAlert;
import grafioschtrader.algo.strategy.model.alerts.PeriodPriceGainLosePercentAlert;
import grafioschtrader.algo.strategy.model.alerts.RsiThresholdAlert;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.ta.TaIndicatorData;
import grafioschtrader.ta.indicator.calc.ExponentialMovingAverage;
import grafioschtrader.ta.indicator.calc.RelativeStrengthIndex;
import grafioschtrader.ta.indicator.calc.SimpleMovingAverage;
import grafioschtrader.types.AlgoSignalKind;

/**
 * Evaluates the configured security alerts and turns the ones that fire into notifications.
 *
 * <p>
 * All entry points use durable scheduling and freshness checks. {@link #evaluateSimpleAlerts(List)} runs straight after
 * an intraday price batch and covers the configured alerts on fresh observations. {@link #evaluateIndicatorAlerts()}
 * runs on a schedule, refreshes stale prices first and covers all six alert types.
 * {@link #evaluateAlertsForTenant(Integer)} runs both, for one tenant, when a user asks for it.
 * </p>
 *
 * <p>
 * What each tier evaluates is decided by {@link AlgoAlertScopeResolver}, which walks the AlgoTop hierarchy and the
 * standalone alerts and hands back strategy and instrument pairs. Whether a matching condition is worth reporting is
 * decided by {@link AlgoAlertStateService} for the types that are specified as crossings, so that a price which is
 * already past its bound when the alert is created stays quiet until it actually moves across. Whether a report has
 * already been made today is decided by {@link AlgoAlarmRecorder} through a unique key.
 * </p>
 *
 * <p>
 * Missing data never becomes a signal. An instrument without a last price, without enough history for its indicator, or
 * without the holding a holding alert measures is logged as unavailable and skipped; it does not fall back to a
 * different measurement and it does not let a zero valued indicator stand in for a real one.
 * </p>
 */
@Service
public class AlgoAlarmEvaluationService {

  private static final Logger log = LoggerFactory.getLogger(AlgoAlarmEvaluationService.class);

  /** Upper bound on the observations loaded for one indicator, whatever period it asks for. */
  private static final int MAX_HISTORY_OBSERVATIONS = 1200;

  /**
   * Observations loaded beyond the period itself. An exponential average and an RSI both need a run-in before their
   * value settles, and the extra rows cost one index range scan.
   */
  private static final int INDICATOR_WARMUP_OBSERVATIONS = 50;

  @Autowired
  private AlgoAlertStateService algoAlertStateService;

  @Autowired
  private AlgoAlarmRecorder algoAlarmRecorder;

  @Autowired
  private AlgoHoldingGainLossService algoHoldingGainLossService;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private AlgoAlertEvaluationCoordinator coordinator;

  @Autowired
  private AlgoAlarmDeliveryService delivery;

  @Autowired
  private AlgoRebalancingService rebalancing;

  @Autowired
  private AlgoMeanReversionEvaluationService meanReversion;

  /** Evaluates new intraday observations, subject to exchange hours and quote freshness. */
  public void evaluateSimpleAlerts(List<Security> updatedSecurities) {
    coordinator.intraday(updatedSecurities);
  }

  /**
   * Evaluates due background alerts of all six alert types, and the rebalancing of every hierarchy that still owes a
   * plan today.
   *
   * <p>
   * Rebalancing runs beside the alert pass rather than inside it. An alert is scheduled per strategy and instrument and
   * can react to a single fresh quote; a rebalancing compares a whole hierarchy against a whole portfolio at one
   * closing date, so it is evaluated once per AlgoTop per day and would gain nothing from the per instrument leases.
   * </p>
   */
  public void evaluateIndicatorAlerts() {
    coordinator.background();
    rebalancing.evaluateAll();
    for (Integer tenant : meanReversion.tenantIds())
      meanReversion.evaluate(tenant, false);
    delivery.deliverPending();
  }

  /** Whether any active alert or any rebalancing is due; used before enqueuing background work. */
  public boolean hasDueAlerts() {
    return coordinator.hasDueAlerts() || rebalancing.hasDueRebalancing() || delivery.hasDueDeliveries()
        || meanReversion.hasDue();
  }

  /**
   * Explicit evaluation remains limited to the caller's tenant. Unlike the background pass it also recalculates a
   * rebalancing that already ran today: a user who asks for an evaluation after changing an allocation is not helped by
   * being told to come back tomorrow.
   *
   * @param idTenant the tenant whose configuration is evaluated
   */
  public void evaluateAlertsForTenant(Integer idTenant) {
    coordinator.manual(idTenant);
    rebalancing.evaluateForTenant(idTenant);
    meanReversion.evaluate(idTenant, true);
  }

  void evaluateOne(AlgoAlertScope scope, Security security, AlgoStrategyImplementationType type, LocalDate today)
      throws Exception {
    String ambiguity = AlertConfigAdapter.describeAmbiguity(scope.strategy());
    if (ambiguity != null) {
      log.warn(ambiguity);
    }
    switch (type) {
    case AS_OBSERVED_SECURITY_ABSOLUTE_PRICE -> evaluateAbsolutePrice(scope, security, today);
    case AS_HOLDING_TOP_GAIN_LOSE -> evaluateHoldingGainLoss(scope, security, today);
    case AS_OBSERVED_SECURITY_PERIOD_PRICE_GAIN_LOSE_PERCENT -> evaluatePeriodPriceChange(scope, security, today);
    case AS_OBSERVED_SECURITY_MA_CROSSING -> evaluateMaCrossing(scope, security, today);
    case AS_OBSERVED_SECURITY_RSI_THRESHOLD -> evaluateRsiThreshold(scope, security, today);
    case AS_OBSERVED_SECURITY_EXPRESSION -> evaluateExpression(scope, security, today);
    default -> {
      // Every other implementation type belongs to a strategy module that does not evaluate anything yet.
    }
    }
  }

  // == The individual alert types ============================================

  private void evaluateAbsolutePrice(AlgoAlertScope scope, Security security, LocalDate today) {
    Double price = security.getSLast();
    if (price == null) {
      unavailable(scope, "no last price");
      return;
    }
    AbsoluteValuePriceAlert config = AlertConfigAdapter.read(scope.strategy(), AbsoluteValuePriceAlert.class);
    if (config == null) {
      unavailable(scope, "no configuration stored");
      return;
    }
    String fingerprint = AlertConfigAdapter.fingerprint(scope.strategy());
    // The two bounds are separate signals: a price can leave the lower band and enter the upper one on the same day,
    // and both are worth a message.
    if (config.getLowerValue() != null) {
      AlgoCrossingResult crossing = algoAlertStateService.observe(scope.idTenant(),
          scope.strategy().getIdAlgoRuleStrategy(), security.getIdSecuritycurrency(), AlgoAlertStateService.BOUND_LOWER,
          fingerprint, price, config.getLowerValue());
      if (crossing == AlgoCrossingResult.CROSSED_DOWN) {
        fire(scope, AlgoSignalKind.PRICE_ALERT, crossing, today,
            String.format(Locale.ROOT, "{\"bound\":\"lower\",\"threshold\":%s,\"price\":%s,\"direction\":\"BELOW\"}",
                config.getLowerValue(), price));
      }
    }
    if (config.getUpperValue() != null) {
      AlgoCrossingResult crossing = algoAlertStateService.observe(scope.idTenant(),
          scope.strategy().getIdAlgoRuleStrategy(), security.getIdSecuritycurrency(), AlgoAlertStateService.BOUND_UPPER,
          fingerprint, price, config.getUpperValue());
      if (crossing == AlgoCrossingResult.CROSSED_UP) {
        fire(scope, AlgoSignalKind.PRICE_ALERT, crossing, today,
            String.format(Locale.ROOT, "{\"bound\":\"upper\",\"threshold\":%s,\"price\":%s,\"direction\":\"ABOVE\"}",
                config.getUpperValue(), price));
      }
    }
  }

  private void evaluateHoldingGainLoss(AlgoAlertScope scope, Security security, LocalDate today) {
    Double price = security.getSLast();
    if (price == null) {
      unavailable(scope, "no last price");
      return;
    }
    HoldingGainLosePercentAlert config = AlertConfigAdapter.read(scope.strategy(), HoldingGainLosePercentAlert.class);
    if (config == null) {
      unavailable(scope, "no configuration stored");
      return;
    }
    var holding = algoHoldingGainLossService.observe(scope.idTenant(), security, price);
    if (!holding.open()) {
      algoAlertStateService.discard(scope.strategy().getIdAlgoRuleStrategy(), security.getIdSecuritycurrency());
      unavailable(scope, "tenant holds no open position in this instrument");
      return;
    }
    String fingerprint = AlertConfigAdapter.fingerprint(scope.strategy());
    if (config.getLowerValue() != null) {
      var crossing = algoAlertStateService.observe(scope.idTenant(), scope.strategy().getIdAlgoRuleStrategy(),
          security.getIdSecuritycurrency(), "HOLDING_LOWER", fingerprint, price, config.getLowerValue());
      if (crossing == AlgoCrossingResult.CROSSED_DOWN)
        fire(scope, AlgoSignalKind.PRICE_ALERT, crossing, today, String.format(Locale.ROOT,
            "{\"bound\":\"lower\",\"threshold\":%s,\"price\":%s}", config.getLowerValue(), price));
    }
    if (config.getUpperValue() != null) {
      var crossing = algoAlertStateService.observe(scope.idTenant(), scope.strategy().getIdAlgoRuleStrategy(),
          security.getIdSecuritycurrency(), "HOLDING_UPPER", fingerprint, price, config.getUpperValue());
      if (crossing == AlgoCrossingResult.CROSSED_UP)
        fire(scope, AlgoSignalKind.PRICE_ALERT, crossing, today, String.format(Locale.ROOT,
            "{\"bound\":\"upper\",\"threshold\":%s,\"price\":%s}", config.getUpperValue(), price));
    }
    if (holding.gainLossPercentage() == null) {
      // Price-bound observations remain valid even when the percentage cost basis is undefined.
      if (config.getGainPercentage() != null || config.getLosePercentage() != null)
        throw new AlgoAlertEvaluationStateService.PartialEvaluationException(
            "Holding percentage unavailable: cost basis is zero");
      return;
    }
    double percentage = holding.gainLossPercentage();
    boolean gainReached = config.getGainPercentage() != null && percentage >= config.getGainPercentage();
    boolean lossReached = config.getLosePercentage() != null && percentage <= -config.getLosePercentage();
    if (gainReached || lossReached) {
      fire(scope, AlgoSignalKind.HOLDING_GAIN_LOSS,
          gainReached ? AlgoCrossingResult.CROSSED_UP : AlgoCrossingResult.CROSSED_DOWN, today,
          String.format(Locale.ROOT,
              "{\"positionGainLossPercent\":%s,\"gainThreshold\":%s,\"loseThreshold\":%s,\"price\":%s}",
              DataBusinessHelper.roundPercentage(percentage), config.getGainPercentage(), config.getLosePercentage(),
              price));
    }
  }

  private void evaluatePeriodPriceChange(AlgoAlertScope scope, Security security, LocalDate today) {
    Double price = security.getSLast();
    if (price == null) {
      unavailable(scope, "no last price");
      return;
    }
    PeriodPriceGainLosePercentAlert config = AlertConfigAdapter.read(scope.strategy(),
        PeriodPriceGainLosePercentAlert.class);
    if (config == null || config.getDaysInPeriod() == null || config.getDaysInPeriod() <= 0) {
      unavailable(scope, "no usable lookback period configured");
      return;
    }
    LocalDate lookbackDate = today.minusDays(config.getDaysInPeriod());
    // The newest close at or before the requested calendar date. Loading a window and taking its oldest row, which is
    // what happened before, moves the reference to whatever quote the window happened to start at and makes the
    // effective lookback drift with weekends and holidays.
    Optional<Historyquote> reference = historyquoteJpaRepository
        .findFirstByIdSecuritycurrencyAndDateLessThanEqualOrderByDateDesc(security.getIdSecuritycurrency(),
            lookbackDate);
    if (reference.isEmpty() || reference.get().getClose() == 0.0) {
      unavailable(scope, "no closing price at or before " + lookbackDate);
      return;
    }
    double referenceClose = reference.get().getClose();
    double changePercent = (price - referenceClose) / referenceClose * 100.0;
    boolean gainReached = config.getGainPercentage() != null && changePercent >= config.getGainPercentage();
    boolean lossReached = config.getLosePercentage() != null && changePercent <= -config.getLosePercentage();
    if (gainReached || lossReached) {
      fire(scope, AlgoSignalKind.PERIOD_PRICE_CHANGE,
          gainReached ? AlgoCrossingResult.CROSSED_UP : AlgoCrossingResult.CROSSED_DOWN, today,
          String.format(Locale.ROOT,
              "{\"changePercent\":%s,\"gainThreshold\":%s,\"loseThreshold\":%s,\"daysInPeriod\":%d,"
                  + "\"referenceDate\":\"%s\",\"referenceClose\":%s,\"price\":%s}",
              DataBusinessHelper.roundPercentage(changePercent), config.getGainPercentage(), config.getLosePercentage(),
              config.getDaysInPeriod(), reference.get().getDate(), referenceClose, price));
    }
  }

  private void evaluateMaCrossing(AlgoAlertScope scope, Security security, LocalDate today) {
    Double price = security.getSLast();
    if (price == null) {
      unavailable(scope, "no last price");
      return;
    }
    MaCrossingAlert config = AlertConfigAdapter.read(scope.strategy(), MaCrossingAlert.class);
    if (config == null) {
      unavailable(scope, "no configuration stored");
      return;
    }
    List<Historyquote> history = loadHistory(security.getIdSecuritycurrency(), config.getPeriod());
    if (history.size() <= config.getPeriod()) {
      unavailable(scope, "only " + history.size() + " observations for a " + config.getPeriod() + " period average");
      return;
    }
    TaIndicatorData[] maData;
    if ("EMA".equals(config.getIndicatorType())) {
      ExponentialMovingAverage ema = new ExponentialMovingAverage(config.getPeriod(), history.size());
      history.forEach(hq -> ema.addData(hq.getDate(), hq.getClose()));
      maData = ema.getTaIndicatorData();
    } else {
      SimpleMovingAverage sma = new SimpleMovingAverage(config.getPeriod(), history.size());
      history.forEach(hq -> sma.addData(hq.getDate(), hq.getClose()));
      maData = sma.getTaIndicatorData();
    }
    if (maData.length == 0) {
      unavailable(scope, "the moving average could not be computed");
      return;
    }
    double average = maData[maData.length - 1].value;
    AlgoCrossingResult crossing = algoAlertStateService.observe(scope.idTenant(),
        scope.strategy().getIdAlgoRuleStrategy(), security.getIdSecuritycurrency(), AlgoAlertStateService.BOUND_MA,
        AlertConfigAdapter.fingerprint(scope.strategy()), price, average);
    boolean wanted = "ABOVE".equals(config.getCrossDirection()) ? crossing == AlgoCrossingResult.CROSSED_UP
        : crossing == AlgoCrossingResult.CROSSED_DOWN;
    if (wanted) {
      fire(scope, AlgoSignalKind.MA_CROSSING, crossing, today,
          String.format(Locale.ROOT,
              "{\"indicatorType\":\"%s\",\"period\":%d,\"maValue\":%.4f,\"price\":%s,\"crossDirection\":\"%s\"}",
              config.getIndicatorType(), config.getPeriod(), average, price, config.getCrossDirection()));
    }
  }

  private void evaluateRsiThreshold(AlgoAlertScope scope, Security security, LocalDate today) {
    RsiThresholdAlert config = AlertConfigAdapter.read(scope.strategy(), RsiThresholdAlert.class);
    if (config == null) {
      unavailable(scope, "no configuration stored");
      return;
    }
    List<Historyquote> history = loadHistory(security.getIdSecuritycurrency(), config.getRsiPeriod());
    if (history.size() <= config.getRsiPeriod()) {
      unavailable(scope, "only " + history.size() + " observations for a " + config.getRsiPeriod() + " period RSI");
      return;
    }
    RelativeStrengthIndex rsi = new RelativeStrengthIndex(config.getRsiPeriod(), history.size());
    history.forEach(hq -> rsi.addData(hq.getDate(), hq.getClose()));
    TaIndicatorData[] rsiData = rsi.getTaIndicatorData();
    if (rsiData.length == 0) {
      unavailable(scope, "the RSI could not be computed");
      return;
    }
    double value = rsiData[rsiData.length - 1].value;
    String fingerprint = AlertConfigAdapter.fingerprint(scope.strategy());
    if (config.getLowerThreshold() != null) {
      AlgoCrossingResult crossing = algoAlertStateService.observe(scope.idTenant(),
          scope.strategy().getIdAlgoRuleStrategy(), security.getIdSecuritycurrency(),
          AlgoAlertStateService.BOUND_RSI_LOWER, fingerprint, value, config.getLowerThreshold());
      if (crossing == AlgoCrossingResult.CROSSED_DOWN) {
        fire(scope, AlgoSignalKind.RSI_THRESHOLD, crossing, today, String.format(Locale.ROOT,
            "{\"rsiValue\":%.2f,\"threshold\":%s,\"direction\":\"OVERSOLD\"}", value, config.getLowerThreshold()));
      }
    }
    if (config.getUpperThreshold() != null) {
      AlgoCrossingResult crossing = algoAlertStateService.observe(scope.idTenant(),
          scope.strategy().getIdAlgoRuleStrategy(), security.getIdSecuritycurrency(),
          AlgoAlertStateService.BOUND_RSI_UPPER, fingerprint, value, config.getUpperThreshold());
      if (crossing == AlgoCrossingResult.CROSSED_UP) {
        fire(scope, AlgoSignalKind.RSI_THRESHOLD, crossing, today, String.format(Locale.ROOT,
            "{\"rsiValue\":%.2f,\"threshold\":%s,\"direction\":\"OVERBOUGHT\"}", value, config.getUpperThreshold()));
      }
    }
  }

  private void evaluateExpression(AlgoAlertScope scope, Security security, LocalDate today) throws Exception {
    Double price = security.getSLast();
    if (price == null) {
      unavailable(scope, "no last price");
      return;
    }
    ExpressionAlert config = AlertConfigAdapter.read(scope.strategy(), ExpressionAlert.class);
    if (config == null || config.getExpression() == null) {
      unavailable(scope, "no expression configured");
      return;
    }
    String expressionText = config.getExpression();
    grafioschtrader.evalex.AlertExpressionSupport.validate(expressionText);
    Expression expression = grafioschtrader.evalex.AlertExpressionSupport.create(expressionText,
        usesIndicatorFunctions(expressionText) ? loadHistory(security.getIdSecuritycurrency(), MAX_HISTORY_OBSERVATIONS)
            : List.of());

    expression.with("price", BigDecimal.valueOf(price));
    withIfPresent(expression, "prevClose", security.getSPrevClose());
    withIfPresent(expression, "open", security.getSOpen());
    withIfPresent(expression, "high", security.getSHigh());
    withIfPresent(expression, "low", security.getSLow());
    withIfPresent(expression, "volume", security.getSVolume() == null ? null : security.getSVolume().doubleValue());

    EvaluationValue result = expression.evaluate();
    boolean triggered = result.isBooleanValue() ? result.getBooleanValue()
        : result.getNumberValue().compareTo(BigDecimal.ZERO) != 0;
    if (triggered) {
      Object value = result.isBooleanValue() ? result.getBooleanValue() : result.getNumberValue();
      fire(scope, AlgoSignalKind.EXPRESSION, AlgoCrossingResult.NO_CHANGE, today,
          String.format(Locale.ROOT, "{\"expression\":\"%s\",\"result\":%s,\"price\":%s}",
              expressionText.replace("\\", "\\\\").replace("\"", "\\\""), value, price));
    }
  }

  private static void withIfPresent(Expression expression, String name, Double value) {
    if (value != null) {
      expression.with(name, BigDecimal.valueOf(value));
    }
  }

  /**
   * Whether an expression calls one of the indicator functions, and therefore needs price history loaded for it.
   *
   * <p>
   * A regular expression rather than a substring search for {@code "SMA("}: EvalEx accepts a function name in any case
   * and tolerates whitespace before the parenthesis, so {@code sma (200)} is a valid call that the literal search
   * missed. Missing it did not degrade gracefully - the expression was then built without the function and failed on an
   * unknown identifier.
   * </p>
   *
   * @param expression the expression text
   * @return true when SMA, EMA or RSI is called
   */
  static boolean usesIndicatorFunctions(String expression) {
    return expression != null && expression.matches("(?is).*\\b(SMA|EMA|RSI)\\s*\\(.*");
  }

  // == Notification ==========================================================

  private void fire(AlgoAlertScope scope, AlgoSignalKind kind, AlgoCrossingResult crossing, LocalDate today,
      String details) {
    algoAlarmRecorder.record(scope, kind, crossing.direction(), details, today);
  }

  // == Helpers ===============================================================

  private void unavailable(AlgoAlertScope scope, String reason) {
    throw new IllegalStateException(reason);
  }

  /**
   * The most recent closing prices of an instrument, oldest first, enough of them for an indicator of the given period.
   *
   * <p>
   * Counted in observations rather than in calendar days. Subtracting days loses roughly three of every seven to
   * weekends, so a 300 period average asked for over 310 calendar days had about 210 observations and was silently
   * never computed.
   * </p>
   *
   * @param idSecuritycurrency the instrument
   * @param period             the longest period any indicator over this data will ask for
   * @return the quotes in chronological order
   */
  private List<Historyquote> loadHistory(Integer idSecuritycurrency, int period) {
    int wanted = Math.min(period + INDICATOR_WARMUP_OBSERVATIONS, MAX_HISTORY_OBSERVATIONS);
    List<Historyquote> newestFirst = historyquoteJpaRepository
        .findByIdSecuritycurrencyOrderByDateDesc(idSecuritycurrency, Limit.of(wanted));
    List<Historyquote> chronological = new ArrayList<>(newestFirst);
    java.util.Collections.reverse(chronological);
    return chronological;
  }

}
