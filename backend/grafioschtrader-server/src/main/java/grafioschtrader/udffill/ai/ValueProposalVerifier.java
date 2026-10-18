package grafioschtrader.udffill.ai;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import grafiosch.types.UDFDataType;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.udffill.UDFFillTemplate;
import grafioschtrader.udffill.http.UDFFillHttp;
import grafioschtrader.udffill.http.UDFFillHttp.FetchResult;
import grafioschtrader.udffill.http.UDFFillHttpException;

/**
 * Verifies a value a language model proposed before the user sees it. The checks establish plausibility, not truth: the
 * value is a valid value of the field in canonical form, the source answers, names the security and contains the
 * excerpt, and the excerpt contains the value — for a URL the target answers and names the security. A proposal that
 * passes becomes PROPOSED with its evidence; one that fails is DISCARDED with the failing check. A transport failure
 * while verifying is retryable, the model answering no value is NOT_FOUND.
 */
@Component
public class ValueProposalVerifier {

  public static final int MAX_EXCERPT_LENGTH = 500;
  private static final int MAX_NOTE_LENGTH = 100;
  private static final Pattern WHITESPACE = Pattern.compile("(?U)\\s+");
  /** A number: digit groups of three separated by a space-like character or an apostrophe, or plain digits. */
  private static final Pattern NUMBER_TOKEN = Pattern
      .compile("[+-]?(?:\\d{1,3}(?:[ \\u00A0\\u2009\\u202F']\\d{3}(?!\\d))+(?:[.,]\\d+)?|\\d+(?:[.,]\\d+)*)");
  private static final List<DateTimeFormatter> DATE_FORMATS = List.of(DateTimeFormatter.ofPattern("yyyy-MM-dd"),
      DateTimeFormatter.ofPattern("dd.MM.yyyy"), DateTimeFormatter.ofPattern("MM/dd/yyyy"));

  /**
   * The structured answer of the model.
   *
   * @param value     the value, or null when the model found none
   * @param sourceUrl the page that names the value
   * @param excerpt   the passage of the page that contains the value
   * @param asOfDate  the date the page gives for the value, ISO format, or null
   */
  public record ValueAnswer(String value, String sourceUrl, String excerpt, String asOfDate) {
  }

  /** The result of the verification. */
  public sealed interface Verification {
  }

  /** Every check passed. */
  public record Proposed(String value, String sourceUrl, String excerpt, LocalDate asOfDate) implements Verification {
  }

  /** A check failed; the note names it. */
  public record Discarded(String note) implements Verification {
  }

  /** The model declined: it found no value. */
  public record NotFound(String note) implements Verification {
  }

  /** A transport failure while verifying; nothing is stored and the cell is asked again at the next run. */
  public record Retry(String reason) implements Verification {
  }

  /** A check failed. */
  private static class CheckFailed extends Exception {
    private static final long serialVersionUID = 1L;

    CheckFailed(String note) {
      super(note);
    }
  }

  /** A transport failure. */
  private static class TransportFailed extends Exception {
    private static final long serialVersionUID = 1L;

    TransportFailed(String reason) {
      super(reason);
    }
  }

  private final UDFFillHttp udfFillHttp;

  public ValueProposalVerifier(UDFFillHttp udfFillHttp) {
    this.udfFillHttp = udfFillHttp;
  }

  /**
   * Verifies the answer of the model for one security.
   *
   * @param field    the field, whose data type and field size validate the value
   * @param security the security
   * @param answer   the answer, null when the model gave none
   * @return the verification outcome
   */
  public Verification verify(UDFMetadataSecurity field, Security security, ValueAnswer answer) {
    if (answer == null || answer.value() == null || answer.value().isBlank()) {
      return new NotFound("The model found no value");
    }
    try {
      boolean url = field.getUdfDataType() == UDFDataType.UDF_URLString;
      String value = url ? answer.value().strip() : canonicalValue(answer.value(), field);
      FetchResult source = fetchText(answer.sourceUrl(), "source");
      String sourceText = plainText(source);
      checkIdentity(sourceText, security, "source");
      String excerpt = checkExcerpt(answer.excerpt(), sourceText);
      if (url) {
        value = checkUrlValue(value, security, field);
      } else if (!excerptContainsNumber(excerpt, Double.parseDouble(value))) {
        throw new CheckFailed("The excerpt does not contain the value");
      }
      return new Proposed(value, source.finalUrl(), excerpt, asOfDate(answer.asOfDate(), sourceText));
    } catch (CheckFailed e) {
      return new Discarded(truncate(e.getMessage()));
    } catch (TransportFailed e) {
      return new Retry(e.getMessage());
    }
  }

  /**
   * Parses a number with {@code .} or {@code ,} as decimal separator and returns it in plain decimal notation with
   * {@code .}, without exponent and trailing zeros; then validates it against the field on its own.
   */
  static String canonicalValue(String raw, UDFMetadataSecurity field) throws CheckFailed {
    Double number = parseNumber(raw.strip());
    if (number == null || !Double.isFinite(number)) {
      throw new CheckFailed("The value is not a finite number");
    }
    if (field.getUdfDataType() == UDFDataType.UDF_NumericInteger && number != Math.rint(number)) {
      throw new CheckFailed("The value is not an integer");
    }
    String canonical = format(number);
    String invalid = UDFFillTemplate.validate(canonical, field);
    if (invalid != null) {
      throw new CheckFailed("Invalid value: " + invalid);
    }
    return canonical;
  }

  static String format(double number) {
    DecimalFormat df = new DecimalFormat("0.###############", DecimalFormatSymbols.getInstance(Locale.ROOT));
    String formatted = df.format(number);
    return "-0".equals(formatted) ? "0" : formatted;
  }

  /**
   * Parses one number. Space-like characters and apostrophes are thousands separators. With both {@code .} and
   * {@code ,} the last one is the decimal separator; a single separator occurring once is the decimal separator, one
   * occurring several times separates thousands.
   *
   * @return the number, or null when the text is not a number
   */
  static Double parseNumber(String text) {
    String t = text.replaceAll("[ \\u00A0\\u2009\\u202F']", "");
    int lastDot = t.lastIndexOf('.');
    int lastComma = t.lastIndexOf(',');
    if (lastDot >= 0 && lastComma >= 0) {
      char decimal = lastDot > lastComma ? '.' : ',';
      char thousands = decimal == '.' ? ',' : '.';
      t = t.replace(String.valueOf(thousands), "").replace(decimal, '.');
    } else {
      char separator = lastDot >= 0 ? '.' : ',';
      long count = t.chars().filter(c -> c == separator).count();
      t = count > 1 ? t.replace(String.valueOf(separator), "") : t.replace(separator, '.');
    }
    if (!t.matches("[+-]?\\d+(\\.\\d+)?")) {
      return null;
    }
    return Double.parseDouble(t);
  }

  /**
   * Tells whether one number token of the excerpt equals the value. Tokens end at a character that is neither digit nor
   * separator, so 0.2 matches neither 10.25 nor 0.21.
   */
  static boolean excerptContainsNumber(String excerpt, double value) {
    Matcher m = NUMBER_TOKEN.matcher(excerpt);
    while (m.find()) {
      Double token = parseNumber(m.group());
      if (token != null && Math.abs(token - value) <= 1e-9 * Math.max(1, Math.abs(value))) {
        return true;
      }
    }
    return false;
  }

  private FetchResult fetchText(String url, String what) throws CheckFailed, TransportFailed {
    if (url == null || url.isBlank()) {
      throw new CheckFailed("No " + what + " given");
    }
    FetchResult result;
    try {
      result = udfFillHttp.fetch(url.strip());
    } catch (UDFFillHttpException e) {
      if (e.isRefused()) {
        throw new CheckFailed("The " + what + " was refused: " + e.getMessage());
      }
      throw new TransportFailed(e.getMessage());
    }
    if (!result.isSuccess()) {
      throw new CheckFailed("The " + what + " answered status " + result.status());
    }
    return result;
  }

  /**
   * Returns the text of a fetched page: an HTML page without markup, any other text as it is.
   */
  public static String plainText(FetchResult result) {
    if (result.body() == null) {
      return "";
    }
    return "text/html".equals(result.contentType()) ? Jsoup.parse(result.body()).text() : result.body();
  }

  /**
   * The page names the security: it contains the ISIN; for a security without ISIN the ticker as a whole token and the
   * name without legal form, which must have at least 3 characters.
   */
  static void checkIdentity(String text, Security security, String what) throws CheckFailed {
    String isin = security.getIsin();
    if (isin != null && !isin.isBlank()) {
      if (!text.contains(isin)) {
        throw new CheckFailed("The " + what + " does not contain the ISIN");
      }
      return;
    }
    String ticker = security.getTickerSymbol();
    String name = security.getName() == null ? "" : UDFFillTemplate.legal(security.getName());
    boolean tickerFound = ticker != null && !ticker.isBlank()
        && Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(ticker) + "(?![\\p{L}\\p{N}])").matcher(text).find();
    if (!tickerFound || name.length() < 3 || !text.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
      throw new CheckFailed("The " + what + " does not name the security");
    }
  }

  private static String checkExcerpt(String excerpt, String text) throws CheckFailed {
    if (excerpt == null || excerpt.isBlank()) {
      throw new CheckFailed("No excerpt given");
    }
    if (excerpt.length() > MAX_EXCERPT_LENGTH) {
      throw new CheckFailed("The excerpt is longer than " + MAX_EXCERPT_LENGTH + " characters");
    }
    String normalized = normalizeWhitespace(excerpt);
    if (!normalizeWhitespace(text).contains(normalized)) {
      throw new CheckFailed("The excerpt does not occur in the source");
    }
    return normalized;
  }

  /**
   * A URL value: the target answers with text and names the security; the value stored is the final URL after
   * redirects, which must still be a valid value of the field.
   */
  private String checkUrlValue(String value, Security security, UDFMetadataSecurity field)
      throws CheckFailed, TransportFailed {
    FetchResult target = fetchText(value, "value URL");
    checkIdentity(plainText(target), security, "value URL");
    String invalid = UDFFillTemplate.validate(target.finalUrl(), field);
    if (invalid != null) {
      throw new CheckFailed("Invalid value: " + invalid);
    }
    return target.finalUrl();
  }

  /**
   * Keeps the as-of date only when it parses as ISO date and occurs in the source in one of the formats yyyy-MM-dd,
   * dd.MM.yyyy or MM/dd/yyyy.
   */
  static LocalDate asOfDate(String asOfDate, String text) {
    if (asOfDate == null || asOfDate.isBlank()) {
      return null;
    }
    try {
      LocalDate date = LocalDate.parse(asOfDate.strip());
      return DATE_FORMATS.stream().anyMatch(f -> text.contains(f.format(date))) ? date : null;
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  static String normalizeWhitespace(String text) {
    return WHITESPACE.matcher(text).replaceAll(" ").strip();
  }

  private static String truncate(String note) {
    return note.length() <= MAX_NOTE_LENGTH ? note : note.substring(0, MAX_NOTE_LENGTH);
  }
}
