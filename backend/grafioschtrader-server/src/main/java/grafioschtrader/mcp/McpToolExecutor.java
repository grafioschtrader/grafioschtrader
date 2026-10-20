package grafioschtrader.mcp;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import grafiosch.common.ClientClock;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafiosch.security.AccessTokenPrincipal;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the body of every tool: it builds the {@link McpCaller}, passes the limits, turns the outcome into a tool result
 * and writes one line to the operation log.
 *
 * <p>
 * The limits are passed in a fixed order. The request bucket and the check for a running heavy report come first and
 * consume no budget; the daily budget is reserved afterwards and stays consumed even when the report then ends with an
 * error, so that faulty calls cannot be repeated endlessly.
 * </p>
 *
 * <p>
 * The operation log names the tool, the user, the token, the outcome, the duration and the number of rows, with a
 * correlation id. Arguments, results, token values and portfolio contents never appear in it.
 * </p>
 *
 * <p>
 * The tools run on the request thread (the server uses {@code SYNC} with immediate execution on a servlet stack), so
 * the security context and the request attributes of the access token filter are visible here.
 * </p>
 */
@Component
@ConditionalOnProperty(prefix = "gt.use", name = "mcp", havingValue = "true")
public class McpToolExecutor {

  private static final Logger log = LoggerFactory.getLogger(McpToolExecutor.class);

  private static final Pattern NESTED_TRANSLATION = Pattern.compile(":(.*?):T");

  /** Serializes the tool results: dates as ISO strings, null values kept, because null and 0 are different answers. */
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
      .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();

  private final McpToolCallLimitService limitService;
  private final MessageSource messageSource;

  public McpToolExecutor(McpToolCallLimitService limitService, MessageSource messageSource) {
    this.limitService = limitService;
    this.messageSource = messageSource;
  }

  /** The body of a tool; the reports declare checked exceptions, which end the call as INTERNAL. */
  @FunctionalInterface
  public interface ToolBody {
    Object apply(McpCaller caller) throws Exception;
  }

  /** A tool result that knows how many rows it carries, for the operation log. */
  public interface RowCount {
    int rowCount();
  }

  /**
   * Executes a tool body with all checks.
   *
   * @param toolName the name of the tool, for the operation log
   * @param heavy    true for the reports of which only one may run per user at a time
   * @param body     the tool body, receiving the caller and returning the result object
   * @return the tool result, an error result when the call was refused or failed
   */
  public CallToolResult execute(String toolName, boolean heavy, ToolBody body) {
    long start = System.nanoTime();
    String correlationId = UUID.randomUUID().toString().substring(0, 8);
    User user = currentUser();
    AccessTokenPrincipal principal = currentPrincipal();
    String outcome = "OK";
    int rows = 0;
    boolean enteredHeavy = false;
    try {
      if (user == null || principal == null) {
        throw new IllegalStateException("Tool called without access token authentication");
      }
      McpCaller caller = createCaller(user, principal);
      limitService.consumeRequest(user.getIdUser());
      if (heavy) {
        enteredHeavy = limitService.tryEnterHeavy(user.getIdUser());
        if (!enteredHeavy) {
          throw new McpToolException(McpErrorCode.BUSY, "gt.mcp.busy");
        }
      }
      limitService.reserveDailyCall(user);
      Object result = body.apply(caller);
      rows = result instanceof RowCount rowCount ? rowCount.rowCount() : 1;
      return CallToolResult.builder().addTextContent(JSON_MAPPER.writeValueAsString(result)).isError(false).build();
    } catch (McpToolException e) {
      outcome = e.getCode().name();
      return errorResult(e.getCode(), translate(user, e.getMessageKey(), e.getMessageArgs()), e.getExtras());
    } catch (DataViolationException e) {
      outcome = McpErrorCode.DOMAIN_ERROR.name();
      return errorResult(McpErrorCode.DOMAIN_ERROR, translate(user, e), Map.of());
    } catch (GeneralNotTranslatedWithArgumentsException e) {
      outcome = McpErrorCode.DOMAIN_ERROR.name();
      return errorResult(McpErrorCode.DOMAIN_ERROR, translate(user, e.getMessageKey(), e.getArguments()), Map.of());
    } catch (SecurityException e) {
      // Ids are checked before a report runs, so this is a report refusing a foreign object it reached indirectly.
      outcome = McpErrorCode.NOT_OWNED.name();
      return errorResult(McpErrorCode.NOT_OWNED, translate(user, "gt.mcp.not.owned", new Object[] { "-" }), Map.of());
    } catch (Exception e) {
      outcome = McpErrorCode.INTERNAL.name();
      log.error("MCP tool {} failed, correlation id {}", toolName, correlationId, e);
      return errorResult(McpErrorCode.INTERNAL, translate(user, "gt.mcp.internal", new Object[] { correlationId }),
          Map.of("correlationId", correlationId));
    } finally {
      if (enteredHeavy) {
        limitService.leaveHeavy(user.getIdUser());
      }
      log.info("MCP tool={} idUser={} idUserAccessToken={} outcome={} durationMs={} rows={} correlationId={}",
          toolName, user == null ? null : user.getIdUser(), principal == null ? null : principal.idUserAccessToken(),
          outcome, (System.nanoTime() - start) / 1_000_000, rows, correlationId);
    }
  }

  private McpCaller createCaller(User user, AccessTokenPrincipal principal) {
    return new McpCaller(user.getIdUser(), user.getIdTenant(), principal.idUserAccessToken(), principal.scope(),
        user.createAndGetJavaLocale(), user.getLanguage(), user.getLocaleStr(), ClientClock.today(),
        ClientClock.zone());
  }

  private CallToolResult errorResult(McpErrorCode code, String message, Map<String, Object> extras) {
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("code", code.name());
    content.put("message", message);
    content.putAll(extras);
    return CallToolResult.builder().addTextContent(JSON_MAPPER.writeValueAsString(content)).isError(true).build();
  }

  private String translate(User user, String messageKey, Object[] args) {
    String message = messageSource.getMessage(messageKey, args, messageKey, locale(user));
    Matcher matcher = NESTED_TRANSLATION.matcher(message);
    StringBuilder sb = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(sb,
          Matcher.quoteReplacement(messageSource.getMessage(matcher.group(1), null, matcher.group(1), locale(user))));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private String translate(User user, DataViolationException e) {
    return e.getDataViolation().stream()
        .map(dv -> (dv.isTranslateFieldName()
            ? messageSource.getMessage(dv.getField(), null, dv.getField(), locale(user))
            : dv.getField()) + ": " + messageSource.getMessage(dv.getMessageKey(), dv.getData(), dv.getMessageKey(),
                locale(user)))
        .collect(Collectors.joining("; "));
  }

  private static Locale locale(User user) {
    return user == null ? Locale.ENGLISH : user.createAndGetJavaLocale();
  }

  private static User currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.getDetails() instanceof User user ? user : null;
  }

  private static AccessTokenPrincipal currentPrincipal() {
    return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
        && attributes.getRequest().getAttribute(AccessTokenPrincipal.REQUEST_ATTRIBUTE) instanceof AccessTokenPrincipal p
            ? p
            : null;
  }
}
