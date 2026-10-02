package grafioschtrader.service;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;

import grafioschtrader.types.AlgoSignalKind;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the text of an algo alarm notification in the language of its recipient.
 *
 * <p>
 * The alarm details are stored as locale independent JSON (see {@link AlgoAlarmDetails}), because the language is only
 * known when the notification is delivered. For every signal kind a headline key {@code algo.alarm.<kind>} names what
 * happened, and a MessageFormat detail key turns the JSON fields into one sentence; numbers are formatted by the
 * pattern of that key, and values that are keys themselves (action, reason, bound, direction, indicator) are translated
 * first.
 * </p>
 *
 * <p>
 * A notification is never lost because its text cannot be written: details that do not parse, or lack a field the
 * sentence needs, are shown as they are stored.
 * </p>
 */
public class AlgoAlarmTextRenderer {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  /** Shown in place of an optional threshold that is not configured. */
  private static final String NO_VALUE = "–";

  private final MessageSource messages;

  public AlgoAlarmTextRenderer(MessageSource messages) {
    this.messages = messages;
  }

  /**
   * The complete body of the notification: the common prefix, the headline with the instrument and the detail line.
   *
   * @param kind           the signal kind, null for an alarm whose stored kind is unknown
   * @param instrumentName name of the instrument the alarm concerns
   * @param details        the stored JSON details
   * @param locale         language of the recipient
   * @return the text of the notification
   */
  public String body(AlgoSignalKind kind, String instrumentName, String details, Locale locale) {
    return messages.getMessage("algo.alarm.mail.body.prefix", null, locale) + "\n"
        + describe(kind, instrumentName, details, locale);
  }

  /**
   * Headline and detail line of one alarm, or the headline and the stored details when they cannot be rendered.
   */
  String describe(AlgoSignalKind kind, String instrumentName, String details, Locale locale) {
    if (kind == null) {
      return instrumentName + "\n" + details;
    }
    String headline = translate(headlineKey(kind), locale) + ": " + instrumentName;
    try {
      JsonNode node = MAPPER.readTree(details);
      return headline + "\n" + messages.getMessage(detailKey(kind), arguments(kind, node, locale), locale);
    } catch (JacksonException | IllegalArgumentException | NoSuchMessageException e) {
      return headline + "\n" + details;
    }
  }

  static String headlineKey(AlgoSignalKind kind) {
    return "algo.alarm." + kind.name().toLowerCase(Locale.ROOT).replace('_', '.');
  }

  static String detailKey(AlgoSignalKind kind) {
    return switch (kind) {
    case ENTRY_SIGNAL, PROFIT_TAKE, STOP_LOSS, RISK_BREACH -> "algo.alarm.trade.signal.detail";
    default -> headlineKey(kind) + ".detail";
    };
  }

  private Object[] arguments(AlgoSignalKind kind, JsonNode node, Locale locale) {
    return switch (kind) {
    case PRICE_ALERT -> new Object[] { translate("algo.alarm.bound." + text(node, "bound"), locale),
        number(node, "threshold"), number(node, "price") };
    case ENTRY_SIGNAL, PROFIT_TAKE, STOP_LOSS, RISK_BREACH -> new Object[] { translate(text(node, "reason"), locale),
        number(node, "units"), number(node, "price") };
    case REBALANCE_DRIFT -> new Object[] { translate(text(node, "action"), locale), number(node, "target"),
        number(node, "actual"), number(node, "deviation"), number(node, "amount"), text(node, "currency"),
        translate(text(node, "reason"), locale) };
    case HOLDING_GAIN_LOSS -> new Object[] { number(node, "positionGainLossPercent"), optional(node, "gainThreshold"),
        optional(node, "loseThreshold"), number(node, "price") };
    case PERIOD_PRICE_CHANGE -> new Object[] { number(node, "changePercent"), number(node, "daysInPeriod"),
        text(node, "referenceDate"), number(node, "referenceClose"), number(node, "price") };
    case MA_CROSSING -> new Object[] { translate(text(node, "indicatorType"), locale), number(node, "period"),
        number(node, "maValue"), number(node, "price"), translate(text(node, "crossDirection"), locale) };
    case RSI_THRESHOLD -> new Object[] { number(node, "rsiValue"), number(node, "threshold"),
        translate("algo.alarm.rsi." + text(node, "direction").toLowerCase(Locale.ROOT), locale) };
    case EXPRESSION -> new Object[] { text(node, "expression"), number(node, "price") };
    case ALLOCATION_BREACH -> new Object[] {
        translate("algo.alarm.level." + text(node, "level").toLowerCase(Locale.ROOT), locale), number(node, "target"),
        number(node, "actual"), number(node, "measure"), number(node, "band") };
    };
  }

  /** Translates a key, falling back to the key itself for a value that has no text. */
  private String translate(String key, Locale locale) {
    String text = messages.getMessage(key, null, key, locale);
    return text == null ? key : text;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      throw new IllegalArgumentException("Alarm detail " + field + " is missing");
    }
    return value.asString();
  }

  private static Double number(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isNumber()) {
      throw new IllegalArgumentException("Alarm detail " + field + " is not a number");
    }
    return value.doubleValue();
  }

  private static Object optional(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || !value.isNumber() ? NO_VALUE : value.doubleValue();
  }
}
