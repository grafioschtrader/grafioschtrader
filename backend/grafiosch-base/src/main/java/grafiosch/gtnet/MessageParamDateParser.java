package grafiosch.gtnet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessage.GTNetMessageParam;

/**
 * Reads a date or date-time out of the parameter map of a {@link grafiosch.entities.GTNetMessage}.
 *
 * <p>
 * The parameters travel between peers as plain strings, and the two producers do not agree on a format: the message
 * dialog submits the raw value of a date form control, which serialises as an ISO instant with a trailing {@code Z}
 * ({@code 2026-09-01T00:00:00.000Z}), while a peer driving the protocol directly sends what
 * {@code LocalDate.toString()} produces ({@code 2026-09-01}). A parser accepting only one of the two silently loses the
 * value — {@code closeStartDate} used to be read with {@code DateTimeFormatter.ISO_DATE_TIME}, which throws on a bare
 * date, so a discontinuation was never recognised as expired and its message never became deletable.
 * </p>
 *
 * <p>
 * Both methods are lenient by design: an absent, blank or unparsable parameter yields {@code null} rather than an
 * exception, because a malformed value from a remote peer must not abort the processing of the message.
 * </p>
 */
public abstract class MessageParamDateParser {

  private MessageParamDateParser() {
  }

  /**
   * Reads a parameter as a date-time. A bare date is widened to the start of that day.
   *
   * @param paramMap  the parameter map of the message, may be null
   * @param paramName the name of the parameter, for example {@code fromDateTime}
   * @return the parsed date-time, or null when the parameter is missing, blank or not parsable
   */
  public static LocalDateTime parseDateTime(Map<String, GTNetMessageParam> paramMap, String paramName) {
    String value = rawValue(paramMap, paramName);
    if (value == null) {
      return null;
    }
    if (value.endsWith("Z")) {
      value = value.substring(0, value.length() - 1);
    }
    try {
      return value.indexOf('T') < 0 ? LocalDate.parse(value).atStartOfDay() : LocalDateTime.parse(value);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Reads a parameter as a date. A date-time is narrowed to its date part.
   *
   * @param paramMap  the parameter map of the message, may be null
   * @param paramName the name of the parameter, for example {@code closeStartDate}
   * @return the parsed date, or null when the parameter is missing, blank or not parsable
   */
  public static LocalDate parseDate(Map<String, GTNetMessageParam> paramMap, String paramName) {
    LocalDateTime dateTime = parseDateTime(paramMap, paramName);
    return dateTime == null ? null : dateTime.toLocalDate();
  }

  /**
   * Whether a future-oriented announcement has run out: the maintenance window has ended, or the announced shutdown
   * date has passed. The single answer for both callers that ask it — the delivery task before sending an announcement
   * on, and the handshake handler before queuing one for a newly connected peer. They used to carry two copies that had
   * drifted apart, and the {@code closeStartDate} branch of both rejected a plain {@code yyyy-MM-dd}.
   *
   * <p>
   * Anything that is not one of the two announcement codes, and any announcement whose date cannot be read, counts as
   * not expired: dropping a message because its date is unreadable would hide it from the administrator.
   * </p>
   *
   * @param message the sent announcement to test
   * @return true when the announcement no longer has any effect ahead of it
   */
  public static boolean isAnnouncementExpired(GTNetMessage message) {
    GNetCoreMessageCode codeType = GNetCoreMessageCode.getByValue(message.getMessageCodeValue());
    if (codeType == GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C) {
      LocalDateTime toDateTime = parseDateTime(message.getGtNetMessageParamMap(), "toDateTime");
      return toDateTime != null && toDateTime.isBefore(LocalDateTime.now());
    }
    if (codeType == GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C) {
      LocalDate closeStartDate = parseDate(message.getGtNetMessageParamMap(), "closeStartDate");
      return closeStartDate != null && closeStartDate.isBefore(LocalDate.now());
    }
    return false;
  }

  private static String rawValue(Map<String, GTNetMessageParam> paramMap, String paramName) {
    if (paramMap == null) {
      return null;
    }
    GTNetMessageParam param = paramMap.get(paramName);
    if (param == null || param.getParamValue() == null || param.getParamValue().isBlank()) {
      return null;
    }
    return param.getParamValue().strip();
  }

}
