package grafiosch.gtnet.handler;

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
import org.springframework.stereotype.Component;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.data.EvaluationValue;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage.GTNetMessageParam;
import grafiosch.entities.GTNetMessageAnswer;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.GTNetTime;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GTNetMessageAnswerJpaRepositoryBase;

/**
 * Evaluates GTNetMessageAnswer rules to determine automatic responses to incoming requests.
 *
 * Uses EvalEx expression engine to evaluate conditions defined in GTNetMessageAnswer entities. Conditions can reference
 * variables such as:
 * <ul>
 * <li><b>Time variables:</b> {@code hour} (0-23), {@code dayOfWeek} (1=Monday, 7=Sunday)</li>
 * <li><b>Request counters:</b> {@code dailyCount} (requests this remote sent us today), {@code dailyLimit} (the budget
 * we grant it, identical to {@code MyDailyRequestLimit})</li>
 * <li><b>My server:</b> {@code MyDailyRequestLimit}, {@code MyTimezone}</li>
 * <li><b>Remote server:</b> {@code RemoteDailyRequestLimit}, {@code RemoteTimezone},
 * {@code RemoteDomainRemoteName}</li>
 * <li><b>Calculated:</b> {@code TimezoneOffsetHours} (decimal hours difference remote - local)</li>
 * <li><b>Connection counts:</b> {@code TotalConnections}, {@code ConnectionsLastPrice},
 * {@code ConnectionsHistorical}</li>
 * <li><b>Message:</b> {@code Message} (free-text message content from the request)</li>
 * <li><b>Remote parameters:</b> {@code param.<name>} - every parameter the request carried, in a namespace of its own
 * so that a value written by the peer can never stand in for one of the trusted variables above</li>
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

  protected final GTNetMessageAnswerJpaRepositoryBase gtNetMessageAnswerJpaRepository;
  protected final GTNetJpaRepository gtNetJpaRepository;
  private final GTNetResponseResolverConfig config;

  public GTNetResponseResolver(GTNetMessageAnswerJpaRepositoryBase gtNetMessageAnswerJpaRepository,
      GTNetJpaRepository gtNetJpaRepository, GTNetResponseResolverConfig config) {
    this.gtNetMessageAnswerJpaRepository = gtNetMessageAnswerJpaRepository;
    this.gtNetJpaRepository = gtNetJpaRepository;
    this.config = config;
  }

  /**
   * Attempts to resolve an automatic response for the given request message code.
   *
   * @param requestCodeValue the incoming message code value
   * @param remoteGTNet      the remote GTNet entity (may be null)
   * @param params           message parameters
   * @return resolved response if a rule matches, empty if manual handling required
   */
  public Optional<ResolvedResponse> resolveAutoResponse(byte requestCodeValue, GTNet remoteGTNet,
      Map<String, GTNetMessageParam> params) {
    return resolveAutoResponse(requestCodeValue, remoteGTNet, params, null);
  }

  /**
   * Attempts to resolve an automatic response for the given request message code.
   *
   * @param requestCodeValue the incoming message code value
   * @param remoteGTNet      the remote GTNet entity (may be null)
   * @param params           message parameters
   * @param message          free-text message content from the request (may be null)
   * @return resolved response if a rule matches, empty if manual handling required
   */
  public Optional<ResolvedResponse> resolveAutoResponse(byte requestCodeValue, GTNet remoteGTNet,
      Map<String, GTNetMessageParam> params, String message) {
    List<GTNetMessageAnswer> rules = gtNetMessageAnswerJpaRepository
        .findByRequestMsgCodeOrderByPriority(requestCodeValue);
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
        if (!isAnswerOfItsRequest(rule)) {
          // A rule is validated when it is saved, but rows predate the validation and the two columns are plain
          // bytes. Answering a handshake with something that answers a data request would be worse than not
          // answering automatically at all, so the rule is skipped and the request waits for an administrator.
          log.warn("Skipping auto-answer rule {}: response code {} does not answer request code {}",
              rule.getIdGtNetMessageAnswer(), rule.getResponseMsgCodeValue(), rule.getRequestMsgCodeValue());
          continue;
        }
        GTNetMessageCode responseCode = lookupMessageCode(rule.getResponseMsgCodeValue());
        return Optional.of(new ResolvedResponse(responseCode, rule.getResponseMsgMessage(), rule.getWaitDaysApply()));
      }
    }

    // No condition matched
    return Optional.empty();
  }

  /**
   * Whether the rule pairs a request with one of the answers the protocol registers for it.
   *
   * @param rule the auto-answer rule about to be applied
   * @return true when the pairing is one the protocol allows
   */
  protected boolean isAnswerOfItsRequest(GTNetMessageAnswer rule) {
    return config.isValidResponse(rule.getRequestMsgCodeValue(), rule.getResponseMsgCodeValue());
  }

  /**
   * Looks up a message code by its byte value. Override in application-specific subclass to provide typed message
   * codes.
   *
   * @param codeValue the byte value
   * @return the message code
   */
  protected GTNetMessageCode lookupMessageCode(byte codeValue) {
    return config.lookupMessageCode(codeValue);
  }

  /**
   * Fetches the local GTNet entry for this server instance.
   *
   * @return the local GTNet, or null if not configured
   */
  private GTNet fetchMyGTNet() {
    Integer myEntryId = config.getMyGTNetEntryId();
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
    return new ConnectionCounts(gtNetJpaRepository.countByAnyAcceptRequest(),
        gtNetJpaRepository.countByLastPriceAccepting(), gtNetJpaRepository.countByHistoricalAccepting());
  }

  private EvalExContext buildEvalContext(GTNet myGTNet, GTNet remoteGTNet, Map<String, GTNetMessageParam> params,
      ConnectionCounts connectionCounts, String message) {
    EvalExContext ctx = new EvalExContext();
    LocalDateTime now = GTNetTime.now();
    ctx.hour = now.getHour();
    ctx.dayOfWeek = now.getDayOfWeek().getValue();
    ctx.message = message;

    // Populate remote server variables
    if (remoteGTNet != null) {
      ctx.remoteTimezone = remoteGTNet.getTimeZone();
      ctx.remoteDailyRequestLimit = remoteGTNet.getDailyRequestLimit();
      ctx.remoteDomainRemoteName = remoteGTNet.getDomainRemoteName();

      ctx.remoteMaxLimitLastPrice = config.getMaxLimitForEntityKind(remoteGTNet, (byte) 0);
      ctx.remoteMaxLimitHistorical = config.getMaxLimitForEntityKind(remoteGTNet, (byte) 1);

      // Legacy variables for backwards compatibility
      ctx.dailyCount = remoteGTNet.getGtNetConfig() != null
          && remoteGTNet.getGtNetConfig().getDailyRequestLimitCount() != null
              ? remoteGTNet.getGtNetConfig().getDailyRequestLimitCount()
              : 0;
    }

    // Populate local server (My) variables
    if (myGTNet != null) {
      ctx.myTimezone = myGTNet.getTimeZone();
      ctx.myDailyRequestLimit = myGTNet.getDailyRequestLimit();

      // dailyCount counts the requests this remote sent us, so the limit it is measured against is ours, not the
      // one the remote publishes. That figure stays available as RemoteDailyRequestLimit.
      ctx.dailyLimit = myGTNet.getDailyRequestLimit() != null ? myGTNet.getDailyRequestLimit() : Integer.MAX_VALUE;

      ctx.myMaxLimitLastPrice = config.getMaxLimitForEntityKind(myGTNet, (byte) 0);
      ctx.myMaxLimitHistorical = config.getMaxLimitForEntityKind(myGTNet, (byte) 1);
    }

    // Calculate timezone offset
    ctx.timezoneOffsetHours = calculateTimezoneOffsetHours(myGTNet != null ? myGTNet.getTimeZone() : null,
        remoteGTNet != null ? remoteGTNet.getTimeZone() : null);

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

      // The remote's own parameters go into a namespace of their own, addressed as param.<name>. They are written by
      // the peer the rule is deciding about, and binding them as bare names let a peer send a parameter called
      // dailyCount or TotalConnections and overwrite the value the condition was meant to test - on the
      // unauthenticated first handshake included. As one structure they can never collide with a trusted variable,
      // and a rule that wants a remote value has to say so.
      expression.with("param", Map.copyOf(ctx.messageParams));

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
  public record ResolvedResponse(GTNetMessageCode responseCode, String message, Short waitDaysApply) {
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
   * Internal context for EvalEx expression evaluation. Contains all variables available for use in GTNetMessageAnswer
   * conditional expressions.
   */
  private static class EvalExContext {
    // Time variables
    int hour;
    int dayOfWeek;

    // Legacy variables (for backwards compatibility)
    int dailyCount;
    int dailyLimit = Integer.MAX_VALUE;

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
