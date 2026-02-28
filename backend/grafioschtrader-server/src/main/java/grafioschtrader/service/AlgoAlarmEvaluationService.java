package grafioschtrader.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.config.ExpressionConfiguration;
import com.ezylang.evalex.data.EvaluationValue;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.BaseConstants;
import grafiosch.entities.MailEntity;
import grafiosch.repository.MailEntityJpaRepository;
import grafiosch.service.SendMailInternalExternalService;
import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.algo.strategy.model.alerts.ExpressionAlert;
import grafioschtrader.algo.strategy.model.alerts.MaCrossingAlert;
import grafioschtrader.algo.strategy.model.alerts.RsiThresholdAlert;
import grafioschtrader.config.FeatureConfig;
import grafioschtrader.entities.AlgoMessageAlert;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.evalex.EmaFunction;
import grafioschtrader.evalex.RsiFunction;
import grafioschtrader.evalex.SmaFunction;
import grafioschtrader.repository.AlgoMessageAlertJpaRepository;
import grafioschtrader.repository.AlgoSecurityJpaRepository;
import grafioschtrader.repository.AlgoStrategyJpaRepository;
import grafioschtrader.repository.AlgoTopJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.WatchlistJpaRepository;
import grafioschtrader.ta.TaIndicatorData;
import grafioschtrader.ta.indicator.calc.ExponentialMovingAverage;
import grafioschtrader.ta.indicator.calc.RelativeStrengthIndex;
import grafioschtrader.ta.indicator.calc.SimpleMovingAverage;
import grafioschtrader.types.MessageGTComType;
import jakarta.mail.MessagingException;

/**
 * Core alarm evaluation service implementing a two-tier hybrid approach:
 * <ul>
 *   <li><b>Tier 1 (event-driven)</b>: Simple price alerts evaluated after each intraday batch update via
 *       {@link #evaluateSimpleAlerts(List)}.</li>
 *   <li><b>Tier 2 (scheduled)</b>: Indicator alerts (MA crossing, RSI threshold, EvalEx expression) evaluated
 *       via background TaskDataChange with stale price refresh via {@link #evaluateIndicatorAlerts()}.</li>
 * </ul>
 * Both tiers evaluate alerts from two sources:
 * <ol>
 *   <li><b>AlgoTop-attached</b>: strategies under an AlgoTop tree (watchlist-based)</li>
 *   <li><b>Standalone</b>: AlgoSecurity entries with {@code idAlgoSecurityParent = NULL}, created via "Add Alert"
 *       context menu on individual securities</li>
 * </ol>
 * Both tiers are gated by {@link FeatureConfig}: {@code isAlgo()} and {@code isAlert()} must both return {@code true}.
 */
@Service
public class AlgoAlarmEvaluationService {

  private static final Logger log = LoggerFactory.getLogger(AlgoAlarmEvaluationService.class);

  /** Stale threshold: prices older than 4 hours are refreshed before Tier 2 evaluation. */
  private static final long STALE_THRESHOLD_MS = 4 * 60 * 60 * 1000L;

  /** Maximum number of trading days loaded for indicator calculation. */
  private static final int MAX_HISTORY_DAYS = 1200;

  @Autowired
  private FeatureConfig featureConfig;

  @Autowired
  private AlgoTopJpaRepository algoTopJpaRepository;

  @Autowired
  private AlgoSecurityJpaRepository algoSecurityJpaRepository;

  @Autowired
  private AlgoStrategyJpaRepository algoStrategyJpaRepository;

  @Autowired
  private WatchlistJpaRepository watchlistJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private AlgoMessageAlertJpaRepository algoMessageAlertJpaRepository;

  @Autowired
  private MailEntityJpaRepository mailEntityJpaRepository;

  @Autowired
  private SendMailInternalExternalService sendMailInternalExternalService;

  @Autowired
  private MessageSource messageSource;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  // == Tier 1: Event-driven simple alerts ====================================

  /**
   * Evaluates simple alert types on freshly updated securities. Called after each intraday batch update.
   * Evaluates both AlgoTop-attached and standalone alerts.
   *
   * @param updatedSecurities securities whose last price was just refreshed
   */
  public void evaluateSimpleAlerts(List<Security> updatedSecurities) {
    if (!featureConfig.isAlgo() || !featureConfig.isAlert()) {
      return;
    }
    if (updatedSecurities == null || updatedSecurities.isEmpty()) {
      return;
    }
    evaluateAlgoTopSimpleAlerts(updatedSecurities);
    evaluateStandaloneSimpleAlerts(updatedSecurities);
  }

  private void evaluateAlgoTopSimpleAlerts(List<Security> updatedSecurities) {
    List<AlgoTop> activeAlgoTops = algoTopJpaRepository.findByActivatableTrue();
    if (activeAlgoTops.isEmpty()) {
      return;
    }
    // Build a map: watchlistId -> list of AlgoTops
    Map<Integer, List<AlgoTop>> watchlistToAlgoTops = new HashMap<>();
    for (AlgoTop at : activeAlgoTops) {
      watchlistToAlgoTops.computeIfAbsent(at.getIdWatchlist(), k -> new ArrayList<>()).add(at);
    }
    // Build a map: securityId -> Security for quick lookup
    Map<Integer, Security> updatedMap = new HashMap<>();
    for (Security s : updatedSecurities) {
      updatedMap.put(s.getIdSecuritycurrency(), s);
    }
    // For each watchlist that has active AlgoTops, check membership
    for (Map.Entry<Integer, List<AlgoTop>> entry : watchlistToAlgoTops.entrySet()) {
      Integer idWatchlist = entry.getKey();
      List<AlgoTop> algoTops = entry.getValue();
      Watchlist watchlist = watchlistJpaRepository.findById(idWatchlist).orElse(null);
      if (watchlist == null) {
        continue;
      }
      List<? extends Securitycurrency<?>> watchlistSecurities = watchlist.getSecuritycurrencyList();
      if (watchlistSecurities == null) {
        continue;
      }
      for (Securitycurrency<?> sc : watchlistSecurities) {
        Security updated = updatedMap.get(sc.getIdSecuritycurrency());
        if (updated == null) {
          continue;
        }
        for (AlgoTop algoTop : algoTops) {
          evaluateSimpleStrategiesForSecurity(algoTop.getIdTenant(), algoTop.getName(),
              algoTop.getIdAlgoAssetclassSecurity(), updated);
        }
      }
    }
  }

  private void evaluateStandaloneSimpleAlerts(List<Security> updatedSecurities) {
    List<Integer> securityIds = updatedSecurities.stream()
        .map(Security::getIdSecuritycurrency).toList();
    List<AlgoSecurity> standaloneAlerts = algoSecurityJpaRepository
        .findByActivatableTrueAndIdAlgoSecurityParentIsNullAndSecurity_idSecuritycurrencyIn(securityIds);
    if (standaloneAlerts.isEmpty()) {
      return;
    }
    Map<Integer, Security> updatedMap = updatedSecurities.stream()
        .collect(Collectors.toMap(Security::getIdSecuritycurrency, s -> s));
    for (AlgoSecurity as : standaloneAlerts) {
      Security updated = updatedMap.get(as.getSecurity().getIdSecuritycurrency());
      if (updated != null) {
        evaluateSimpleStrategiesForSecurity(as.getIdTenant(), updated.getName(),
            as.getIdAlgoAssetclassSecurity(), updated);
      }
    }
  }

  private void evaluateSimpleStrategiesForSecurity(Integer idTenant, String alertName,
      Integer idAlgoAssetclassSecurity, Security security) {
    List<AlgoStrategy> strategies = algoStrategyJpaRepository
        .findByIdAlgoAssetclassSecurityAndIdTenant(idAlgoAssetclassSecurity, idTenant);
    for (AlgoStrategy strategy : strategies) {
      if (!strategy.isActivatable()) {
        continue;
      }
      AlgoStrategyImplementationType implType = strategy.getAlgoStrategyImplementations();
      if (implType == null) {
        continue;
      }
      switch (implType) {
      case AS_ABSOLUTE_PRICE_ALERT:
        evaluateAbsolutePriceAlert(idTenant, alertName, strategy, security);
        break;
      case AS_HOLDING_GAIN_LOSE_PERCENTAGE_ALERT:
        evaluateGainLossPercentageAlert(idTenant, alertName, strategy, security);
        break;
      case AS_PERIOD_PRICE_GAIN_LOSE_PERCENT_ALERT:
        evaluatePeriodPriceAlert(idTenant, alertName, strategy, security);
        break;
      default:
        // Indicator alerts handled by Tier 2
        break;
      }
    }
  }

  private void evaluateAbsolutePriceAlert(Integer idTenant, String alertName, AlgoStrategy strategy,
      Security security) {
    if (security.getSLast() == null || strategy.getStrategyConfig() == null) {
      return;
    }
    try {
      Map<String, Object> config = objectMapper.readValue(strategy.getStrategyConfig(), Map.class);
      Double lowerValue = toDouble(config.get("lowerValue"));
      Double upperValue = toDouble(config.get("upperValue"));
      double lastPrice = security.getSLast();
      boolean triggered = false;
      String details;
      if (lowerValue != null && lastPrice <= lowerValue) {
        triggered = true;
        details = String.format("{\"threshold\":%.4f,\"actual\":%.4f,\"direction\":\"BELOW\"}", lowerValue, lastPrice);
      } else if (upperValue != null && lastPrice >= upperValue) {
        triggered = true;
        details = String.format("{\"threshold\":%.4f,\"actual\":%.4f,\"direction\":\"ABOVE\"}", upperValue, lastPrice);
      } else {
        return;
      }
      if (triggered) {
        fireAlert(idTenant, alertName, strategy, security.getIdSecuritycurrency(), (byte) 1, details);
      }
    } catch (Exception e) {
      log.warn("Error evaluating absolute price alert for strategy {}: {}", strategy.getIdAlgoRuleStrategy(),
          e.getMessage());
    }
  }

  private void evaluateGainLossPercentageAlert(Integer idTenant, String alertName, AlgoStrategy strategy,
      Security security) {
    if (security.getSLast() == null || security.getSPrevClose() == null || strategy.getStrategyConfig() == null) {
      return;
    }
    try {
      Map<String, Object> config = objectMapper.readValue(strategy.getStrategyConfig(), Map.class);
      Double thresholdPercent = toDouble(config.get("thresholdPercent"));
      if (thresholdPercent == null) {
        return;
      }
      double changePercent = ((security.getSLast() - security.getSPrevClose()) / security.getSPrevClose()) * 100.0;
      if (Math.abs(changePercent) >= Math.abs(thresholdPercent)) {
        String details = String.format("{\"changePercent\":%.2f,\"threshold\":%.2f}", changePercent, thresholdPercent);
        fireAlert(idTenant, alertName, strategy, security.getIdSecuritycurrency(), (byte) 1, details);
      }
    } catch (Exception e) {
      log.warn("Error evaluating gain/loss % alert for strategy {}: {}", strategy.getIdAlgoRuleStrategy(),
          e.getMessage());
    }
  }

  private void evaluatePeriodPriceAlert(Integer idTenant, String alertName, AlgoStrategy strategy, Security security) {
    if (security.getSLast() == null || strategy.getStrategyConfig() == null) {
      return;
    }
    try {
      Map<String, Object> config = objectMapper.readValue(strategy.getStrategyConfig(), Map.class);
      Double thresholdPercent = toDouble(config.get("thresholdPercent"));
      Integer daysInPeriod = toInteger(config.get("daysInPeriod"));
      if (thresholdPercent == null || daysInPeriod == null || daysInPeriod <= 0) {
        return;
      }
      Date fromDate = Date
          .from(LocalDate.now().minusDays(daysInPeriod + 5).atStartOfDay(ZoneId.systemDefault()).toInstant());
      Date toDate = new Date();
      List<Historyquote> hqs = historyquoteJpaRepository
          .findByIdSecuritycurrencyAndDateBetweenOrderByDate(security.getIdSecuritycurrency(), fromDate, toDate);
      if (hqs.isEmpty()) {
        return;
      }
      // Use the oldest price in the period as the reference
      double referenceClose = hqs.get(0).getClose();
      if (referenceClose == 0) {
        return;
      }
      double changePercent = ((security.getSLast() - referenceClose) / referenceClose) * 100.0;
      if (Math.abs(changePercent) >= Math.abs(thresholdPercent)) {
        String details = String.format(
            "{\"changePercent\":%.2f,\"threshold\":%.2f,\"daysInPeriod\":%d,\"referenceClose\":%.4f}", changePercent,
            thresholdPercent, daysInPeriod, referenceClose);
        fireAlert(idTenant, alertName, strategy, security.getIdSecuritycurrency(), (byte) 1, details);
      }
    } catch (Exception e) {
      log.warn("Error evaluating period price alert for strategy {}: {}", strategy.getIdAlgoRuleStrategy(),
          e.getMessage());
    }
  }

  // == Tier 2: Scheduled indicator alerts ====================================

  /**
   * Evaluates indicator-based alert conditions for all active AlgoTop configurations and standalone alerts.
   * Refreshes stale prices (older than 4 hours) before evaluation.
   */
  public void evaluateIndicatorAlerts() {
    if (!featureConfig.isAlgo() || !featureConfig.isAlert()) {
      return;
    }
    evaluateAlgoTopIndicatorAlerts();
    evaluateStandaloneIndicatorAlerts();
  }

  private void evaluateAlgoTopIndicatorAlerts() {
    List<AlgoTop> activeAlgoTops = algoTopJpaRepository.findByActivatableTrue();
    if (activeAlgoTops.isEmpty()) {
      return;
    }
    for (AlgoTop algoTop : activeAlgoTops) {
      try {
        evaluateIndicatorAlertsForAlgoTop(algoTop);
      } catch (Exception e) {
        log.error("Error evaluating indicator alerts for AlgoTop {}: {}", algoTop.getIdAlgoAssetclassSecurity(),
            e.getMessage(), e);
      }
    }
  }

  private void evaluateStandaloneIndicatorAlerts() {
    List<AlgoSecurity> standaloneAlerts = algoSecurityJpaRepository
        .findByActivatableTrueAndIdAlgoSecurityParentIsNull();
    if (standaloneAlerts.isEmpty()) {
      return;
    }
    // Refresh stale prices
    long staleThreshold = System.currentTimeMillis() - STALE_THRESHOLD_MS;
    List<Security> staleSecurities = standaloneAlerts.stream()
        .map(AlgoSecurity::getSecurity)
        .filter(s -> s.getSTimestamp() == null || s.getSTimestamp().getTime() < staleThreshold)
        .collect(Collectors.toList());
    if (!staleSecurities.isEmpty()) {
      log.debug("Refreshing {} stale securities for standalone alerts", staleSecurities.size());
      securityJpaRepository.updateLastPriceByList(staleSecurities);
    }
    // Evaluate indicator strategies
    for (AlgoSecurity as : standaloneAlerts) {
      Security security = as.getSecurity();
      List<AlgoStrategy> strategies = algoStrategyJpaRepository
          .findByIdAlgoAssetclassSecurityAndIdTenant(as.getIdAlgoAssetclassSecurity(), as.getIdTenant());
      for (AlgoStrategy strategy : strategies) {
        evaluateIndicatorStrategy(as.getIdTenant(), security.getName(), strategy, security);
      }
    }
  }

  private void evaluateIndicatorAlertsForAlgoTop(AlgoTop algoTop) {
    Watchlist watchlist = watchlistJpaRepository.findById(algoTop.getIdWatchlist()).orElse(null);
    if (watchlist == null) {
      return;
    }
    List<? extends Securitycurrency<?>> securities = watchlist.getSecuritycurrencyList();
    if (securities == null || securities.isEmpty()) {
      return;
    }
    // Collect stale securities for refresh
    long staleThreshold = System.currentTimeMillis() - STALE_THRESHOLD_MS;
    List<Security> staleSecurities = new ArrayList<>();
    List<Security> allSecurities = new ArrayList<>();
    for (Securitycurrency<?> sc : securities) {
      if (sc instanceof Security security) {
        allSecurities.add(security);
        if (security.getSTimestamp() == null || security.getSTimestamp().getTime() < staleThreshold) {
          staleSecurities.add(security);
        }
      }
    }
    // Refresh stale prices
    if (!staleSecurities.isEmpty()) {
      log.debug("Refreshing {} stale securities for AlgoTop {}", staleSecurities.size(),
          algoTop.getIdAlgoAssetclassSecurity());
      securityJpaRepository.updateLastPriceByList(staleSecurities);
    }
    // Evaluate indicator strategies on each security
    List<AlgoStrategy> strategies = algoStrategyJpaRepository
        .findByIdAlgoAssetclassSecurityAndIdTenant(algoTop.getIdAlgoAssetclassSecurity(), algoTop.getIdTenant());
    for (Security security : allSecurities) {
      for (AlgoStrategy strategy : strategies) {
        evaluateIndicatorStrategy(algoTop.getIdTenant(), algoTop.getName(), strategy, security);
      }
    }
  }

  private void evaluateIndicatorStrategy(Integer idTenant, String alertName, AlgoStrategy strategy,
      Security security) {
    if (!strategy.isActivatable()) {
      return;
    }
    AlgoStrategyImplementationType implType = strategy.getAlgoStrategyImplementations();
    if (implType == null || strategy.getStrategyConfig() == null) {
      return;
    }
    try {
      switch (implType) {
      case AS_MA_CROSSING_ALERT:
        evaluateMaCrossingAlert(idTenant, alertName, strategy, security);
        break;
      case AS_RSI_THRESHOLD_ALERT:
        evaluateRsiThresholdAlert(idTenant, alertName, strategy, security);
        break;
      case AS_EXPRESSION_ALERT:
        evaluateExpressionAlert(idTenant, alertName, strategy, security);
        break;
      default:
        break;
      }
    } catch (Exception e) {
      log.warn("Error evaluating indicator strategy {} for security {}: {}", strategy.getIdAlgoRuleStrategy(),
          security.getIdSecuritycurrency(), e.getMessage());
    }
  }

  private void evaluateMaCrossingAlert(Integer idTenant, String alertName, AlgoStrategy strategy, Security security)
      throws Exception {
    if (security.getSLast() == null) {
      return;
    }
    MaCrossingAlert config = objectMapper.readValue(strategy.getStrategyConfig(), MaCrossingAlert.class);
    List<Historyquote> hqs = loadHistoryForIndicator(security.getIdSecuritycurrency(), config.getPeriod() + 10);
    if (hqs.size() <= config.getPeriod()) {
      return;
    }
    // Compute MA
    TaIndicatorData[] maData;
    if ("EMA".equals(config.getIndicatorType())) {
      ExponentialMovingAverage ema = new ExponentialMovingAverage(config.getPeriod(), hqs.size());
      for (Historyquote hq : hqs) {
        ema.addData(dateToLocalDate(hq.getDate()), hq.getClose());
      }
      maData = ema.getTaIndicatorData();
    } else {
      SimpleMovingAverage sma = new SimpleMovingAverage(config.getPeriod(), hqs.size());
      for (Historyquote hq : hqs) {
        sma.addData(dateToLocalDate(hq.getDate()), hq.getClose());
      }
      maData = sma.getTaIndicatorData();
    }
    if (maData.length == 0) {
      return;
    }
    double lastMaValue = maData[maData.length - 1].value;
    double lastPrice = security.getSLast();
    boolean triggered = false;
    if ("ABOVE".equals(config.getCrossDirection()) && lastPrice > lastMaValue) {
      triggered = true;
    } else if ("BELOW".equals(config.getCrossDirection()) && lastPrice < lastMaValue) {
      triggered = true;
    }
    if (triggered) {
      String details = String.format(
          "{\"indicatorType\":\"%s\",\"period\":%d,\"maValue\":%.4f,\"price\":%.4f,\"crossDirection\":\"%s\"}",
          config.getIndicatorType(), config.getPeriod(), lastMaValue, lastPrice, config.getCrossDirection());
      fireAlert(idTenant, alertName, strategy, security.getIdSecuritycurrency(), (byte) 1, details);
    }
  }

  private void evaluateRsiThresholdAlert(Integer idTenant, String alertName, AlgoStrategy strategy, Security security)
      throws Exception {
    if (security.getSLast() == null) {
      return;
    }
    RsiThresholdAlert config = objectMapper.readValue(strategy.getStrategyConfig(), RsiThresholdAlert.class);
    List<Historyquote> hqs = loadHistoryForIndicator(security.getIdSecuritycurrency(), config.getRsiPeriod() + 20);
    if (hqs.size() <= config.getRsiPeriod()) {
      return;
    }
    RelativeStrengthIndex rsi = new RelativeStrengthIndex(config.getRsiPeriod(), hqs.size());
    for (Historyquote hq : hqs) {
      rsi.addData(dateToLocalDate(hq.getDate()), hq.getClose());
    }
    TaIndicatorData[] rsiData = rsi.getTaIndicatorData();
    if (rsiData.length == 0) {
      return;
    }
    double lastRsi = rsiData[rsiData.length - 1].value;
    boolean triggered = false;
    String direction = null;
    if (config.getLowerThreshold() != null && lastRsi < config.getLowerThreshold()) {
      triggered = true;
      direction = "OVERSOLD";
    } else if (config.getUpperThreshold() != null && lastRsi > config.getUpperThreshold()) {
      triggered = true;
      direction = "OVERBOUGHT";
    }
    if (triggered) {
      String details = String.format(
          "{\"rsiValue\":%.2f,\"lowerThreshold\":%s,\"upperThreshold\":%s,\"direction\":\"%s\"}",
          lastRsi, config.getLowerThreshold(), config.getUpperThreshold(), direction);
      fireAlert(idTenant, alertName, strategy, security.getIdSecuritycurrency(), (byte) 1, details);
    }
  }

  private void evaluateExpressionAlert(Integer idTenant, String alertName, AlgoStrategy strategy, Security security)
      throws Exception {
    if (security.getSLast() == null) {
      return;
    }
    ExpressionAlert config = objectMapper.readValue(strategy.getStrategyConfig(), ExpressionAlert.class);
    String exprStr = config.getExpression();

    // Build expression with or without indicator functions
    Expression expression;
    if (usesIndicatorFunctions(exprStr)) {
      List<Historyquote> hqs = loadHistoryForIndicator(security.getIdSecuritycurrency(), MAX_HISTORY_DAYS);
      ExpressionConfiguration exprConfig = ExpressionConfiguration.defaultConfiguration()
          .withAdditionalFunctions(
              Map.entry("SMA", new SmaFunction(hqs)),
              Map.entry("EMA", new EmaFunction(hqs)),
              Map.entry("RSI", new RsiFunction(hqs)));
      expression = new Expression(exprStr, exprConfig);
    } else {
      expression = new Expression(exprStr);
    }

    // Inject intraday price variables
    expression.with("price", BigDecimal.valueOf(security.getSLast()));
    if (security.getSPrevClose() != null) {
      expression.with("prevClose", BigDecimal.valueOf(security.getSPrevClose()));
    }
    if (security.getSOpen() != null) {
      expression.with("open", BigDecimal.valueOf(security.getSOpen()));
    }
    if (security.getSHigh() != null) {
      expression.with("high", BigDecimal.valueOf(security.getSHigh()));
    }
    if (security.getSLow() != null) {
      expression.with("low", BigDecimal.valueOf(security.getSLow()));
    }
    if (security.getSVolume() != null) {
      expression.with("volume", BigDecimal.valueOf(security.getSVolume()));
    }

    EvaluationValue result = expression.evaluate();
    boolean triggered = result.isBooleanValue()
        ? result.getBooleanValue()
        : result.getNumberValue().compareTo(BigDecimal.ZERO) != 0;
    if (triggered) {
      Object resultValue = result.isBooleanValue() ? result.getBooleanValue() : result.getNumberValue();
      String details = String.format("{\"expression\":\"%s\",\"result\":%s,\"price\":%.4f}",
          exprStr.replace("\"", "\\\""), resultValue, security.getSLast());
      fireAlert(idTenant, alertName, strategy, security.getIdSecuritycurrency(), (byte) 1, details);
    }
  }

  private static boolean usesIndicatorFunctions(String expression) {
    return expression.contains("SMA(") || expression.contains("EMA(") || expression.contains("RSI(");
  }

  // == Common alert infrastructure ===========================================

  /**
   * Creates an AlgoMessageAlert record and sends a notification to the user, with daily dedup via MailEntity.
   *
   * @param idTenant           tenant to notify
   * @param alertName          name used in the mail subject (AlgoTop name or security name for standalone alerts)
   * @param strategy           the triggered strategy
   * @param idSecuritycurrency the security that triggered the alert
   * @param alarmType          alarm type code
   * @param alarmDetails       JSON details of the triggered condition
   */
  private void fireAlert(Integer idTenant, String alertName, AlgoStrategy strategy, Integer idSecuritycurrency,
      byte alarmType, String alarmDetails) {
    Integer idAlgoStrategy = strategy.getIdAlgoRuleStrategy();
    // Dedup: check if already alerted today for this strategy
    LocalDate today = LocalDate.now();
    List<MailEntity> existingAlerts = mailEntityJpaRepository
        .findAll().stream()
        .filter(me -> MessageGTComType.USER_ALGO_ALARM_TRIGGERED.getValue().equals(me.getMessageComType().getValue())
            && idAlgoStrategy.equals(me.getIdEntity()) && today.equals(me.getMarkDate()))
        .toList();
    if (!existingAlerts.isEmpty()) {
      return; // Already alerted today
    }

    // Create alarm record
    AlgoMessageAlert alert = new AlgoMessageAlert();
    alert.setIdTenant(idTenant);
    alert.setIdAlgoStrategy(idAlgoStrategy);
    alert.setIdSecurityCurrency(idSecuritycurrency);
    alert.setAlarmType(alarmType);
    alert.setAlarmDetails(alarmDetails);
    alert.setAlertTime(Timestamp.from(Instant.now()));
    algoMessageAlertJpaRepository.save(alert);

    // Send notification
    try {
      Locale locale = Locale.ENGLISH;
      String subject = messageSource.getMessage("algo.alarm.subject", new Object[] { alertName }, locale);
      String bodyPrefix = messageSource.getMessage("algo.alarm.mail.body.prefix", null, locale);
      String message = bodyPrefix + "\n" + alarmDetails;

      Integer idMailSendRecv = sendMailInternalExternalService.sendMailInternAndOrExternal(
          BaseConstants.SYSTEM_ID_USER, idTenant, subject, message,
          MessageGTComType.USER_ALGO_ALARM_TRIGGERED);

      MailEntity mailEntity = new MailEntity(MessageGTComType.USER_ALGO_ALARM_TRIGGERED, idAlgoStrategy, today);
      if (idMailSendRecv != null) {
        mailEntity.setIdMailSendRecv(idMailSendRecv);
      }
      mailEntityJpaRepository.save(mailEntity);
    } catch (MessagingException e) {
      log.error("Failed to send alarm notification for strategy {}: {}", idAlgoStrategy, e.getMessage());
    }
  }

  // == Helpers ===============================================================

  private List<Historyquote> loadHistoryForIndicator(Integer idSecuritycurrency, int minDays) {
    int daysToLoad = Math.min(Math.max(minDays, 100), MAX_HISTORY_DAYS);
    Date fromDate = Date
        .from(LocalDate.now().minusDays(daysToLoad).atStartOfDay(ZoneId.systemDefault()).toInstant());
    Date toDate = new Date();
    return historyquoteJpaRepository.findByIdSecuritycurrencyAndDateBetweenOrderByDate(idSecuritycurrency, fromDate,
        toDate);
  }

  private static LocalDate dateToLocalDate(Date date) {
    return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
  }

  private static Double toDouble(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.valueOf(value.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Integer toInteger(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.valueOf(value.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
