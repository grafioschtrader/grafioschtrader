package grafioschtrader.gtnet.handler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.data.EvaluationValue;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetEntity;
import grafioschtrader.entities.GTNetMessage.GTNetMessageParam;
import grafioschtrader.entities.GTNetMessageAnswer;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.repository.GTNetJpaRepository;
import grafioschtrader.repository.GTNetMessageAnswerJpaRepository;
import grafioschtrader.service.GlobalparametersService;

/**
 * Evaluates GTNetMessageAnswer rules to determine automatic responses to incoming requests.
 *
 * Uses EvalEx expression engine to evaluate conditions defined in GTNetMessageAnswer entities. Conditions can reference
 * variables such as:
 * <ul>
 * <li><b>Time variables:</b> {@code hour} (0-23), {@code dayOfWeek} (1=Monday, 7=Sunday)</li>
 * <li><b>Request counters:</b> {@code dailyCount}, {@code dailyLimit}</li>
 * <li><b>My server:</b> {@code MyDailyRequestLimit}, {@code MyTimezone},
 *     {@code MyMaxLimitLastPrice}, {@code MyMaxLimitHistorical}</li>
 * <li><b>Remote server:</b> {@code RemoteDailyRequestLimit}, {@code RemoteTimezone},
 *     {@code RemoteMaxLimitLastPrice}, {@code RemoteMaxLimitHistorical}, {@code RemoteDomainRemoteName}</li>
 * <li><b>Calculated:</b> {@code TimezoneOffsetHours} (decimal hours difference remote - local)</li>
 * <li><b>Connection counts:</b> {@code TotalConnections}, {@code ConnectionsLastPrice}, {@code ConnectionsHistorical}</li>
 * <li><b>Message:</b> {@code Message} (free-text message content from the request)</li>
 * <li>Any parameter from the message payload</li>
 * </ul>
 *
 * Rules are evaluated in priority order (lowest priority value first). The first matching condition determines the
 * response. If no condition matches, the message awaits manual admin review.
 *
 * @see GTNetMessageAnswer for the rule configuration entity
 */
@Component
public class GTNetResponseResolver {

  private static final Logger log = LoggerFactory.getLogger(GTNetResponseResolver.class);

  @Autowired
  private GTNetMessageAnswerJpaRepository gtNetMessageAnswerJpaRepository;

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  /**
   * Attempts to resolve an automatic response for the given request message code.
   *
   * @param requestCode the incoming message code
   * @param remoteGTNet the remote GTNet entity (may be null)
   * @param params      message parameters
   * @return resolved response if a rule matches, empty if manual handling required
   */
  public Optional<ResolvedResponse> resolveAutoResponse(GTNetMessageCodeType requestCode, GTNet remoteGTNet,
      Map<String, GTNetMessageParam> params) {
    return resolveAutoResponse(requestCode, remoteGTNet, params, null);
  }

  /**
   * Attempts to resolve an automatic response for the given request message code.
   *
   * @param requestCode the incoming message code
   * @param remoteGTNet the remote GTNet entity (may be null)
   * @param params      message parameters
   * @param message     free-text message content from the request (may be null)
   * @return resolved response if a rule matches, empty if manual handling required
   */
  public Optional<ResolvedResponse> resolveAutoResponse(GTNetMessageCodeType requestCode, GTNet remoteGTNet,
      Map<String, GTNetMessageParam> params, String message) {
    List<GTNetMessageAnswer> rules = gtNetMessageAnswerJpaRepository
        .findByRequestMsgCodeOrderByPriority(requestCode.getValue());
    return resolveAutoResponse(rules, remoteGTNet, params, message);
  }

  /**
   * Resolves auto-response using pre-loaded rules from the context.
   *
   * @param rules       the list of GTNetMessageAnswer rules ordered by priority (may be null or empty)
   * @param remoteGTNet the remote GTNet entity
   * @param params      message parameters
   * @return resolved response if a rule matches, empty otherwise
   */
  public Optional<ResolvedResponse> resolveAutoResponse(List<GTNetMessageAnswer> rules, GTNet remoteGTNet,
      Map<String, GTNetMessageParam> params) {
    return resolveAutoResponse(rules, remoteGTNet, params, null);
  }

  /**
   * Resolves auto-response using pre-loaded rules from the context.
   *
   * @param rules       the list of GTNetMessageAnswer rules ordered by priority (may be null or empty)
   * @param remoteGTNet the remote GTNet entity
   * @param params      message parameters
   * @param message     free-text message content from the request (may be null)
   * @return resolved response if a rule matches, empty otherwise
   */
  public Optional<ResolvedResponse> resolveAutoResponse(List<GTNetMessageAnswer> rules, GTNet remoteGTNet,
      Map<String, GTNetMessageParam> params, String message) {
    if (rules == null || rules.isEmpty()) {
      return Optional.empty();
    }

    // Fetch local GTNet and connection counts for context
    GTNet myGTNet = fetchMyGTNet();
    ConnectionCounts connectionCounts = fetchConnectionCounts();

    EvalExContext evalContext = buildEvalContext(myGTNet, remoteGTNet, params, connectionCounts, message);

    // Evaluate each rule in priority order
    for (GTNetMessageAnswer rule : rules) {
      if (evaluateCondition(rule.getResponseMsgConditional(), evalContext)) {
        return Optional.of(new ResolvedResponse(rule.getResponseMsgCode(), rule.getResponseMsgMessage(),
            rule.getWaitDaysApply()));
      }
    }

    // No condition matched
    return Optional.empty();
  }

  /**
   * Fetches the local GTNet entry for this server instance.
   *
   * @return the local GTNet, or null if not configured
   */
  private GTNet fetchMyGTNet() {
    Integer myEntryId = globalparametersService.getGTNetMyEntryID();
    if (myEntryId == null) {
      return null;
    }
    return gtNetJpaRepository.findById(myEntryId).orElse(null);
  }

  /**
   * Fetches connection counts for all GTNet entries with active data exchange.
   *
   * @return connection counts record
   */
  private ConnectionCounts fetchConnectionCounts() {
    return new ConnectionCounts(
        gtNetJpaRepository.countByAnyAcceptRequest(),
        gtNetJpaRepository.countByLastPriceAccepting(),
        gtNetJpaRepository.countByHistoricalAccepting()
    );
  }

  private EvalExContext buildEvalContext(GTNet myGTNet, GTNet remoteGTNet, Map<String, GTNetMessageParam> params,
      ConnectionCounts connectionCounts, String message) {
    EvalExContext ctx = new EvalExContext();
    LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
    ctx.hour = now.getHour();
    ctx.dayOfWeek = now.getDayOfWeek().getValue();
    ctx.message = message;

    // Populate remote server variables
    if (remoteGTNet != null) {
      ctx.remoteTimezone = remoteGTNet.getTimeZone();
      ctx.remoteDailyRequestLimit = remoteGTNet.getDailyRequestLimit();
      ctx.remoteMaxLimitLastPrice = getMaxLimitForKind(remoteGTNet, GTNetExchangeKindType.LAST_PRICE);
      ctx.remoteMaxLimitHistorical = getMaxLimitForKind(remoteGTNet, GTNetExchangeKindType.HISTORICAL_PRICES);
      ctx.remoteDomainRemoteName = remoteGTNet.getDomainRemoteName();

      // Legacy variables for backwards compatibility
      ctx.dailyCount = remoteGTNet.getGtNetConfig() != null
          && remoteGTNet.getGtNetConfig().getDailyRequestLimitCount() != null
              ? remoteGTNet.getGtNetConfig().getDailyRequestLimitCount()
              : 0;
      ctx.dailyLimit = remoteGTNet.getDailyRequestLimit() != null ? remoteGTNet.getDailyRequestLimit()
          : Integer.MAX_VALUE;
    }

    // Populate local server (My) variables
    if (myGTNet != null) {
      ctx.myTimezone = myGTNet.getTimeZone();
      ctx.myDailyRequestLimit = myGTNet.getDailyRequestLimit();
      ctx.myMaxLimitLastPrice = getMaxLimitForKind(myGTNet, GTNetExchangeKindType.LAST_PRICE);
      ctx.myMaxLimitHistorical = getMaxLimitForKind(myGTNet, GTNetExchangeKindType.HISTORICAL_PRICES);
    }

    // Calculate timezone offset
    ctx.timezoneOffsetHours = calculateTimezoneOffsetHours(
        myGTNet != null ? myGTNet.getTimeZone() : null,
        remoteGTNet != null ? remoteGTNet.getTimeZone() : null
    );

    // Populate connection counts
    if (connectionCounts != null) {
      ctx.totalConnections = connectionCounts.total();
      ctx.connectionsLastPrice = connectionCounts.lastPrice();
      ctx.connectionsHistorical = connectionCounts.historical();
    }

    // Add message parameters as context variables
    if (params != null) {
      for (Map.Entry<String, GTNetMessageParam> entry : params.entrySet()) {
        ctx.messageParams.put(entry.getKey(), entry.getValue().getParamValue());
      }
    }

    return ctx;
  }

  /**
   * Gets the maxLimit from a GTNet's entity of the specified kind.
   *
   * @param gtNet the GTNet to query
   * @param kind  the entity kind
   * @return the maxLimit value, or null if entity not found
   */
  private Short getMaxLimitForKind(GTNet gtNet, GTNetExchangeKindType kind) {
    if (gtNet == null) {
      return null;
    }
    return gtNet.getEntity(kind).map(GTNetEntity::getMaxLimit).orElse(null);
  }

  /**
   * Calculates the timezone offset in decimal hours between local and remote timezones.
   *
   * @param localTimezone  the local timezone identifier (e.g., "Europe/Zurich")
   * @param remoteTimezone the remote timezone identifier
   * @return the offset in hours (positive = remote is ahead, negative = remote is behind), or ZERO if calculation fails
   */
  private BigDecimal calculateTimezoneOffsetHours(String localTimezone, String remoteTimezone) {
    if (localTimezone == null || remoteTimezone == null) {
      return BigDecimal.ZERO;
    }
    try {
      ZonedDateTime now = ZonedDateTime.now();
      ZoneId localZone = ZoneId.of(localTimezone);
      ZoneId remoteZone = ZoneId.of(remoteTimezone);
      int localOffsetSeconds = localZone.getRules().getOffset(now.toInstant()).getTotalSeconds();
      int remoteOffsetSeconds = remoteZone.getRules().getOffset(now.toInstant()).getTotalSeconds();
      int diffSeconds = remoteOffsetSeconds - localOffsetSeconds;
      return BigDecimal.valueOf(diffSeconds).divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
    } catch (Exception e) {
      log.warn("Failed to calculate timezone offset between '{}' and '{}': {}", localTimezone, remoteTimezone,
          e.getMessage());
      return BigDecimal.ZERO;
    }
  }

  private boolean evaluateCondition(String condition, EvalExContext ctx) {
    // Null or empty condition means unconditional match
    if (condition == null || condition.isBlank()) {
      return true;
    }

    try {
      Expression expression = new Expression(condition);

      // Time variables
      expression.with("hour", ctx.hour);
      expression.with("dayOfWeek", ctx.dayOfWeek);

      // Legacy variables for backwards compatibility
      expression.with("dailyCount", ctx.dailyCount);
      expression.with("dailyLimit", ctx.dailyLimit);
      if (ctx.remoteTimezone != null) {
        expression.with("requesterTimezone", ctx.remoteTimezone);
      }

      // My (local) server variables
      if (ctx.myDailyRequestLimit != null) {
        expression.with("MyDailyRequestLimit", ctx.myDailyRequestLimit);
      }
      if (ctx.myTimezone != null) {
        expression.with("MyTimezone", ctx.myTimezone);
      }
      if (ctx.myMaxLimitLastPrice != null) {
        expression.with("MyMaxLimitLastPrice", ctx.myMaxLimitLastPrice);
      }
      if (ctx.myMaxLimitHistorical != null) {
        expression.with("MyMaxLimitHistorical", ctx.myMaxLimitHistorical);
      }

      // Remote server variables
      if (ctx.remoteDailyRequestLimit != null) {
        expression.with("RemoteDailyRequestLimit", ctx.remoteDailyRequestLimit);
      }
      if (ctx.remoteTimezone != null) {
        expression.with("RemoteTimezone", ctx.remoteTimezone);
      }
      if (ctx.remoteMaxLimitLastPrice != null) {
        expression.with("RemoteMaxLimitLastPrice", ctx.remoteMaxLimitLastPrice);
      }
      if (ctx.remoteMaxLimitHistorical != null) {
        expression.with("RemoteMaxLimitHistorical", ctx.remoteMaxLimitHistorical);
      }
      if (ctx.remoteDomainRemoteName != null) {
        expression.with("RemoteDomainRemoteName", ctx.remoteDomainRemoteName);
      }

      // Message content
      if (ctx.message != null) {
        expression.with("Message", ctx.message);
      }

      // Calculated variables
      if (ctx.timezoneOffsetHours != null) {
        expression.with("TimezoneOffsetHours", ctx.timezoneOffsetHours);
      }

      // Connection counts
      expression.with("TotalConnections", ctx.totalConnections);
      expression.with("ConnectionsLastPrice", ctx.connectionsLastPrice);
      expression.with("ConnectionsHistorical", ctx.connectionsHistorical);

      // Add message parameters
      for (Map.Entry<String, String> entry : ctx.messageParams.entrySet()) {
        expression.with(entry.getKey(), entry.getValue());
      }

      EvaluationValue result = expression.evaluate();
      return result.getBooleanValue();
    } catch (Exception e) {
      log.warn("Failed to evaluate condition '{}': {}", condition, e.getMessage());
      return false;
    }
  }

  /**
   * Container for resolved response information.
   *
   * @param responseCode  the message code to respond with
   * @param message       optional text message to include
   * @param waitDaysApply cooling-off period in days after this response
   */
  public record ResolvedResponse(GTNetMessageCodeType responseCode, String message, Short waitDaysApply) {
  }

  /**
   * Container for connection counts used in EvalEx expressions.
   *
   * @param total      total GTNet entries with acceptRequest > 0
   * @param lastPrice  count for LAST_PRICE entity kind
   * @param historical count for HISTORICAL_PRICES entity kind
   */
  public record ConnectionCounts(int total, int lastPrice, int historical) {
  }

  /**
   * Internal context for EvalEx expression evaluation.
   * Contains all variables available for use in GTNetMessageAnswer conditional expressions.
   */
  private static class EvalExContext {
    // Time variables
    int hour;
    int dayOfWeek;

    // Legacy variables (for backwards compatibility)
    int dailyCount;
    int dailyLimit;

    // My (local) server variables
    Integer myDailyRequestLimit;
    String myTimezone;
    Short myMaxLimitLastPrice;
    Short myMaxLimitHistorical;

    // Remote server variables
    Integer remoteDailyRequestLimit;
    String remoteTimezone;
    Short remoteMaxLimitLastPrice;
    Short remoteMaxLimitHistorical;
    String remoteDomainRemoteName;

    // Message content
    String message;

    // Calculated variables
    BigDecimal timezoneOffsetHours;

    // Connection counts
    int totalConnections;
    int connectionsLastPrice;
    int connectionsHistorical;

    // Message parameters
    Map<String, String> messageParams = new java.util.HashMap<>();
  }
}
