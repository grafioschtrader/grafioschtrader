package grafioschtrader.gtnet.handler;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.data.EvaluationValue;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage.GTNetMessageParam;
import grafioschtrader.entities.GTNetMessageAnswer;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.repository.GTNetMessageAnswerJpaRepository;

/**
 * Evaluates GTNetMessageAnswer rules to determine automatic responses to incoming requests.
 *
 * Uses EvalEx expression engine to evaluate conditions defined in GTNetMessageAnswer entities. Conditions can reference
 * variables such as:
 * <ul>
 * <li>{@code hour} - Current hour of day (0-23)</li>
 * <li>{@code dayOfWeek} - Day of week (1=Monday, 7=Sunday)</li>
 * <li>{@code dailyCount} - Number of requests from this domain today</li>
 * <li>{@code requesterTimezone} - Timezone of the requesting server</li>
 * <li>Any parameter from the message payload</li>
 * </ul>
 *
 * Conditions are evaluated in order (1, 2, 3). The first matching condition determines the response. If no condition
 * matches, the message awaits manual admin review.
 *
 * @see GTNetMessageAnswer for the rule configuration entity
 */
@Component
public class GTNetResponseResolver {

  private static final Logger log = LoggerFactory.getLogger(GTNetResponseResolver.class);

  @Autowired
  private GTNetMessageAnswerJpaRepository gtNetMessageAnswerJpaRepository;

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
    Optional<GTNetMessageAnswer> rulesOpt = gtNetMessageAnswerJpaRepository.findById(requestCode.getValue());
    if (rulesOpt.isEmpty()) {
      return Optional.empty();
    }

    GTNetMessageAnswer rules = rulesOpt.get();
    EvalExContext evalContext = buildEvalContext(remoteGTNet, params);

    // Evaluate condition 1
    if (evaluateCondition(rules.getResponseMsgConditional1(), evalContext)) {
      return Optional.of(
          new ResolvedResponse(rules.getResponseMsgCode1(), rules.getResponseMsgMessage1(), rules.getWaitDaysAplly()));
    }

    // Evaluate condition 2
    if (rules.getResponseMsgCode2() != null && evaluateCondition(rules.getResponseMsgConditional2(), evalContext)) {
      return Optional.of(
          new ResolvedResponse(rules.getResponseMsgCode2(), rules.getResponseMsgMessage2(), rules.getWaitDaysAplly()));
    }

    // Evaluate condition 3
    if (rules.getResponseMsgCode3() != null && evaluateCondition(rules.getResponseMsgConditional3(), evalContext)) {
      return Optional.of(
          new ResolvedResponse(rules.getResponseMsgCode3(), rules.getResponseMsgMessage3(), rules.getWaitDaysAplly()));
    }

    // No condition matched
    return Optional.empty();
  }

  /**
   * Resolves auto-response using pre-loaded rules from the context.
   *
   * @param rules       the GTNetMessageAnswer rules (may be null)
   * @param remoteGTNet the remote GTNet entity
   * @param params      message parameters
   * @return resolved response if a rule matches, empty otherwise
   */
  public Optional<ResolvedResponse> resolveAutoResponse(GTNetMessageAnswer rules, GTNet remoteGTNet,
      Map<String, GTNetMessageParam> params) {
    if (rules == null) {
      return Optional.empty();
    }

    EvalExContext evalContext = buildEvalContext(remoteGTNet, params);

    // Evaluate condition 1
    if (evaluateCondition(rules.getResponseMsgConditional1(), evalContext)) {
      return Optional.of(
          new ResolvedResponse(rules.getResponseMsgCode1(), rules.getResponseMsgMessage1(), rules.getWaitDaysAplly()));
    }

    // Evaluate condition 2
    if (rules.getResponseMsgCode2() != null && evaluateCondition(rules.getResponseMsgConditional2(), evalContext)) {
      return Optional.of(
          new ResolvedResponse(rules.getResponseMsgCode2(), rules.getResponseMsgMessage2(), rules.getWaitDaysAplly()));
    }

    // Evaluate condition 3
    if (rules.getResponseMsgCode3() != null && evaluateCondition(rules.getResponseMsgConditional3(), evalContext)) {
      return Optional.of(
          new ResolvedResponse(rules.getResponseMsgCode3(), rules.getResponseMsgMessage3(), rules.getWaitDaysAplly()));
    }

    return Optional.empty();
  }

  private EvalExContext buildEvalContext(GTNet remoteGTNet, Map<String, GTNetMessageParam> params) {
    EvalExContext ctx = new EvalExContext();
    LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
    ctx.hour = now.getHour();
    ctx.dayOfWeek = now.getDayOfWeek().getValue();

    if (remoteGTNet != null) {
      ctx.requesterTimezone = remoteGTNet.getTimeZone();
      ctx.dailyCount = remoteGTNet.getGtNetConfig().getDailyRequestLimitCount() != null
          ? remoteGTNet.getGtNetConfig().getDailyRequestLimitCount()
          : 0;
      ctx.dailyLimit = remoteGTNet.getDailyRequestLimit() != null ? remoteGTNet.getDailyRequestLimit()
          : Integer.MAX_VALUE;
    }

    // Add message parameters as context variables
    if (params != null) {
      for (Map.Entry<String, GTNetMessageParam> entry : params.entrySet()) {
        ctx.messageParams.put(entry.getKey(), entry.getValue().getParamValue());
      }
    }

    return ctx;
  }

  private boolean evaluateCondition(String condition, EvalExContext ctx) {
    // Null or empty condition means unconditional match
    if (condition == null || condition.isBlank()) {
      return true;
    }

    try {
      Expression expression = new Expression(condition);
      expression.with("hour", ctx.hour);
      expression.with("dayOfWeek", ctx.dayOfWeek);
      expression.with("dailyCount", ctx.dailyCount);
      expression.with("dailyLimit", ctx.dailyLimit);

      if (ctx.requesterTimezone != null) {
        expression.with("requesterTimezone", ctx.requesterTimezone);
      }

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
   * @param waitDaysApply cooling-off period after this response
   */
  public record ResolvedResponse(GTNetMessageCodeType responseCode, String message, String waitDaysApply) {
  }

  /**
   * Internal context for EvalEx expression evaluation.
   */
  private static class EvalExContext {
    int hour;
    int dayOfWeek;
    int dailyCount;
    int dailyLimit;
    String requesterTimezone;
    Map<String, String> messageParams = new java.util.HashMap<>();
  }
}
