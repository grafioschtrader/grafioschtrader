package grafioschtrader.rest;

import java.util.*;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import grafiosch.entities.User;
import grafiosch.service.DailyLimitService;
import grafiosch.types.OperationType;
import grafioschtrader.config.FeatureConfig;
import grafioschtrader.config.LimitKeyConfig;
import grafioschtrader.entities.*;
import grafioschtrader.repository.*;
import grafioschtrader.service.*;
import grafioschtrader.types.TenantKindType;
import io.swagger.v3.oas.annotations.media.Schema;

/** Authorized read-only diagnostics and bounded manual actions for live alerts. */
@RestController
@RequestMapping("/api/algoalerts")
public class AlgoAlertResource {
  public static final String ACTION_LIMIT = LimitKeyConfig.ENTITY_NAME_ALGO_ALERT_ACTION;
  private final AlgoAlertScopeResolver scopes;
  private final AlgoAlertEvaluationStateJpaRepository evaluations;
  private final AlgoMessageAlertJpaRepository alarms;
  private final AlgoAlarmDeliveryService delivery;
  private final AlgoAlarmEvaluationService evaluator;
  private final TenantJpaRepository tenants;
  private final DailyLimitService limits;
  private final FeatureConfig features;

  @org.springframework.beans.factory.annotation.Autowired
  private AlgoRecommendationJpaRepository recommendations;

  @org.springframework.beans.factory.annotation.Autowired
  private HistoryquoteJpaRepository historyquotes;

  @org.springframework.beans.factory.annotation.Autowired
  private AlgoHierarchyAlertOverviewService hierarchyAlertOverview;

  @org.springframework.beans.factory.annotation.Autowired
  private AlgoMessageAlertRetentionService retention;

  @Schema(description = "Current daily-close strategy decision; quantities are proposals, not fills")
  public record TradingDto(Integer idAlgoStrategy, Integer idSecuritycurrency, String contextName, String strategyName,
      String securityName, String valuationDate, String recommendedAction, Double recommendedUnits,
      Double recommendedAmount, String currency, Double price, String rationale) {
  }

  /** Current daily mean reversion decisions; reads do not evaluate or modify the portfolio. */
  @GetMapping("/trading")
  public List<TradingDto> trading() {
    Integer tenantId = tenant(false).getActualIdTenant();
    Map<String, AlgoAlertScope> pairs = new HashMap<>();
    scopes.resolveForTenant(tenantId).forEach(s -> pairs.put(s.strategy().getId() + ":" + s.security().getId(), s));
    return recommendations
        .findByIdTenantAndTriggerKind(tenantId, grafioschtrader.types.AlgoRebalancingTrigger.MEAN_REVERSION).stream()
        .filter(r -> pairs.containsKey(r.getIdAlgoStrategy() + ":" + r.getIdSecuritycurrency())).map(r -> {
          var scope = pairs.get(r.getIdAlgoStrategy() + ":" + r.getIdSecuritycurrency());
          String name = strategyName(scope.strategy());
          Double price = historyquotes.findByIdSecuritycurrencyAndDate(r.getIdSecuritycurrency(), r.getValuationDate())
              .map(Historyquote::getClose).orElse(null);
          return new TradingDto(r.getIdAlgoStrategy(), r.getIdSecuritycurrency(), scope.contextName(), name,
              scope.security().getName(), r.getValuationDate().toString(), r.getRecommendedAction().name(),
              r.getRecommendedUnits(), r.getRecommendedAmount(), r.getCurrency(), price, r.getRationale());
        }).toList();
  }

  /** Assignment options are supplied by the server, scoped to the authenticated main tenant. */
  @GetMapping("/strategies/{security}")
  public List<AssignmentDto> assignmentOptions(@PathVariable Integer security) {
    return scopes.resolveForTenant(tenant(false).getActualIdTenant()).stream()
        .filter(s -> s.security().getId().equals(security)
            && AlgoMeanReversionEvaluationService.isMeanReversion(s.strategy()))
        .map(AlgoAlertScope::strategy).distinct().map(s -> new AssignmentDto(s.getId(), strategyName(s))).toList();
  }

  public record AssignmentDto(Integer idAlgoRuleStrategy, String name) {
  }

  private static String strategyName(AlgoStrategy strategy) {
    try {
      String name = tools.jackson.databind.json.JsonMapper.builder().build().readTree(strategy.getStrategyConfig())
          .path("strategy_name").asString();
      if (name != null && !name.isBlank())
        return name;
    } catch (tools.jackson.core.JacksonException | IllegalArgumentException _) {
      // A draft may have no configuration yet.
    }
    return strategy.getAlgoStrategyImplementations().name();
  }

  public AlgoAlertResource(AlgoAlertScopeResolver scopes, AlgoAlertEvaluationStateJpaRepository evaluations,
      AlgoMessageAlertJpaRepository alarms, AlgoAlarmDeliveryService delivery, AlgoAlarmEvaluationService evaluator,
      TenantJpaRepository tenants, DailyLimitService limits, FeatureConfig features) {
    this.scopes = scopes;
    this.evaluations = evaluations;
    this.alarms = alarms;
    this.delivery = delivery;
    this.evaluator = evaluator;
    this.tenants = tenants;
    this.limits = limits;
    this.features = features;
  }

  @Schema(description = "One resolved strategy/instrument scope and its latest evaluation")
  public record EvaluationDto(Integer strategyId, Integer securityId, String contextName, String securityName,
      String strategyType, boolean active, String outcome, String reason, String lastAttempt, String lastSuccess,
      String quoteTimestamp) {
  }

  @GetMapping("/evaluations")
  public List<EvaluationDto> evaluations() {
    Integer tenant = tenant(false).getActualIdTenant();
    Map<String, AlgoAlertEvaluationState> states = new HashMap<>();
    evaluations.findByIdTenant(tenant)
        .forEach(s -> states.put(s.getIdAlgoStrategy() + ":" + s.getIdSecuritycurrency(), s));
    return scopes.resolveAlertScopes(tenant, features.isAlgo()).stream()
        .filter(s -> AlgoAlertEvaluationCoordinator.isAlertType(s.strategy().getAlgoStrategyImplementations()))
        .map(s -> {
          var state = states.get(s.strategy().getIdAlgoRuleStrategy() + ":" + s.security().getIdSecuritycurrency());
          return new EvaluationDto(s.strategy().getIdAlgoRuleStrategy(), s.security().getIdSecuritycurrency(),
              s.contextName(), s.security().getName(), s.strategy().getAlgoStrategyImplementations().name(), s.active(),
              state == null ? "NEVER_EVALUATED" : state.getOutcome(), state == null ? null : state.getReason(),
              state == null ? null : text(state.getLastAttempt()), state == null ? null : text(state.getLastSuccess()),
              state == null ? null : text(state.getQuoteTimestamp()));
        }).toList();
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }

  /**
   * The alerts of every AlgoTop hierarchy of the tenant, each marked with whether the live evaluation considers it.
   * Only the assigned monitoring hierarchy is evaluated, so this shows the user which alerts are dormant. Empty while
   * rule-based trading is switched off, since the hierarchy alerts then are not evaluated at all.
   */
  @GetMapping("/hierarchies")
  public List<grafioschtrader.dto.AlgoTopAlertGroupDto> hierarchies() {
    User user = tenant(false);
    return features.isAlgo() ? hierarchyAlertOverview.overview(user.getActualIdTenant(), user.getLanguage())
        : List.of();
  }

  @Schema(description = """
      Signal and per-channel delivery diagnostics of one recorded notification; transport payloads and leases are not
      exposed""")
  public record NotificationDto(Integer id, String contextName, String securityName, String alertTime,
      String deliveryStatus, String deliveryChannels, int deliveryAttempts, String nextAttemptAt,
      String internalCompletedAt, String externalCompletedAt, String deliveryError, String alarmDetails,
      @Schema(description = """
          Whether the user may delete the notification now: its delivery has finished and it is older than the window in
          which it still guards against the same alarm being sent again""") boolean deletable) {
  }

  /**
   * Every recorded notification of the tenant, newest first. The whole list is returned because the client pages, sorts
   * and filters it in its table, like every other table of the application.
   */
  @GetMapping("/notifications")
  public List<NotificationDto> notifications() {
    Integer tenant = tenant(false).getActualIdTenant();
    java.time.LocalDate today = java.time.LocalDate.now();
    return alarms.findByIdTenantOrderByIdAlgoMessageAlertDesc(tenant).stream()
        .map(a -> new NotificationDto(a.getIdAlgoMessageAlert(), a.getContextName(), a.getSecurityName(),
            text(a.getAlertTime()), a.getDeliveryStatus(), a.getDeliveryChannels(), a.getDeliveryAttempts(),
            text(a.getNextAttemptAt()), text(a.getInternalCompletedAt()), text(a.getExternalCompletedAt()),
            a.getDeliveryError(), a.getAlarmDetails(), AlgoMessageAlertRetentionService.isDeletable(a, today)))
        .toList();
  }

  /**
   * Deletes the selected notifications of the tenant. The selection is deleted completely or not at all; it is refused
   * when it contains a notification that is still being delivered or still guards against a repeated alarm. One call
   * counts as one alert action against the daily limit, however many notifications it removes.
   */
  @PostMapping("/notifications/deletes")
  public ResponseEntity<Void> deleteNotifications(@RequestBody List<Integer> ids) {
    User user = tenant(true);
    limits.check(user, ACTION_LIMIT, 1);
    retention.deleteByUser(user.getActualIdTenant(), ids);
    limits.log(user.getIdUser(), ACTION_LIMIT, OperationType.DELETE, 1);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/notifications/{id}/retry")
  public ResponseEntity<Void> retry(@PathVariable Integer id) {
    User user = tenant(true);
    limits.check(user, ACTION_LIMIT, 1);
    delivery.retry(user.getActualIdTenant(), id);
    limits.log(user.getIdUser(), ACTION_LIMIT, OperationType.UPDATE, 1);
    return ResponseEntity.accepted().build();
  }

  /** Shared with the existing compatibility endpoint. */
  public void evaluateNow() {
    User user = tenant(true);
    limits.check(user, ACTION_LIMIT, 1);
    limits.log(user.getIdUser(), ACTION_LIMIT, OperationType.UPDATE, 1);
    evaluator.evaluateAlertsForTenant(user.getActualIdTenant());
  }

  /**
   * Resolves the user and refuses every alert endpoint that is called from a simulation environment.
   *
   * <p>
   * Alerts watch live prices and deliver notifications, so they belong to the user's own portfolio. The tenant examined
   * here is therefore the one the request currently operates in ({@code getIdTenant()}), not the home tenant the alerts
   * themselves are evaluated for ({@code getActualIdTenant()}) - a home tenant is never a simulation copy, so testing
   * it could never refuse anything.
   * </p>
   */
  private User tenant(boolean write) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Tenant tenant = tenants.findById(user.getIdTenant()).orElseThrow();
    if (tenant.getTenantKindType() == TenantKindType.SIMULATION_COPY)
      throw new grafiosch.exceptions.DataViolationException("name", "gt.algo.alert.live.only", null);
    if (write && (user.isTenantAccessReadOnly() || !features.isAlert()))
      throw new SecurityException(grafiosch.BaseConstants.CLIENT_SECURITY_BREACH);
    return user;
  }
}
