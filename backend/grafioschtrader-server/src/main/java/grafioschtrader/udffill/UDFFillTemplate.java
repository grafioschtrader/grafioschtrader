package grafioschtrader.udffill;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import grafiosch.BaseConstants;
import grafiosch.entities.UDFData;
import grafiosch.entities.UDFMetadata;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.udffill.UDFFillTemplateException.Reason;

/**
 * Parser and evaluator of the rule of the fill source {@code TEMPLATE}. A rule is one or more lines; empty lines and
 * lines starting with {@code #} are ignored. A line may start with a condition {@code [mic=XNAS,XNYS;currency=USD]}
 * followed by at least one space, the rest is text with placeholders {@code {name|filter|filter(arg,...)}}. The first
 * line that produces a value wins: its condition matches, every placeholder is non-empty after its filters and the
 * result passes the validation of the field. Any other line falls through to the next one.
 *
 * An instance is immutable and thread-safe; it is cached per field and fill rule version. The evaluation reads only the
 * {@link UDFFillContext} and performs no I/O.
 */
public class UDFFillTemplate {

  /** The attributes of a security a placeholder can read. */
  public enum PlaceholderName {
    ISIN("isin"), TICKER("ticker"), NAME("name"), MIC("mic"), CURRENCY("currency"), YAHOO_SYMBOL("yahooSymbol");

    private final String token;

    private PlaceholderName(String token) {
      this.token = token;
    }

    public String getToken() {
      return token;
    }

    static PlaceholderName of(String token) {
      return Arrays.stream(values()).filter(p -> p.token.equals(token)).findFirst().orElse(null);
    }
  }

  private enum FilterType {
    LOWER, UPPER, ASCII, LEGAL, SLUG, URLENCODE, MAP;

    static FilterType of(String token) {
      return Arrays.stream(values()).filter(f -> f.name().toLowerCase(Locale.ROOT).equals(token)).findFirst()
          .orElse(null);
    }
  }

  private enum ConditionKey {
    MIC, CURRENCY;

    static ConditionKey of(String token) {
      return Arrays.stream(values()).filter(c -> c.name().toLowerCase(Locale.ROOT).equals(token)).findFirst()
          .orElse(null);
    }
  }

  /** Why a line did not produce a value. */
  public enum FallThroughReason {
    /** The condition of the line does not match the security. */
    CONDITION,
    /** A placeholder is empty, before or after its filters. */
    EMPTY_PLACEHOLDER,
    /** The computed value fails the validation of the field. */
    INVALID
  }

  /**
   * Why one line fell through.
   *
   * @param lineNumber the number of the line within the rule, counted from 1
   * @param reason     the reason
   * @param detail     the empty placeholder, or the validation message; null for a condition miss
   */
  public record LineOutcome(int lineNumber, FallThroughReason reason, String detail) {
  }

  /**
   * The result of evaluating a rule for one security.
   *
   * @param value       the computed value, or null when no line produced one
   * @param lineNumber  the line that produced the value, or null
   * @param fallThrough per line tried before, why it did not produce a value
   */
  public record TemplateResult(String value, Integer lineNumber, List<LineOutcome> fallThrough) {

    public boolean hasValue() {
      return value != null;
    }

    /**
     * Tells whether the rule failed only because of validation: no value was produced but at least one matching line
     * computed a candidate that the field refused.
     *
     * @return true when at least one line fell through as {@link FallThroughReason#INVALID}
     */
    public boolean isInvalid() {
      return value == null && fallThrough.stream().anyMatch(lo -> lo.reason() == FallThroughReason.INVALID);
    }

    /**
     * Returns the validation message of the first invalid line.
     *
     * @return the message, or null when no line was invalid
     */
    public String getInvalidMessage() {
      return fallThrough.stream().filter(lo -> lo.reason() == FallThroughReason.INVALID).map(LineOutcome::detail)
          .findFirst().orElse(null);
    }
  }

  private record Filter(FilterType type, String argument, Map<String, String> map) {
  }

  private sealed interface Segment permits Literal, Placeholder {
  }

  private record Literal(String text) implements Segment {
  }

  private record Placeholder(PlaceholderName name, List<Filter> filters) implements Segment {
  }

  private record Line(int lineNumber, Map<ConditionKey, Set<String>> condition, List<Segment> segments) {
  }

  private static final char[] ARGUMENT_FORBIDDEN = { ',', '=', '(', ')', '|', '{', '}' };

  private static final Pattern PARENTHESISED = Pattern.compile("\\([^()]*\\)");
  private static final Pattern LEGAL_FORM_WORDS = Pattern.compile(
      "(?<![\\p{L}\\p{N}])(?:Corporation|Holdings|Holding|Company|Limited|Group|GmbH|KGaA|Corp|Inc|Ltd|plc|SpA|AG|SE|SA|NV|Co|KG)(?![\\p{L}\\p{N}])\\.?",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
  private static final Pattern NON_ASCII = Pattern.compile("[^\\x00-\\x7F]");
  private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
  private static final Map<String, String> ASCII_REPLACEMENTS = createAsciiReplacements();

  private final List<Line> lines;

  private UDFFillTemplate(List<Line> lines) {
    this.lines = lines;
  }

  private static Map<String, String> createAsciiReplacements() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("ß", "ss");
    m.put("æ", "ae");
    m.put("Æ", "AE");
    m.put("œ", "oe");
    m.put("Œ", "OE");
    m.put("ø", "o");
    m.put("Ø", "O");
    m.put("ł", "l");
    m.put("Ł", "L");
    m.put("đ", "d");
    m.put("Đ", "D");
    m.put("þ", "th");
    return m;
  }

  /**
   * Parses a rule. Lines are separated by {@code \n} or {@code \r\n}; leading and trailing whitespace of a line is
   * removed.
   *
   * @param rule the rule as stored in {@code fill_rule}
   * @return the parsed template
   * @throws UDFFillTemplateException when the rule does not parse, naming line and position of the fault
   */
  public static UDFFillTemplate parse(String rule) {
    List<Line> lines = new ArrayList<>();
    String[] rawLines = rule == null ? new String[0] : rule.split("\\r?\\n", -1);
    for (int i = 0; i < rawLines.length; i++) {
      String raw = rawLines[i];
      String text = raw.strip();
      if (!text.isEmpty() && !text.startsWith("#")) {
        int column = raw.length() - raw.stripLeading().length() + 1;
        lines.add(parseLine(text, i + 1, column));
      }
    }
    if (lines.isEmpty()) {
      throw new UDFFillTemplateException(Reason.NO_LINE, 1, 1, null);
    }
    return new UDFFillTemplate(lines);
  }

  private static Line parseLine(String text, int lineNumber, int column) {
    Map<ConditionKey, Set<String>> condition = null;
    int pos = 0;
    if (text.startsWith("[")) {
      int close = text.indexOf(']');
      if (close < 0 || text.substring(1, close).indexOf('[') >= 0) {
        throw new UDFFillTemplateException(Reason.UNBALANCED_BRACKET, lineNumber, column, "[");
      }
      condition = parseCondition(text.substring(1, close), lineNumber, column + 1);
      if (close + 1 >= text.length()) {
        throw new UDFFillTemplateException(Reason.EMPTY_TEMPLATE, lineNumber, column + close + 1, null);
      }
      if (text.charAt(close + 1) != ' ') {
        throw new UDFFillTemplateException(Reason.CONDITION_SPACE_MISSING, lineNumber, column + close + 1, "]");
      }
      pos = close + 1;
      while (pos < text.length() && text.charAt(pos) == ' ') {
        pos++;
      }
    }
    if (pos >= text.length()) {
      throw new UDFFillTemplateException(Reason.EMPTY_TEMPLATE, lineNumber, column + pos, null);
    }
    return new Line(lineNumber, condition, parseSegments(text.substring(pos), lineNumber, column + pos));
  }

  private static Map<ConditionKey, Set<String>> parseCondition(String text, int lineNumber, int column) {
    Map<ConditionKey, Set<String>> condition = new EnumMap<>(ConditionKey.class);
    for (String part : text.split(";", -1)) {
      int eq = part.indexOf('=');
      if (eq < 0) {
        throw new UDFFillTemplateException(Reason.INVALID_CONDITION, lineNumber, column, part.strip());
      }
      String keyToken = part.substring(0, eq).strip();
      ConditionKey key = ConditionKey.of(keyToken);
      if (key == null) {
        throw new UDFFillTemplateException(Reason.UNKNOWN_CONDITION_KEY, lineNumber, column, keyToken);
      }
      if (condition.containsKey(key)) {
        throw new UDFFillTemplateException(Reason.DUPLICATE_CONDITION_KEY, lineNumber, column, keyToken);
      }
      Set<String> values = new LinkedHashSet<>();
      for (String value : part.substring(eq + 1).split(",", -1)) {
        String v = value.strip();
        if (v.isEmpty() || v.indexOf('=') >= 0) {
          throw new UDFFillTemplateException(Reason.INVALID_CONDITION, lineNumber, column, part.strip());
        }
        values.add(v);
      }
      condition.put(key, values);
    }
    return condition;
  }

  private static List<Segment> parseSegments(String text, int lineNumber, int column) {
    List<Segment> segments = new ArrayList<>();
    StringBuilder literal = new StringBuilder();
    int i = 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      boolean doubled = i + 1 < text.length() && text.charAt(i + 1) == c;
      if ((c == '{' || c == '}') && doubled) {
        literal.append(c);
        i += 2;
      } else if (c == '{') {
        int close = text.indexOf('}', i + 1);
        if (close < 0 || text.substring(i + 1, close).indexOf('{') >= 0) {
          throw new UDFFillTemplateException(Reason.UNBALANCED_BRACE, lineNumber, column + i, "{");
        }
        if (!literal.isEmpty()) {
          segments.add(new Literal(literal.toString()));
          literal.setLength(0);
        }
        segments.add(parsePlaceholder(text.substring(i + 1, close), lineNumber, column + i));
        i = close + 1;
      } else if (c == '}') {
        throw new UDFFillTemplateException(Reason.UNBALANCED_BRACE, lineNumber, column + i, "}");
      } else {
        literal.append(c);
        i++;
      }
    }
    if (!literal.isEmpty()) {
      segments.add(new Literal(literal.toString()));
    }
    return segments;
  }

  private static Placeholder parsePlaceholder(String inner, int lineNumber, int column) {
    String[] parts = inner.split("\\|", -1);
    String nameToken = parts[0].strip();
    PlaceholderName name = PlaceholderName.of(nameToken);
    if (name == null) {
      throw new UDFFillTemplateException(Reason.UNKNOWN_PLACEHOLDER, lineNumber, column, nameToken);
    }
    List<Filter> filters = new ArrayList<>();
    for (int i = 1; i < parts.length; i++) {
      filters.add(parseFilter(parts[i].strip(), lineNumber, column));
    }
    return new Placeholder(name, filters);
  }

  private static Filter parseFilter(String token, int lineNumber, int column) {
    int open = token.indexOf('(');
    String filterName = open < 0 ? token : token.substring(0, open).strip();
    FilterType type = FilterType.of(filterName);
    if (type == null) {
      throw new UDFFillTemplateException(Reason.UNKNOWN_FILTER, lineNumber, column, filterName);
    }
    List<String> args = null;
    if (open >= 0) {
      if (!token.endsWith(")")) {
        throw new UDFFillTemplateException(Reason.INVALID_FILTER_ARGUMENT, lineNumber, column, token);
      }
      String argText = token.substring(open + 1, token.length() - 1);
      if (argText.indexOf('(') >= 0 || argText.indexOf(')') >= 0) {
        throw new UDFFillTemplateException(Reason.INVALID_FILTER_ARGUMENT, lineNumber, column, token);
      }
      args = Arrays.asList(argText.split(",", -1));
    }
    return createFilter(type, args, token, lineNumber, column);
  }

  private static Filter createFilter(FilterType type, List<String> args, String token, int lineNumber, int column) {
    switch (type) {
    case MAP:
      if (args == null) {
        throw new UDFFillTemplateException(Reason.INVALID_FILTER_ARGUMENT, lineNumber, column, token);
      }
      return new Filter(type, null, parseMapArguments(args, lineNumber, column));
    case SLUG:
      if (args == null) {
        return new Filter(type, "_", null);
      }
      if (args.size() != 1 || args.getFirst().isEmpty() || containsForbidden(args.getFirst())) {
        throw new UDFFillTemplateException(Reason.INVALID_FILTER_ARGUMENT, lineNumber, column, token);
      }
      return new Filter(type, args.getFirst(), null);
    default:
      if (args != null) {
        throw new UDFFillTemplateException(Reason.INVALID_FILTER_ARGUMENT, lineNumber, column, token);
      }
      return new Filter(type, null, null);
    }
  }

  private static Map<String, String> parseMapArguments(List<String> args, int lineNumber, int column) {
    Map<String, String> map = new HashMap<>();
    for (String arg : args) {
      int eq = arg.indexOf('=');
      if (eq <= 0 || arg.indexOf('=', eq + 1) >= 0) {
        throw new UDFFillTemplateException(Reason.INVALID_FILTER_ARGUMENT, lineNumber, column, arg);
      }
      String key = arg.substring(0, eq);
      String value = arg.substring(eq + 1);
      if (containsForbidden(key) || containsForbidden(value)) {
        throw new UDFFillTemplateException(Reason.INVALID_FILTER_ARGUMENT, lineNumber, column, arg);
      }
      if (map.put(key, value) != null) {
        throw new UDFFillTemplateException(Reason.DUPLICATE_MAP_KEY, lineNumber, column, key);
      }
    }
    return map;
  }

  private static boolean containsForbidden(String text) {
    for (char c : ARGUMENT_FORBIDDEN) {
      if (text.indexOf(c) >= 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Evaluates the rule for one security. Lines are tried in order; the first line that produces a value wins.
   *
   * @param context the master data of the security
   * @param field   the field whose value is computed; its data type and field size validate each candidate
   * @return the value or none, the line that produced it, and per line tried before the reason it fell through
   */
  public TemplateResult evaluate(UDFFillContext context, UDFMetadataSecurity field) {
    List<LineOutcome> fallThrough = new ArrayList<>();
    for (Line line : lines) {
      if (line.condition() != null && !conditionMatches(line.condition(), context)) {
        fallThrough.add(new LineOutcome(line.lineNumber(), FallThroughReason.CONDITION, null));
        continue;
      }
      StringBuilder sb = new StringBuilder();
      String emptyPlaceholder = render(line, context, sb);
      if (emptyPlaceholder != null) {
        fallThrough.add(new LineOutcome(line.lineNumber(), FallThroughReason.EMPTY_PLACEHOLDER, emptyPlaceholder));
        continue;
      }
      String value = sb.toString();
      String invalidMessage = validate(value, field);
      if (invalidMessage != null) {
        fallThrough.add(new LineOutcome(line.lineNumber(), FallThroughReason.INVALID, invalidMessage));
        continue;
      }
      return new TemplateResult(value, line.lineNumber(), fallThrough);
    }
    return new TemplateResult(null, null, fallThrough);
  }

  private static boolean conditionMatches(Map<ConditionKey, Set<String>> condition, UDFFillContext context) {
    for (Map.Entry<ConditionKey, Set<String>> entry : condition.entrySet()) {
      String actual = switch (entry.getKey()) {
      case MIC -> context.mic();
      case CURRENCY -> context.security().getCurrency();
      };
      if (actual == null || !entry.getValue().contains(actual)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Renders the segments of a line into {@code sb}.
   *
   * @return null when every placeholder was non-empty, otherwise the token of the first empty placeholder
   */
  private static String render(Line line, UDFFillContext context, StringBuilder sb) {
    for (Segment segment : line.segments()) {
      switch (segment) {
      case Literal literal -> sb.append(literal.text());
      case Placeholder placeholder -> {
        String value = readPlaceholder(placeholder.name(), context);
        for (int i = 0; i < placeholder.filters().size() && !isEmpty(value); i++) {
          value = applyFilter(placeholder.filters().get(i), value);
        }
        if (isEmpty(value)) {
          return placeholder.name().getToken();
        }
        sb.append(value);
      }
      }
    }
    return null;
  }

  private static String readPlaceholder(PlaceholderName name, UDFFillContext context) {
    return switch (name) {
    case ISIN -> context.security().getIsin();
    case TICKER -> context.security().getTickerSymbol();
    case NAME -> context.security().getName();
    case MIC -> context.mic();
    case CURRENCY -> context.security().getCurrency();
    case YAHOO_SYMBOL -> context.yahooSymbol();
    };
  }

  private static boolean isEmpty(String value) {
    return value == null || value.isEmpty();
  }

  private static String applyFilter(Filter filter, String value) {
    return switch (filter.type()) {
    case LOWER -> value.toLowerCase(Locale.ROOT);
    case UPPER -> value.toUpperCase(Locale.ROOT);
    case ASCII -> ascii(value);
    case LEGAL -> legal(value);
    case SLUG -> slug(value, filter.argument());
    case URLENCODE -> URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    case MAP -> filter.map().get(value);
    };
  }

  /**
   * Transliterates to ASCII: Unicode NFKD with combining marks removed, then a few letters without decomposition are
   * replaced, and every character still outside ASCII is removed.
   */
  static String ascii(String value) {
    String result = COMBINING_MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFKD)).replaceAll("");
    for (Map.Entry<String, String> replacement : ASCII_REPLACEMENTS.entrySet()) {
      result = result.replace(replacement.getKey(), replacement.getValue());
    }
    return NON_ASCII.matcher(result).replaceAll("");
  }

  /**
   * Removes parenthesised segments and legal-form words from a company name. A heuristic for building slugs, not an
   * identification of the issuer.
   */
  public static String legal(String value) {
    String result = value;
    String before;
    do {
      before = result;
      result = PARENTHESISED.matcher(result).replaceAll(" ");
    } while (!result.equals(before));
    result = LEGAL_FORM_WORDS.matcher(result).replaceAll(" ");
    result = result.strip();
    while (!result.isEmpty() && (result.endsWith("&") || result.endsWith(","))) {
      result = result.substring(0, result.length() - 1).strip();
    }
    return WHITESPACE.matcher(result).replaceAll(" ").strip();
  }

  static String slug(String value, String separator) {
    String result = NON_SLUG.matcher(ascii(value).toLowerCase(Locale.ROOT)).replaceAll(separator);
    while (result.startsWith(separator)) {
      result = result.substring(separator.length());
    }
    while (result.endsWith(separator)) {
      result = result.substring(0, result.length() - separator.length());
    }
    return result;
  }

  /**
   * Validates a candidate value against the field on an isolated one-key map, never on the stored values: with a
   * one-field metadata list {@code checkDataAgainstMetadata} would remove every other key.
   *
   * @return null when the value is valid, otherwise the validation message
   */
  public static String validate(String value, UDFMetadataSecurity field) {
    String key = BaseConstants.UDF_FIELD_PREFIX + field.getIdUDFMetadata();
    Map<String, Object> values = new HashMap<>();
    values.put(key, value);
    try {
      UDFData udfData = new UDFData(null, values);
      udfData.checkDataAgainstMetadata(List.<UDFMetadata>of(field));
      return udfData.getJsonValues().containsKey(key) ? null : "Not a field of the metadata";
    } catch (Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
  }
}
