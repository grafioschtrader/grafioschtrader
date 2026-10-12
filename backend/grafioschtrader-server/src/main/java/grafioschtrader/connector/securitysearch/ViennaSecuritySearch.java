package grafioschtrader.connector.securitysearch;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SecuritySearchStatus;
import io.github.bucket4j.Bandwidth;
import tools.jackson.databind.JsonNode;

/**
 * Finds the listing of an ISIN on the Vienna Stock Exchange. Wiener Börse answers automated calls of its search page
 * with HTTP 403, but its quote page accepts an ISIN and redirects to the page of the instrument - share (prime or
 * global market), bond or fund. That page carries the internal number of the listing as {@code data-id-notation}, which
 * is the URL extension of the Vienna price connector, the kind of instrument in its {@code <title>} ("… AKTIE …", "…
 * Anleihe …", "… Fondsdaten …") and, for a share, its ticker as "Kürzel". An ISIN not listed in Vienna redirects to the
 * home page, which has no notation. ISIN and MIC count as delivered, because the page of the exchange names the ISIN.
 *
 * <p>
 * Master data comes from the chart endpoint the price connector also uses, with {@code INSTRUMENT_DATA_NEEDED=1}: name,
 * currency, exchange code ({@code VIE}, or {@code @AT} for the OeKB data of a fund) and the range of available prices,
 * whose first day is the first trading day of the listing (Andritz: 2001-06-25). The stock exchange hyperlink is the
 * quote page with ISIN and notation, as users maintain it. Only the notation attribute, the title and the ticker label
 * of the page are read; if Wiener Börse rebuilds the page, the search finds nothing and the probe of the connector
 * shows it.
 * </p>
 *
 * <p>
 * The terms of a bond - maturity, start of term, first listing, denomination and coupon frequency - are not part of the
 * delivered page. Its script fills each "master data" placeholder ({@code id="placeholder-c…"}) by posting back to the
 * page, one module per request and only with {@code Referer} and {@code X-Requested-With}; the answer is JSON whose
 * {@code content} is a table of label and value. The search does the same, and a bond whose terms cannot be read keeps
 * the master data of the chart endpoint.
 * </p>
 */
@Component
public class ViennaSecuritySearch extends BaseSecuritySearchProvider {

  public static final String PROVIDER_ID = "vienna";

  private static final Logger log = LoggerFactory.getLogger(ViennaSecuritySearch.class);

  static final String TYPE_SHARE = "AKTIE";
  static final String TYPE_BOND = "Anleihe";
  static final String TYPE_FUND = "Fondsdaten";

  private static final String DEFAULT_BASE_URL = "https://www.wienerborse.at/";
  private static final String QUOTE_PAGE_DE = "marktdaten/aktien-sonstige/preisdaten/";
  private static final String QUOTE_PAGE_EN = "en/market-data/shares-others/quote/";
  private static final String VIENNA_EXCHANGE_CODE = "VIE";
  /** Source code of the net asset values OeKB publishes for Austrian funds, which Wiener Börse shows as fund data. */
  private static final String OEKB_FUND_DATA_CODE = "@AT";
  private static final Set<String> TITLE_LABELS = Set.of(TYPE_BOND, TYPE_FUND, "Kurs", "Wiener B\u00f6rse");
  private static final Pattern ISIN_SEGMENT = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

  private static final Pattern NOTATION = Pattern.compile("data-id-notation=\"([0-9]+)\"");
  private static final Pattern TITLE = Pattern.compile("<title>([^<]*)</title>");
  private static final Pattern TICKER = Pattern
      .compile("header-label\">Kürzel:\\s*</span>\\s*<span[^>]*>\\s*([^<\\s]+)\\s*</span>");
  private static final Pattern PLACEHOLDER = Pattern.compile("id=\"placeholder-(c[0-9]+)\"");
  private static final Pattern TERM_ROW = Pattern.compile("<th[^>]*>(.*?)</th>\\s*<td[^>]*>(.*?)</td>",
      Pattern.DOTALL);
  private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
  /** Longest name a security may have ({@code Security.name}). */
  private static final int MAX_NAME_LENGTH = 80;
  private static final DateTimeFormatter TERM_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  static final String TERM_LONG_NAME = "Langname";
  static final String TERM_MATURITY = "Fälligkeit";
  static final String TERM_START = "Laufzeitbeginn";
  static final String TERM_FIRST_LISTING = "Erstnotiz";
  static final String TERM_DENOMINATION = "Stückelung";
  static final String TERM_COUPON_MONTHS = "Kuponfrequenz in Monaten";

  private final String baseUrl;

  @Autowired
  public ViennaSecuritySearch() {
    this(DEFAULT_BASE_URL, DEFAULT_REQUEST_TIMEOUT);
  }

  /**
   * @param baseUrl        base URL of Wiener Börse, ending with a slash; tests point it to a local server
   * @param requestTimeout timeout of a single request
   */
  ViennaSecuritySearch(String baseUrl, Duration requestTimeout) {
    // A bond needs the page, the chart and one request per master data module, as a browser view of it does
    super(Bandwidth.builder().capacity(8).refillGreedy(8, Duration.ofSeconds(10)).build(), requestTimeout,
        DEFAULT_MAX_TOKEN_WAIT);
    this.baseUrl = baseUrl;
  }

  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  /** Exchanges and instrument types are those of {@code ViennaStockExchangeFeedConnector}. */
  @Override
  public Optional<String> getFeedConnectorShortId() {
    return Optional.of(PROVIDER_ID);
  }

  @Override
  public boolean supports(SecuritySearchQuery query) {
    return query.hasValidIsin() && (query.mic() == null || GlobalConstants.STOCK_EX_MIC_AUSTRIA.equals(query.mic()));
  }

  @Override
  protected List<SecuritySearchCandidateDto> fetchCandidates(SecuritySearchQuery query) throws Exception {
    String url = baseUrl + QUOTE_PAGE_DE + "?ISIN=" + URLEncoder.encode(query.isin(), StandardCharsets.UTF_8);
    SecuritySearchCandidateDto candidate = parsePage(sendExpectOk(HttpRequest.newBuilder(URI.create(url)).GET()),
        query.isin());
    return candidate == null ? List.of() : List.of(candidate);
  }

  /**
   * Converts the quote page of an instrument.
   *
   * @param html the page
   * @param isin the queried ISIN
   * @return the listing, or null when the page has no notation or does not name the ISIN
   */
  static SecuritySearchCandidateDto parsePage(String html, String isin) {
    Matcher notation = NOTATION.matcher(html);
    if (!notation.find() || !html.contains(isin)) {
      return null;
    }
    String title = group(TITLE, html);
    // A bond page shows its ISIN as "Kürzel", which is no ticker
    String ticker = group(TICKER, html);
    if (isin.equalsIgnoreCase(ticker)) {
      ticker = null;
    }
    return new SecuritySearchCandidateDto(PROVIDER_ID, ticker, notation.group(1), VIENNA_EXCHANGE_CODE, isin,
        IdentityEvidence.RETURNED, null, IdentityEvidence.UNKNOWN, GlobalConstants.STOCK_EX_MIC_AUSTRIA,
        IdentityEvidence.RETURNED, nameFromTitle(title), typeFromTitle(title));
  }

  /**
   * Name of the instrument from the page title, whose segments are separated by "•": a share has the form "Andritz AG
   * AKTIE • Wiener Börse", a bond "AT… • Anleihe • Land NOE … • Kurs • Wiener Börse", a fund "AT… • 3 Banken
   * Anleihefonds-Selektion (T) • Fondsdaten • Wiener Börse". The name is the first segment that is neither the ISIN,
   * nor the kind, nor one of the fixed labels.
   *
   * @param title the page title, may be null
   * @return the name, or null
   */
  static String nameFromTitle(String title) {
    if (title == null) {
      return null;
    }
    for (String segment : segments(title)) {
      if (segment.endsWith(" " + TYPE_SHARE)) {
        return segment.substring(0, segment.length() - TYPE_SHARE.length() - 1).trim();
      }
      if (!ISIN_SEGMENT.matcher(segment).matches() && !TITLE_LABELS.contains(segment)) {
        return segment;
      }
    }
    return null;
  }

  /**
   * Kind of the instrument from the page title: a segment ending with "AKTIE", or the segment "Anleihe" or
   * "Fondsdaten". Whole segments are compared, since a fund name may contain "Anleihe".
   *
   * @param title the page title, may be null
   * @return {@link #TYPE_SHARE}, {@link #TYPE_BOND}, {@link #TYPE_FUND} or null
   */
  static String typeFromTitle(String title) {
    if (title == null) {
      return null;
    }
    for (String segment : segments(title)) {
      if (segment.endsWith(" " + TYPE_SHARE)) {
        return TYPE_SHARE;
      }
      if (TYPE_BOND.equals(segment) || TYPE_FUND.equals(segment)) {
        return segment;
      }
    }
    return null;
  }

  private static List<String> segments(String title) {
    return Arrays.stream(title.split("\u2022")).map(String::trim).filter(p -> !p.isEmpty()).toList();
  }

  @Override
  public boolean supportsBaseData(SecuritySearchCandidateDto candidate) {
    return PROVIDER_ID.equals(candidate.providerId()) && candidate.providerInstrumentId() != null;
  }

  @Override
  protected SecurityBaseDataDto fetchBaseData(SecuritySearchCandidateDto candidate, Locale locale) throws Exception {
    LocalDate today = LocalDate.now();
    String url = baseUrl + "interactive-chart/canvas/data/?DATETIME_TZ_START_RANGE=" + today.minusDays(7)
        + "T00:00:00Z&DATETIME_TZ_END_RANGE=" + today + "T00:00:00Z&ID_NOTATION=" + candidate.providerInstrumentId()
        + "&useExchanges=false&PRICE_TYPE=LAST&ID_SECURITY_TYPE=&GRANULARITY=1D&WITH_EARNINGS=0"
        + "&INSTRUMENT_DATA_NEEDED=1&ID_QUALITY_PRICE=2&MAX_HISTORY_YEARS=33&reqType=TSStimeSeries";
    SecurityBaseDataDto baseData = parseChart(sendExpectOk(HttpRequest.newBuilder(URI.create(url)).GET()), candidate,
        locale);
    return TYPE_BOND.equals(candidate.providerInstrumentType()) ? withBondTerms(baseData, fetchBondTerms(candidate))
        : baseData;
  }

  /**
   * Reads the master data modules of a bond page. A module that cannot be read is skipped, so that the master data of
   * the chart endpoint remain.
   *
   * @param candidate the bond found on the quote page
   * @return label and value of every term, in page order; empty when the page could not be read
   * @throws InterruptedException when interrupted
   */
  private Map<String, String> fetchBondTerms(SecuritySearchCandidateDto candidate) throws InterruptedException {
    Map<String, String> terms = new LinkedHashMap<>();
    try {
      String url = baseUrl + QUOTE_PAGE_DE + "?ISIN=" + URLEncoder.encode(candidate.isin(), StandardCharsets.UTF_8);
      HttpResponse<String> page = send(HttpRequest.newBuilder(URI.create(url)).GET());
      if (page.statusCode() != 200) {
        return terms;
      }
      URI pageUri = page.uri();
      URI postUri = new URI(pageUri.getScheme(), pageUri.getAuthority(), pageUri.getPath(), null, null);
      String query = "?" + pageUri.getRawQuery();
      Matcher placeholder = PLACEHOLDER.matcher(page.body());
      while (placeholder.find()) {
        String moduleId = placeholder.group(1);
        String body = "type=1&ajax-call=1&" + formField("params[" + moduleId + "][action]", "") + "&"
            + formField("params[" + moduleId + "][url-data]", query);
        String answer = sendExpectOk(HttpRequest.newBuilder(postUri).header("Referer", pageUri.toString())
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(body)));
        terms.putAll(parseModule(answer, moduleId));
      }
    } catch (IOException | URISyntaxException | RuntimeException e) {
      log.info("Bond terms of {} could not be read from Wiener Börse: {}", candidate.isin(), e.toString());
    }
    return terms;
  }

  private static String formField(String name, String value) {
    return URLEncoder.encode(name, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  /**
   * Converts the answer for one master data module: JSON with the module id as key and the table as {@code content}.
   * A value "-" means not set and is left out.
   *
   * @param body     the JSON answer
   * @param moduleId id of the requested module
   * @return label and value of every row, in table order
   */
  static Map<String, String> parseModule(String body, String moduleId) {
    Map<String, String> terms = new LinkedHashMap<>();
    String content = text(OBJECT_MAPPER.readTree(body).path(moduleId), "content");
    if (content != null) {
      Matcher row = TERM_ROW.matcher(content);
      while (row.find()) {
        String label = cellText(row.group(1));
        String value = cellText(row.group(2));
        if (!label.isEmpty() && !value.isEmpty() && !"-".equals(value)) {
          terms.put(label, value);
        }
      }
    }
    return terms;
  }

  private static String cellText(String html) {
    return HtmlUtils.htmlUnescape(HTML_TAG.matcher(html).replaceAll("")).strip();
  }

  /**
   * Adds the terms of a bond to its master data. As at SIX, the first listing - or else the start of term - is the
   * first active day, and the maturity the last; denomination and coupon frequency are set when the page names them.
   * The long name replaces the abbreviated name of the chart endpoint when it fits a security name. The remaining
   * terms become raw attributes.
   *
   * @param baseData master data from the chart endpoint
   * @param terms    label and value of the bond terms
   * @return the completed master data
   */
  static SecurityBaseDataDto withBondTerms(SecurityBaseDataDto baseData, Map<String, String> terms) {
    if (terms.isEmpty()) {
      return baseData;
    }
    Map<String, String> remaining = new LinkedHashMap<>(terms);
    String longName = remaining.get(TERM_LONG_NAME);
    String name = baseData.name();
    if (longName != null && longName.length() <= MAX_NAME_LENGTH) {
      name = remaining.remove(TERM_LONG_NAME);
    }
    LocalDate maturity = termDate(remaining.remove(TERM_MATURITY));
    LocalDate start = termDate(remaining.remove(TERM_START));
    LocalDate firstListing = termDate(remaining.remove(TERM_FIRST_LISTING));
    Integer denomination = termInteger(remaining.remove(TERM_DENOMINATION));
    DistributionFrequency frequency = couponFrequencyFromMonths(remaining.remove(TERM_COUPON_MONTHS));
    LocalDate activeFrom = firstListing != null ? firstListing : start;
    LocalDate oldest = LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY);
    if (activeFrom != null && activeFrom.isBefore(oldest)) {
      activeFrom = oldest;
    }
    Map<String, String> attributes = new LinkedHashMap<>(baseData.providerAttributes());
    attributes.putAll(remaining);
    return new SecurityBaseDataDto(baseData.providerId(), baseData.providerInstrumentId(), baseData.isin(),
        baseData.currency(), baseData.mic(), name, baseData.tickerSymbol(), baseData.issuerCountry(),
        activeFrom != null ? activeFrom : baseData.activeFromDate(),
        maturity != null ? maturity : baseData.activeToDate(),
        frequency != null ? frequency : baseData.distributionFrequency(),
        denomination != null ? denomination : baseData.denomination(), baseData.productLink(),
        baseData.stockexchangeLink(), firstListing != null ? firstListing : baseData.listedSince(),
        baseData.distributing(), attributes);
  }

  /**
   * Maps the coupon frequency in months, e.g. 12 for an annual coupon.
   *
   * @param months the raw value, may be null
   * @return the frequency, or null for a value without a counterpart
   */
  static DistributionFrequency couponFrequencyFromMonths(String months) {
    if (months == null) {
      return null;
    }
    return switch (months.strip()) {
    case "12" -> DistributionFrequency.DF_YEAR;
    case "6" -> DistributionFrequency.DF_SEMI_ANNUAL;
    case "3" -> DistributionFrequency.DF_QUARTERLY;
    case "1" -> DistributionFrequency.DF_MONTHLY;
    default -> null;
    };
  }

  /** A date in the form 03.10.2029; null when absent or not in that form. */
  private static LocalDate termDate(String value) {
    try {
      return value == null ? null : LocalDate.parse(value, TERM_DATE);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /** A whole amount in German notation, e.g. 500,00 or 100.000,00; null when absent or fractional. */
  static Integer termInteger(String value) {
    if (value == null) {
      return null;
    }
    try {
      double amount = Double.parseDouble(value.replace(".", "").replace(',', '.'));
      return amount == Math.rint(amount) && amount > 0 && amount <= Integer.MAX_VALUE ? (int) amount : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Converts the chart answer with instrument data into master data.
   *
   * @param body      the JSON response
   * @param candidate the listing found on the quote page
   * @param locale    language of the user, for the stock exchange hyperlink
   * @return the master data
   */
  static SecurityBaseDataDto parseChart(String body, SecuritySearchCandidateDto candidate, Locale locale) {
    JsonNode chart = OBJECT_MAPPER.readTree(body);
    String exchangeCode = text(chart, "exchangeCode");
    if (exchangeCode != null && !VIENNA_EXCHANGE_CODE.equals(exchangeCode)
        && !OEKB_FUND_DATA_CODE.equals(exchangeCode)) {
      throw new SearchFailureException(SecuritySearchStatus.NO_MATCH,
          "Notation " + candidate.providerInstrumentId() + " is traded on " + exchangeCode);
    }
    JsonNode range = chart.path("range");
    LocalDate listedSince = range.isArray() && !range.isEmpty() && range.get(0).isNumber()
        ? Instant.ofEpochMilli(range.get(0).asLong()).atZone(ZoneId.of("Europe/Vienna")).toLocalDate()
        : null;
    LocalDate oldest = LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY);
    LocalDate activeFrom = listedSince == null || listedSince.isAfter(oldest) ? listedSince : oldest;
    Map<String, String> attributes = new LinkedHashMap<>();
    if (candidate.providerInstrumentType() != null) {
      attributes.put("type", candidate.providerInstrumentType());
    }
    attributes.put("idNotation", candidate.providerInstrumentId());
    String name = text(chart, "instrumentName");
    String page = locale != null && "de".equals(locale.getLanguage()) ? QUOTE_PAGE_DE : QUOTE_PAGE_EN;
    String link = DEFAULT_BASE_URL + page + "?ISIN=" + candidate.isin() + "&ID_NOTATION="
        + candidate.providerInstrumentId();
    return new SecurityBaseDataDto(PROVIDER_ID, candidate.providerInstrumentId(), candidate.isin(),
        text(chart, "currency"), candidate.mic(), name != null ? name : candidate.name(), candidate.providerSymbol(),
        null, activeFrom, null, null, null, null, link, listedSince, null, attributes);
  }

  private static String group(Pattern pattern, String text) {
    Matcher m = pattern.matcher(text);
    return m.find() ? m.group(1).trim() : null;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() || value.asString().isBlank() ? null : value.asString();
  }
}
