package grafioschtrader.connector.securitysearch;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
import org.springframework.util.DigestUtils;

import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SecuritySearchStatus;
import io.github.bucket4j.Bandwidth;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * Searches the Xetra and Frankfurt listings of Deutsche Börse, without a key, in two steps:
 * <ol>
 * <li>The TradingView search of Börse Frankfurt ({@code tradingview/search}) returns one entry per listing, with a
 * symbol of the form {@code MIC:ISIN} (e.g. {@code XETR:DE0007164600}), the name and the instrument type. ISIN and MIC
 * are therefore delivered.</li>
 * <li>The search does not carry the trading currency. For the listings on the queried exchange - at most
 * {@link #MAX_DETAIL_REQUESTS} - the price information of the live API supplies it. When that request fails, the
 * currency simply stays unknown; the listing is not dropped.</li>
 * </ol>
 * The provider symbol is the {@code MIC:ISIN} symbol of Börse Frankfurt.
 *
 * <p>
 * Master data combines the search entry (name and instrument type {@code Aktie}, {@code ETP} or {@code Anleihe}) with
 * the price information of the listing (currency, {@code tradedInPercent}, {@code minimumTradableUnit}). Deutsche Börse
 * delivers no ticker. The stock exchange hyperlink is the listing page of Deutsche Börse with the ISIN in place of the
 * name slug, e.g. {@code https://live.deutsche-boerse.com/aktie/DE0007164600?mic=XETR}; the site redirects it to the
 * slug page.
 * </p>
 *
 * <p>
 * The terms of a bond come from {@code master_data_bond} (maturity, first trading day, minimum investment amount,
 * coupon, issuer), {@code interest_rate_widget} (coupon frequency as {@code interestPaymentCycle}) and
 * {@code data_sheet_header} (WKN, which becomes the ticker, since a bond has no exchange ticker). These endpoints
 * answer only requests signed the way the page's own script signs them: {@code Client-Date} is the local time of the
 * request, {@code X-Client-TraceId} the MD5 of date, URL and a salt, {@code X-Security} the MD5 of the minute. The salt
 * is read from the main script of the site and cached; an empty answer {@code {}} means a rejected signature and
 * renews the salt once. A bond whose terms cannot be read keeps the master data of the price information.
 * </p>
 */
@Component
public class XetraSecuritySearch extends BaseSecuritySearchProvider {

  public static final String PROVIDER_ID = "xetra";

  /** Upper bound of price-information requests made to establish the currency within one search. */
  static final int MAX_DETAIL_REQUESTS = 2;

  private static final Set<String> SUPPORTED_MICS = Set.of(GlobalConstants.STOCK_EX_MIC_XETRA,
      GlobalConstants.STOCK_EX_MIC_FRANKFURT);
  private static final String DEFAULT_SEARCH_BASE_URL = "https://api.boerse-frankfurt.de/v1/";
  private static final String DEFAULT_PRICE_BASE_URL = "https://api.live.deutsche-boerse.com/v1/";
  private static final String LISTING_PAGE_BASE = "https://live.deutsche-boerse.com/";
  private static final String DEFAULT_SITE_URL = LISTING_PAGE_BASE;
  static final String TYPE_BOND = "Anleihe";
  /** A WKN has six letters or digits, which is also the length of the ticker column. */
  private static final Pattern WKN = Pattern.compile("[A-Z0-9]{6}");
  private static final Pattern MAIN_SCRIPT = Pattern.compile("src=\"(main\\.[0-9a-f]+\\.js)\"");
  private static final Pattern SALT = Pattern.compile("tracing:\\{salt:\"([0-9a-f]+)\"\\}");
  /** The page signs in the local time of its users; GT signs as a browser in Germany would. */
  static final ZoneId SIGNING_ZONE = ZoneId.of("Europe/Berlin");
  private static final DateTimeFormatter CLIENT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
  private static final DateTimeFormatter SECURITY_MINUTE = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
  private static final Duration SALT_MAX_AGE = Duration.ofHours(24);
  /** Path of the listing page per instrument type of the search; an ETP is an ETF, ETC or ETN. */
  private static final Map<String, String> LISTING_PAGE_PATH = Map.of("Aktie", "aktie", "ETP", "etf", "Anleihe",
      "anleihe");

  private static final Logger log = LoggerFactory.getLogger(XetraSecuritySearch.class);

  private final String searchBaseUrl;
  private final String priceBaseUrl;
  private final String siteUrl;

  /** Salt of the request signature, read from the main script of the site; null until first needed. */
  private String salt;
  private Instant saltReadAt;

  @Autowired
  public XetraSecuritySearch() {
    this(DEFAULT_SEARCH_BASE_URL, DEFAULT_PRICE_BASE_URL, DEFAULT_SITE_URL, DEFAULT_REQUEST_TIMEOUT);
  }

  /**
   * @param searchBaseUrl  base URL of the Börse Frankfurt API, ending with a slash
   * @param priceBaseUrl   base URL of the Deutsche Börse live API, ending with a slash
   * @param siteUrl        base URL of the Deutsche Börse live site, whose main script holds the signature salt
   * @param requestTimeout timeout of a single request
   */
  XetraSecuritySearch(String searchBaseUrl, String priceBaseUrl, String siteUrl, Duration requestTimeout) {
    // A bond needs search, price information, three signed requests and, once a day, two for the salt
    super(Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofSeconds(10)).build(), requestTimeout,
        DEFAULT_MAX_TOKEN_WAIT);
    this.searchBaseUrl = searchBaseUrl;
    this.priceBaseUrl = priceBaseUrl;
    this.siteUrl = siteUrl;
  }

  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  /** Exchanges and instrument types are those of {@code XetraFeedConnector}. */
  @Override
  public Optional<String> getFeedConnectorShortId() {
    return Optional.of(PROVIDER_ID);
  }

  @Override
  public boolean supports(SecuritySearchQuery query) {
    return query.hasValidIsin() && (query.mic() == null || SUPPORTED_MICS.contains(query.mic()));
  }

  @Override
  protected List<SecuritySearchCandidateDto> fetchCandidates(SecuritySearchQuery query) throws Exception {
    String url = searchBaseUrl + "tradingview/search?limit=" + query.maxResults() + "&query=" + query.isin();
    List<SecuritySearchCandidateDto> listings = parseSearch(sendExpectOk(HttpRequest.newBuilder(URI.create(url))));
    List<SecuritySearchCandidateDto> candidates = new ArrayList<>();
    int detailRequests = 0;
    for (SecuritySearchCandidateDto listing : listings) {
      boolean onQueriedMic = query.mic() == null || query.mic().equals(listing.mic());
      if (onQueriedMic && listing.isin() != null && detailRequests < MAX_DETAIL_REQUESTS) {
        detailRequests++;
        listing = withCurrency(listing, fetchCurrency(listing.isin(), listing.mic()));
      }
      candidates.add(listing);
    }
    return candidates;
  }

  @Override
  public boolean supportsBaseData(SecuritySearchCandidateDto candidate) {
    return PROVIDER_ID.equals(candidate.providerId()) && candidate.isin() != null && candidate.mic() != null;
  }

  @Override
  protected SecurityBaseDataDto fetchBaseData(SecuritySearchCandidateDto candidate, Locale locale) throws Exception {
    String url = priceBaseUrl + "data/price_information/single?isin="
        + URLEncoder.encode(candidate.isin(), StandardCharsets.UTF_8) + "&mic="
        + URLEncoder.encode(candidate.mic(), StandardCharsets.UTF_8);
    SecurityBaseDataDto baseData = parseBaseData(sendExpectOk(HttpRequest.newBuilder(URI.create(url))), candidate);
    if (baseData != null && TYPE_BOND.equals(candidate.providerInstrumentType())) {
      baseData = withBondTerms(baseData, fetchSigned("master_data_bond", candidate.isin()),
          fetchSigned("interest_rate_widget", candidate.isin()), fetchSigned("data_sheet_header", candidate.isin()));
    }
    return baseData;
  }

  /**
   * Requests a signed endpoint of the live API. An empty answer counts as a rejected signature: the salt is read again
   * and, when it changed, the request repeated once.
   *
   * @param endpoint path below {@code data/}, e.g. {@code master_data_bond}
   * @param isin     ISIN of the instrument
   * @return the answer, or null when the endpoint delivers nothing or cannot be read
   * @throws InterruptedException when interrupted
   */
  private JsonNode fetchSigned(String endpoint, String isin) throws InterruptedException {
    String url = priceBaseUrl + "data/" + endpoint + "?isin=" + URLEncoder.encode(isin, StandardCharsets.UTF_8);
    try {
      String usedSalt = salt(false);
      for (int attempt = 0; usedSalt != null && attempt < 2; attempt++) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url));
        signatureHeaders(url, usedSalt, ZonedDateTime.now(SIGNING_ZONE)).forEach(request::header);
        HttpResponse<String> response = send(request);
        if (response.statusCode() != 200) {
          return null;
        }
        JsonNode answer = OBJECT_MAPPER.readTree(response.body());
        if (!answer.isEmpty()) {
          return answer;
        }
        String renewed = salt(true);
        usedSalt = renewed != null && !renewed.equals(usedSalt) ? renewed : null;
      }
    } catch (IOException | SearchFailureException | JacksonException e) {
      log.info("{} of {} could not be read from Deutsche Börse: {}", endpoint, isin, e.toString());
    }
    return null;
  }

  /**
   * Returns the cached salt, reading it from the main script of the site when there is none, it is older than a day, or
   * a renewal is requested.
   */
  private synchronized String salt(boolean renew) throws InterruptedException {
    if (renew || salt == null || saltReadAt.plus(SALT_MAX_AGE).isBefore(Instant.now())) {
      try {
        String script = mainScript(sendExpectOk(HttpRequest.newBuilder(URI.create(siteUrl))));
        String read = script == null ? null
            : parseSalt(sendExpectOk(HttpRequest.newBuilder(URI.create(siteUrl + script))));
        if (read != null) {
          salt = read;
          saltReadAt = Instant.now();
        }
      } catch (IOException | SearchFailureException e) {
        log.info("Signature salt of Deutsche Börse could not be read: {}", e.toString());
      }
    }
    return salt;
  }

  /**
   * Finds the main script in the home page of the site.
   *
   * @param html the home page
   * @return the file name, e.g. {@code main.19b2a10d0231cd30.js}, or null
   */
  static String mainScript(String html) {
    Matcher m = MAIN_SCRIPT.matcher(html);
    return m.find() ? m.group(1) : null;
  }

  /**
   * Finds the signature salt in the main script, where the configuration holds it as {@code tracing:{salt:"…"}}.
   *
   * @param script the main script
   * @return the salt, or null
   */
  static String parseSalt(String script) {
    Matcher m = SALT.matcher(script);
    return m.find() ? m.group(1) : null;
  }

  /**
   * Builds the signature headers the script of the site adds to every request of the live API.
   *
   * @param url  the complete request URL, including its query
   * @param salt the salt of the site
   * @param now  the time of the request in the signing zone
   * @return {@code Client-Date}, {@code X-Client-TraceId} and {@code X-Security}
   */
  static Map<String, String> signatureHeaders(String url, String salt, ZonedDateTime now) {
    String clientDate = CLIENT_DATE.format(now);
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Client-Date", clientDate);
    headers.put("X-Client-TraceId", DigestUtils.md5DigestAsHex((clientDate + url + salt).getBytes(StandardCharsets.UTF_8)));
    headers.put("X-Security", DigestUtils.md5DigestAsHex(SECURITY_MINUTE.format(now).getBytes(StandardCharsets.UTF_8)));
    return headers;
  }

  /**
   * Adds the terms of a bond to its master data. As at SIX, the first trading day - or else the issue date - is the
   * first active day and the maturity the last. The denomination is the smallest tradable unit of the price
   * information ("Kleinste handelbare Einheit", e.g. 100000), or else the minimum investment amount, each only when it is
   * at least one whole unit; a Bund reports 0 and 0.01 and keeps it open. The smallest tradable unit needs no signed
   * request, so it is set even when the terms cannot be read. The WKN is the ticker of a bond, which has no exchange
   * ticker. Coupon, issuer and the issue data become raw attributes.
   *
   * @param baseData master data from the price information
   * @param master   answer of {@code master_data_bond}, may be null
   * @param interest answer of {@code interest_rate_widget}, may be null
   * @param header   answer of {@code data_sheet_header}, may be null
   * @return the completed master data
   */
  static SecurityBaseDataDto withBondTerms(SecurityBaseDataDto baseData, JsonNode master, JsonNode interest,
      JsonNode header) {
    JsonNode m = sameIsin(master, baseData.isin()) ? master : OBJECT_MAPPER.createObjectNode();
    JsonNode i = sameIsin(interest, baseData.isin()) ? interest : OBJECT_MAPPER.createObjectNode();
    String wkn = sameIsin(header, baseData.isin()) ? text(header, "wkn") : null;
    if (wkn != null && !WKN.matcher(wkn).matches()) {
      wkn = null;
    }
    Integer smallestTradableUnit = wholeAmount(baseData.providerAttributes().get("minimumTradableUnit"));
    if (m.isEmpty() && i.isEmpty() && smallestTradableUnit == null && wkn == null) {
      return baseData;
    }
    LocalDate firstTradingDay = date(text(m, "firstTradingDay"));
    LocalDate activeFrom = firstTradingDay != null ? firstTradingDay : date(text(m, "issueDate"));
    LocalDate oldest = LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY);
    if (activeFrom != null && activeFrom.isBefore(oldest)) {
      activeFrom = oldest;
    }
    LocalDate maturity = date(text(m, "maturity"));
    Integer denomination = smallestTradableUnit != null ? smallestTradableUnit
        : wholeAmount(m.path("minimumInvestmentAmount"));
    DistributionFrequency frequency = couponFrequency(text(i.path("interestPaymentCycle"), "originalValue"));
    Map<String, String> attributes = new LinkedHashMap<>(baseData.providerAttributes());
    putIfPresent(attributes, "coupon", text(i, "coupon") != null ? text(i, "coupon") : text(m, "cupon"));
    putIfPresent(attributes, "issuer", text(m, "issuer"));
    putIfPresent(attributes, "issueDate", text(m, "issueDate"));
    putIfPresent(attributes, "firstAnnualPayDate", text(i, "firstAnnualPayDate"));
    putIfPresent(attributes, "minimumInvestmentAmount", text(m, "minimumInvestmentAmount"));
    putIfPresent(attributes, "issueVolume", plainNumber(m.path("issueVolume")));
    putIfPresent(attributes, "bondType", text(m.path("type").path("translations"), "en"));
    return new SecurityBaseDataDto(baseData.providerId(), baseData.providerInstrumentId(), baseData.isin(),
        baseData.currency(), baseData.mic(), baseData.name(),
        baseData.tickerSymbol() != null ? baseData.tickerSymbol() : wkn, baseData.issuerCountry(),
        activeFrom != null ? activeFrom : baseData.activeFromDate(),
        maturity != null ? maturity : baseData.activeToDate(),
        frequency != null ? frequency : baseData.distributionFrequency(),
        denomination != null ? denomination : baseData.denomination(), baseData.productLink(),
        baseData.stockexchangeLink(), firstTradingDay != null ? firstTradingDay : baseData.listedSince(),
        baseData.distributing(), attributes);
  }

  /**
   * Maps the {@code interestPaymentCycle} code of Deutsche Börse: 1 annual, 2 semi-annual, 4 quarterly, 6 monthly, 8 no
   * interest dates. Every other code - four-monthly, at maturity, irregular, unknown - has no counterpart.
   *
   * @param code the code, may be null
   * @return the frequency, or null
   */
  static DistributionFrequency couponFrequency(String code) {
    if (code == null) {
      return null;
    }
    return switch (code) {
    case "1" -> DistributionFrequency.DF_YEAR;
    case "2" -> DistributionFrequency.DF_SEMI_ANNUAL;
    case "4" -> DistributionFrequency.DF_QUARTERLY;
    case "6" -> DistributionFrequency.DF_MONTHLY;
    case "8" -> DistributionFrequency.DF_NONE;
    default -> null;
    };
  }

  /** A number without exponent, e.g. 28500000000 instead of 2.85E10; null when absent. */
  private static String plainNumber(JsonNode value) {
    if (!value.isNumber()) {
      return null;
    }
    double amount = value.asDouble();
    return amount == Math.rint(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
  }

  /** An answer is only taken for the instrument it names; one without ISIN or for another instrument is ignored. */
  private static boolean sameIsin(JsonNode answer, String isin) {
    return answer != null && isin != null && isin.equals(text(answer, "isin"));
  }

  private static LocalDate date(String value) {
    try {
      return value == null ? null : LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static Integer wholeAmount(JsonNode value) {
    return value.isNumber() ? wholeAmount(value.asDouble()) : null;
  }

  /** A raw attribute such as {@code 100000}; null when absent or not a number. */
  private static Integer wholeAmount(String value) {
    try {
      return value == null ? null : wholeAmount(Double.parseDouble(value));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Integer wholeAmount(double amount) {
    return amount >= 1 && amount == Math.rint(amount) && amount <= Integer.MAX_VALUE ? (int) amount : null;
  }

  /**
   * Converts the price information of a listing into master data.
   *
   * @param body      the JSON response of {@code price_information/single}
   * @param candidate the search candidate it was requested for
   * @return the master data, or null when the response is empty
   */
  static SecurityBaseDataDto parseBaseData(String body, SecuritySearchCandidateDto candidate) {
    JsonNode info = OBJECT_MAPPER.readTree(body);
    String isin = text(info, "isin");
    if (isin == null) {
      return null;
    }
    String mic = text(info, "mic");
    if (!isin.equals(candidate.isin()) || mic != null && !mic.equals(candidate.mic())) {
      throw new SearchFailureException(SecuritySearchStatus.NO_MATCH,
          "Price information " + mic + ":" + isin + " contradicts " + candidate.providerSymbol());
    }
    String currency = parseCurrency(body);
    Map<String, String> attributes = new LinkedHashMap<>();
    putIfPresent(attributes, "type", candidate.providerInstrumentType());
    putIfPresent(attributes, "exchange", candidate.providerExchangeCode());
    putIfPresent(attributes, "tradedInPercent", text(info, "tradedInPercent"));
    putIfPresent(attributes, "minimumTradableUnit", text(info, "minimumTradableUnit"));
    String path = LISTING_PAGE_PATH.get(candidate.providerInstrumentType());
    String listingPage = path == null ? null : LISTING_PAGE_BASE + path + "/" + isin + "?mic=" + candidate.mic();
    return new SecurityBaseDataDto(PROVIDER_ID, candidate.providerSymbol(), isin,
        currency != null ? currency : candidate.currency(), candidate.mic(), candidate.name(), null, null, null, null,
        null, null, null, listingPage, null, null, attributes);
  }

  private static void putIfPresent(Map<String, String> attributes, String key, String value) {
    if (value != null && !value.isBlank()) {
      attributes.put(key, value);
    }
  }

  /**
   * Converts the TradingView search response. An entry whose symbol is not of the form {@code MIC:ISIN} keeps ISIN and
   * MIC unknown.
   *
   * @param body the JSON array returned by the search
   * @return one candidate per entry
   */
  static List<SecuritySearchCandidateDto> parseSearch(String body) {
    List<SecuritySearchCandidateDto> candidates = new ArrayList<>();
    for (JsonNode entry : OBJECT_MAPPER.readTree(body)) {
      String symbol = text(entry, "symbol");
      String mic = null;
      String isin = null;
      if (symbol != null && symbol.indexOf(':') == 4) {
        mic = symbol.substring(0, 4);
        isin = symbol.substring(5);
      }
      candidates.add(new SecuritySearchCandidateDto(PROVIDER_ID, symbol, null, text(entry, "exchange"), isin,
          isin == null ? IdentityEvidence.UNKNOWN : IdentityEvidence.RETURNED, null, IdentityEvidence.UNKNOWN, mic,
          mic == null ? IdentityEvidence.UNKNOWN : IdentityEvidence.RETURNED, text(entry, "description"),
          text(entry, "type")));
    }
    return candidates;
  }

  /**
   * Extracts the trading currency from a price information response of the live API.
   *
   * @param body the JSON response
   * @return the currency, or null when the response carries none
   */
  static String parseCurrency(String body) {
    JsonNode currency = OBJECT_MAPPER.readTree(body).path("currency").path("originalValue");
    return currency.isMissingNode() || currency.isNull() || currency.asString().isBlank() ? null : currency.asString();
  }

  private String fetchCurrency(String isin, String mic) throws InterruptedException {
    String url = priceBaseUrl + "data/price_information/single?isin=" + URLEncoder.encode(isin, StandardCharsets.UTF_8)
        + "&mic=" + URLEncoder.encode(mic, StandardCharsets.UTF_8);
    try {
      HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url)));
      return response.statusCode() == 200 ? parseCurrency(response.body()) : null;
    } catch (IOException | SearchFailureException | JacksonException e) {
      log.debug("No currency for {} on {}: {}", isin, mic, e.toString());
      return null;
    }
  }

  private static SecuritySearchCandidateDto withCurrency(SecuritySearchCandidateDto c, String currency) {
    if (currency == null) {
      return c;
    }
    return new SecuritySearchCandidateDto(c.providerId(), c.providerSymbol(), c.providerInstrumentId(),
        c.providerExchangeCode(), c.isin(), c.isinEvidence(), currency, IdentityEvidence.RETURNED, c.mic(),
        c.micEvidence(), c.name(), c.providerInstrumentType());
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asString();
  }
}
