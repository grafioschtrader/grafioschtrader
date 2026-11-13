package grafioschtrader.udffill.resolver;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.types.UDFDataType;
import grafioschtrader.dto.UDFFillArgumentOption;
import grafioschtrader.entities.Security;
import grafioschtrader.udffill.http.UDFFillHttp;
import grafioschtrader.udffill.http.UDFFillHttp.FetchResult;
import grafioschtrader.udffill.http.UDFFillHttpException;
import grafioschtrader.udffill.resolver.ResolveOutcome.Found;
import grafioschtrader.udffill.resolver.ResolveOutcome.NotFound;
import grafioschtrader.udffill.resolver.ResolveOutcome.NotFoundReason;
import grafioschtrader.udffill.resolver.ResolveOutcome.Retryable;

/**
 * Reads a figure of an ETF from its German justETF profile, which is addressed by the ISIN of the security. The
 * argument names the figure: {@code TER} is the total expense ratio in percent per year, {@code FUND_SIZE} the fund
 * size in millions of the fund currency, both for decimal fields. {@code ACTIVE} is true for an ETF whose securities
 * are selected actively, false for an index ETF, for a boolean field. justETF marks an active ETF with the investment
 * concept "Aktiv gemanagt" and a label on the profile; its own classification applies, so an ETF on an option strategy
 * index such as a covered call index counts as not active.
 *
 * The figures are read from the elements that carry a {@code data-testid} on the profile page, not from running text.
 * An ISIN justETF does not know is answered with a redirect to the search page; that and a page that does not carry
 * the ISIN count as unknown. Only the ISIN leaves the instance. A profile page is about 500 KB, so the parsed figures
 * of one ISIN are kept for a few minutes: a field for the TER and one for the fund size then cost one request. Requests
 * are separated by at least one second.
 */
@Component
public class JustEtfUDFValueResolver implements IUDFValueResolver {

  public static final String RESOLVER_ID = "JUSTETF";
  public static final String TER = "TER";
  public static final String FUND_SIZE = "FUND_SIZE";
  public static final String ACTIVE = "ACTIVE";
  /** NLS key of the message for an unknown figure. */
  public static final String INVALID_ARGUMENT_KEY = "gt.udf.fill.resolver.justetf.figure";

  private static final List<UDFFillArgumentOption> ARGUMENT_OPTIONS = List.of(
      new UDFFillArgumentOption(TER, Set.of(UDFDataType.UDF_Numeric)),
      new UDFFillArgumentOption(FUND_SIZE, Set.of(UDFDataType.UDF_Numeric)),
      new UDFFillArgumentOption(ACTIVE, Set.of(UDFDataType.UDF_Boolean)));

  static final String PROFILE_URL = "https://www.justetf.com/de/etf-profile.html?isin=";
  static final long PROFILE_CACHE_MILLIS = 10 * 60 * 1000L;
  private static final int PROFILE_CACHE_CLEANUP_SIZE = 200;
  private static final long MIN_REQUEST_DISTANCE_MILLIS = 1000;
  private static final long MAX_RETRY_AFTER_SECONDS = 60;
  private static final String TER_SELECTOR = "[data-testid=tl_etf-basics_value_ter]";
  private static final String FUND_SIZE_SELECTOR = "tr[data-testid=etf-basics_row_fund-size] td:nth-child(2)";
  private static final String CONCEPT_SELECTOR = "[data-testid=tl_etf-basics_value_strategy-risk]";
  private static final String ACTIVE_MARKER_SELECTOR = "[data-testid=etf-quote-section_actively-managed-label],"
      + "[data-testid=etf-profile-controls_strategy-warning-label-text]";
  private static final Pattern TER_TEXT = Pattern.compile("^\\s*(\\d+(?:,\\d+)?)\\s*%");
  private static final Pattern FUND_SIZE_TEXT = Pattern
      .compile("^\\s*[A-Z]{3}\\s+(\\d{1,3}(?:\\.\\d{3})*(?:,\\d+)?|\\d+(?:,\\d+)?)\\s*(Tsd\\.|Mio\\.|Mrd\\.|Bio\\.)?");

  /** Waits between requests; replaced in tests. */
  @FunctionalInterface
  interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }

  /** The figures of one profile page, null where the page shows none. */
  private record Profile(Double terPercent, Double fundSizeMillions, Boolean active) {
  }

  private record CachedProfile(Profile profile, long expiresAtMillis) {
  }

  /** The profile of an ISIN, or the reason there is none. */
  private record ProfileAnswer(Profile profile, ResolveOutcome failure) {
  }

  private final UDFFillHttp udfFillHttp;
  private final Sleeper sleeper;
  private final Map<String, CachedProfile> profiles = new ConcurrentHashMap<>();
  private long lastRequestNanos;

  @Autowired
  public JustEtfUDFValueResolver(UDFFillHttp udfFillHttp) {
    this(udfFillHttp, Thread::sleep);
  }

  JustEtfUDFValueResolver(UDFFillHttp udfFillHttp, Sleeper sleeper) {
    this.udfFillHttp = udfFillHttp;
    this.sleeper = sleeper;
  }

  @Override
  public String getResolverId() {
    return RESOLVER_ID;
  }

  @Override
  public Set<UDFDataType> getSupportedDataTypes() {
    return Set.of(UDFDataType.UDF_Numeric, UDFDataType.UDF_Boolean);
  }

  /**
   * The figures with the data type each fills: {@code ACTIVE} is a yes or no, the others are numbers.
   */
  @Override
  public List<UDFFillArgumentOption> getArgumentOptions() {
    return ARGUMENT_OPTIONS;
  }

  @Override
  public boolean supportsDataType(UDFDataType udfDataType, String argument) {
    return ARGUMENT_OPTIONS.stream()
        .anyMatch(o -> o.argument().equals(argument) && o.udfDataTypes().contains(udfDataType));
  }

  @Override
  public String getDefaultArgument() {
    return TER;
  }

  /**
   * Normalises the figure to upper case; blank means the TER.
   */
  @Override
  public String normalizeArgument(String argument) {
    if (argument == null || argument.isBlank()) {
      return TER;
    }
    String figure = argument.strip().toUpperCase(Locale.ROOT).replace(' ', '_');
    if (!figure.equals(TER) && !figure.equals(FUND_SIZE) && !figure.equals(ACTIVE)) {
      throw new IllegalArgumentException(INVALID_ARGUMENT_KEY);
    }
    return figure;
  }

  @Override
  public ResolveOutcome resolve(Security security, String argument) {
    String isin = security.getIsin();
    if (isin == null || isin.isBlank()) {
      return new NotFound(NotFoundReason.NO_ISIN);
    }
    ProfileAnswer answer = getProfile(isin.strip());
    if (answer.failure() != null) {
      return answer.failure();
    }
    String value = switch (argument) {
    case TER -> format(answer.profile().terPercent());
    case ACTIVE -> answer.profile().active() == null ? null : answer.profile().active().toString();
    default -> format(answer.profile().fundSizeMillions());
    };
    return value == null ? new NotFound(NotFoundReason.NO_VALUE)
        : new Found(value, PROFILE_URL + URLEncoder.encode(isin.strip(), StandardCharsets.UTF_8));
  }

  private ProfileAnswer getProfile(String isin) {
    long now = System.currentTimeMillis();
    CachedProfile cached = profiles.get(isin);
    if (cached != null && cached.expiresAtMillis() > now) {
      return new ProfileAnswer(cached.profile(), null);
    }
    FetchResult result;
    try {
      result = request(PROFILE_URL + URLEncoder.encode(isin, StandardCharsets.UTF_8));
    } catch (UDFFillHttpException e) {
      return new ProfileAnswer(null, new Retryable(e.getMessage()));
    }
    if (result.status() == 429) {
      waitRetryAfter(result.retryAfter());
      return new ProfileAnswer(null, new Retryable("justETF answered 429 Too Many Requests"));
    }
    if (!result.isSuccess()) {
      return new ProfileAnswer(null, new Retryable("justETF answered status " + result.status()));
    }
    Document page = Jsoup.parse(result.body());
    // An unknown ISIN is redirected to the search page, which does not carry the figures
    if (!result.finalUrl().contains("/etf-profile.html") || !page.text().contains(isin)) {
      return new ProfileAnswer(null, new NotFound(NotFoundReason.ZERO_HITS));
    }
    Profile profile = new Profile(parseTer(page), parseFundSize(page), parseActive(page));
    if (profiles.size() > PROFILE_CACHE_CLEANUP_SIZE) {
      profiles.values().removeIf(e -> e.expiresAtMillis() <= now);
    }
    profiles.put(isin, new CachedProfile(profile, now + PROFILE_CACHE_MILLIS));
    return new ProfileAnswer(profile, null);
  }

  /**
   * Parses the TER of the profile page.
   *
   * @param page the profile page
   * @return the TER in percent, or null when the page shows none, for example "n/a"
   */
  static Double parseTer(Document page) {
    Element element = page.selectFirst(TER_SELECTOR);
    Matcher m = element == null ? null : TER_TEXT.matcher(element.text());
    return m != null && m.find() ? parseGermanNumber(m.group(1)) : null;
  }

  /**
   * Parses the fund size of the profile page, for example {@code EUR 2.472 Mio.}.
   *
   * @param page the profile page
   * @return the fund size in millions of the fund currency, or null when the page shows none
   */
  static Double parseFundSize(Document page) {
    Element element = page.selectFirst(FUND_SIZE_SELECTOR);
    Matcher m = element == null ? null : FUND_SIZE_TEXT.matcher(element.text());
    if (m == null || !m.find()) {
      return null;
    }
    double number = parseGermanNumber(m.group(1));
    String unit = m.group(2);
    if (unit == null) {
      return number / 1_000_000;
    }
    return switch (unit) {
    case "Tsd." -> number / 1000;
    case "Mrd." -> number * 1000;
    case "Bio." -> number * 1_000_000;
    default -> number;
    };
  }

  /**
   * Tells whether the profile page presents the ETF as actively managed: the investment concept reads "Aktiv gemanagt"
   * or the page carries the label of an actively managed strategy.
   *
   * @param page the profile page
   * @return true for an active ETF, false when the page names an investment concept without marking it active, null
   *         when the page names no investment concept, so nothing can be said
   */
  static Boolean parseActive(Document page) {
    Element concept = page.selectFirst(CONCEPT_SELECTOR);
    boolean marked = page.selectFirst(ACTIVE_MARKER_SELECTOR) != null
        || (concept != null && concept.text().toLowerCase(Locale.ROOT).startsWith("aktiv"));
    return marked ? Boolean.TRUE : concept == null ? null : Boolean.FALSE;
  }

  /** A German number: {@code .} separates thousands, {@code ,} the decimals. */
  private static double parseGermanNumber(String text) {
    return Double.parseDouble(text.replace(".", "").replace(',', '.'));
  }

  static String format(Double number) {
    return number == null ? null : formatNumber(number);
  }

  private static String formatNumber(double number) {
    return new DecimalFormat("0.###############", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(number);
  }

  /**
   * Sends one request, at least a second after the previous one. Requests are serial for the whole instance.
   */
  private synchronized FetchResult request(String url) throws UDFFillHttpException {
    long waitMillis = MIN_REQUEST_DISTANCE_MILLIS - (System.nanoTime() - lastRequestNanos) / 1_000_000;
    if (lastRequestNanos != 0 && waitMillis > 0) {
      sleepInterruptibly(waitMillis);
    }
    try {
      return udfFillHttp.fetch(url);
    } finally {
      lastRequestNanos = System.nanoTime();
    }
  }

  private void waitRetryAfter(String retryAfter) {
    long seconds = MAX_RETRY_AFTER_SECONDS;
    if (retryAfter != null) {
      try {
        seconds = Math.min(Math.max(Long.parseLong(retryAfter.strip()), 0), MAX_RETRY_AFTER_SECONDS);
      } catch (NumberFormatException e) {
        // An HTTP date is not evaluated; the longest wait applies
      }
    }
    sleepInterruptibly(seconds * 1000);
  }

  /** Sleeps; an interruption is kept on the thread, so that the fill run stops before the next security. */
  private void sleepInterruptibly(long millis) {
    try {
      sleeper.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
