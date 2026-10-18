package grafioschtrader.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    A hint of the security lookup: something it could not resolve, why, and what follows for the security form, e.g. a
    first trading day that no provider delivers or an instrument that the providers found only on another exchange.
    The text is resolved by the client from the message key and its arguments.""")
public record SecurityLookupHint(
    @Schema(description = "How much the hint matters for the security being created") Severity severity,
    @Schema(description = """
        Field of the lookup candidate the hint is about, e.g. 'activeFromDate', 'stockexchange' or 'providerId'; the client
        derives the column label from it""") String field,
    @Schema(description = "Translation key of the hint text, with ngx-translate placeholders") String messageKey,
    @Schema(description = """
        Values of the placeholders by name. A name ending in 'Key' holds a translation key, a name ending in 'Date' a
        date 'yyyy-MM-dd'; the client translates or formats such a value and fills the placeholder named without the
        suffix, e.g. 'statusKey' fills '{{status}}' and 'defaultDate' fills '{{default}}'.""") Map<String, String> args) {

  /** How much a hint matters; ordered from the most to the least important. */
  public enum Severity {
    /** The proposed values are probably wrong, e.g. the instrument was not found on the chosen exchange. */
    ERROR,
    /** A value the form needs is missing or a provider failed; the user should check the form. */
    WARNING,
    /** Worth knowing, but no action is required. */
    INFO
  }

  /**
   * Creates a hint.
   *
   * @param severity   how much it matters
   * @param field      field of the candidate it is about, may be null
   * @param messageKey translation key of the text
   * @param args       placeholder values by name; may be empty, an argument without value is left out
   * @return the hint
   */
  public static SecurityLookupHint of(Severity severity, String field, String messageKey, Map<String, String> args) {
    Map<String, String> present = new LinkedHashMap<>();
    args.forEach((name, value) -> {
      if (value != null) {
        present.put(name, value);
      }
    });
    return new SecurityLookupHint(severity, field, messageKey, Collections.unmodifiableMap(present));
  }
}
