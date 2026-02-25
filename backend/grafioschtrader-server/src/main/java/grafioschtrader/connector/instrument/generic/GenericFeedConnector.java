package grafioschtrader.connector.instrument.generic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import grafiosch.common.DateHelper;
import grafiosch.entities.User;
import grafioschtrader.dto.TokenConfig;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GenericConnectorDef;
import grafioschtrader.entities.GenericConnectorEndpoint;
import grafioschtrader.entities.GenericConnectorFieldMapping;
import grafioschtrader.entities.GenericConnectorHttpHeader;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.types.DateFormatType;
import grafioschtrader.types.EndpointOption;
import grafioschtrader.types.HtmlExtractMode;
import grafioschtrader.types.JsonDataStructure;
import grafioschtrader.types.NumberFormatType;
import grafioschtrader.types.RateLimitType;
import grafioschtrader.types.ResponseFormatType;
import grafioschtrader.types.TickerBuildStrategy;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

/**
 * A feed connector that is configured entirely from a GenericConnectorDef database entity rather than from code.
 * Supports JSON, CSV, and HTML (JSoup) response formats for both historical and intraday data, for securities and
 * currency pairs. Not a Spring @Component; instances are created dynamically by GenericFeedConnectorFactory.
 */
public class GenericFeedConnector extends BaseFeedConnector {

  private static final Logger log = LoggerFactory.getLogger(GenericFeedConnector.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final GenericConnectorDef connectorDef;
  private final Bucket rateLimitBucket;
  private final Semaphore rateLimitSemaphore;
  private String apiKey;

  /** Parsed from YAML in constructor; null when no auto-token is configured. */
  private final TokenConfig tokenConfig;
  private String cachedSid;
  private Instant tokenExpiresAt;
  private final Object tokenLock = new Object();

  public GenericFeedConnector(GenericConnectorDef connectorDef, String apiKey) {
    super(buildSupportedFeed(connectorDef), connectorDef.getShortId(), connectorDef.getReadableName(),
        connectorDef.getRegexUrlPattern(),
        buildUrlCheckSet(connectorDef));
    this.connectorDef = connectorDef;
    this.apiKey = apiKey;
    this.rateLimitBucket = buildBucket(connectorDef);
    this.rateLimitSemaphore = buildSemaphore(connectorDef);
    this.tokenConfig = parseTokenConfig(connectorDef.getTokenConfigYaml());
  }

  private static TokenConfig parseTokenConfig(String yaml) {
    if (yaml == null || yaml.isBlank()) {
      return null;
    }
    try {
      return new YAMLMapper().readValue(yaml, TokenConfig.class);
    } catch (Exception e) {
      log.error("Failed to parse token config YAML: {}", e.getMessage());
      return null;
    }
  }

  @Override
  public boolean isActivated() {
    if (!connectorDef.isActivated()) {
      return false;
    }
    // Auto-token connectors don't need a static API key
    if (connectorDef.hasAutoToken()) {
      return true;
    }
    if (connectorDef.isNeedsApiKey() && (apiKey == null || apiKey.isBlank())) {
      return false;
    }
    return true;
  }

  @Override
  public boolean needHistoricalGapFiller(Security security) {
    return connectorDef.isNeedHistoryGapFiller();
  }

  @JsonIgnore
  @Override
  public int getIntradayDelayedSeconds() {
    return connectorDef.getIntradayDelaySeconds();
  }

  @Override
  public Description getDescription() {
    Description description = new Description();
    if (connectorDef.getDescriptionNLS() == null) {
      return description;
    }
    String language;
    try {
      language = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getLanguage().getKey();
    } catch (Exception e) {
      language = "en";
    }
    String text = connectorDef.getDescriptionNLS().getText(language);
    if (text == null) {
      text = connectorDef.getDescriptionNLS().getText("en");
    }
    if (text == null) {
      return description;
    }
    boolean intraOn = true;
    boolean historicalOn = true;
    try (BufferedReader br = new BufferedReader(new StringReader(text))) {
      String line;
      while ((line = br.readLine()) != null) {
        if (line.trim().equalsIgnoreCase("[historical]")) {
          historicalOn = true;
          intraOn = false;
        } else if (line.trim().equalsIgnoreCase("[intra]")) {
          historicalOn = false;
          intraOn = true;
        } else {
          if (historicalOn) {
            description.historicalDescription += line;
          }
          if (intraOn) {
            description.intraDescription += line;
          }
        }
      }
    } catch (Exception e) {
      log.warn("Error parsing description for generic connector {}", connectorDef.getShortId());
    }
    return description;
  }

  @Override
  public String hideApiKeyForError(String url) {
    if (apiKey != null && !apiKey.isBlank()) {
      return url.replace(apiKey, "???");
    }
    return url;
  }

  @Override
  public EnumSet<DownloadLink> isDownloadLinkCreatedLazy() {
    if (connectorDef.isNeedsApiKey() || connectorDef.hasAutoToken()) {
      EnumSet<DownloadLink> set = EnumSet.noneOf(DownloadLink.class);
      if (findEndpoint("FS_HISTORY", "SECURITY") != null || findEndpoint("FS_HISTORY", "CURRENCY") != null) {
        set.add(DownloadLink.DL_HISTORY_FORCE_BACKEND);
      }
      if (findEndpoint("FS_INTRA", "SECURITY") != null || findEndpoint("FS_INTRA", "CURRENCY") != null) {
        set.add(DownloadLink.DL_INTRA_FORCE_BACKEND);
      }
      return set;
    }
    return EnumSet.noneOf(DownloadLink.class);
  }

  @Override
  public String getContentOfPageRequest(String httpPageUrl) {
    try {
      return httpGet(httpPageUrl, null);
    } catch (Exception e) {
      return "Failure!";
    }
  }

  // ======================== Historical Data ========================

  @Override
  public List<Historyquote> getEodSecurityHistory(Security security, Date from, Date to) throws Exception {
    GenericConnectorEndpoint endpoint = findEndpoint("FS_HISTORY", "SECURITY");
    if (endpoint == null) {
      throw new UnsupportedOperationException("No FS_HISTORY+SECURITY endpoint configured for " + getShortID());
    }
    String ticker = buildTicker(endpoint, security.getUrlHistoryExtend(), null);
    return fetchHistory(endpoint, ticker, from, to, security);
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(Currencypair currencyPair, Date from, Date to) throws Exception {
    GenericConnectorEndpoint endpoint = findEndpoint("FS_HISTORY", "CURRENCY");
    if (endpoint == null) {
      throw new UnsupportedOperationException("No FS_HISTORY+CURRENCY endpoint configured for " + getShortID());
    }
    String ticker = buildTicker(endpoint, currencyPair.getUrlHistoryExtend(), currencyPair);
    return fetchHistory(endpoint, ticker, from, to, null);
  }

  @Override
  public String getSecurityHistoricalDownloadLink(Security security) {
    GenericConnectorEndpoint endpoint = findEndpoint("FS_HISTORY", "SECURITY");
    if (endpoint == null) {
      return null;
    }
    String ticker = buildTicker(endpoint, security.getUrlHistoryExtend(), null);
    return buildUrl(endpoint, ticker, new Date(), new Date());
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(Currencypair currencypair) {
    GenericConnectorEndpoint endpoint = findEndpoint("FS_HISTORY", "CURRENCY");
    if (endpoint == null) {
      return null;
    }
    String ticker = buildTicker(endpoint, currencypair.getUrlHistoryExtend(), currencypair);
    return buildUrl(endpoint, ticker, new Date(), new Date());
  }

  // ======================== Intraday Data ========================

  @Override
  public void updateSecurityLastPrice(Security security) throws Exception {
    GenericConnectorEndpoint endpoint = findEndpoint("FS_INTRA", "SECURITY");
    if (endpoint == null) {
      throw new UnsupportedOperationException("No FS_INTRA+SECURITY endpoint configured for " + getShortID());
    }
    String ticker = buildTicker(endpoint, security.getUrlIntraExtend(), null);
    Map<String, Double> values = fetchIntraday(endpoint, ticker);
    applyIntradayValues(security, values);
  }

  @Override
  public void updateCurrencyPairLastPrice(Currencypair currencyPair) throws Exception {
    GenericConnectorEndpoint endpoint = findEndpoint("FS_INTRA", "CURRENCY");
    if (endpoint == null) {
      throw new UnsupportedOperationException("No FS_INTRA+CURRENCY endpoint configured for " + getShortID());
    }
    String ticker = buildTicker(endpoint, currencyPair.getUrlIntraExtend(), currencyPair);
    Map<String, Double> values = fetchIntraday(endpoint, ticker);
    applyIntradayValues(currencyPair, values);
  }

  @Override
  public String getSecurityIntradayDownloadLink(Security security) {
    GenericConnectorEndpoint endpoint = findEndpoint("FS_INTRA", "SECURITY");
    if (endpoint == null) {
      return null;
    }
    String ticker = buildTicker(endpoint, security.getUrlIntraExtend(), null);
    Date now = needsDatePlaceholders(endpoint) ? new Date() : null;
    return buildUrl(endpoint, ticker, now, now);
  }

  @Override
  public String getCurrencypairIntradayDownloadLink(Currencypair currencypair) {
    GenericConnectorEndpoint endpoint = findEndpoint("FS_INTRA", "CURRENCY");
    if (endpoint == null) {
      return null;
    }
    String ticker = buildTicker(endpoint, currencypair.getUrlIntraExtend(), currencypair);
    Date now = needsDatePlaceholders(endpoint) ? new Date() : null;
    return buildUrl(endpoint, ticker, now, now);
  }

  // ======================== Core Fetch Logic ========================

  private List<Historyquote> fetchHistory(GenericConnectorEndpoint endpoint, String ticker, Date from, Date to,
      Security security) throws Exception {
    if (endpoint.getMaxDataPoints() == null) {
      return fetchSingleHistory(endpoint, ticker, from, to, security);
    }
    List<Historyquote> allQuotes = new ArrayList<>();
    Date currentFrom = from;
    for (int i = 0; i < 100; i++) {
      List<Historyquote> batch = fetchSingleHistory(endpoint, ticker, currentFrom, to, security);
      if (batch.isEmpty()) {
        break;
      }
      allQuotes.addAll(batch);
      Date lastDate = batch.get(batch.size() - 1).getDate();
      if (!lastDate.before(to)) {
        break;
      }
      currentFrom = DateHelper.setTimeToZeroAndAddDay(lastDate, 1);
      log.info("Chunked fetch {}: batch {} returned {} rows, last date {}. Next from: {}",
          getShortID(), i + 1, batch.size(), lastDate, currentFrom);
    }
    return allQuotes;
  }

  private List<Historyquote> fetchSingleHistory(GenericConnectorEndpoint endpoint, String ticker, Date from, Date to,
      Security security) throws Exception {
    String url = buildUrl(endpoint, ticker, from, to);
    acquireRateLimit();
    try {
      String body = httpGet(url, endpoint);
      List<Historyquote> quotes = parseHistoryResponse(endpoint, body);
      if (endpoint.getEndpointOptions().contains(EndpointOption.SKIP_WEEKEND_DATA)) {
        quotes.removeIf(hq -> {
          DayOfWeek dow = hq.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().getDayOfWeek();
          return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
        });
      }
      if (security != null && connectorDef.isGbxDividerEnabled()) {
        double divider = FeedConnectorHelper.getGBXLondonDivider(security);
        if (divider != 1.0) {
          for (Historyquote hq : quotes) {
            hq.setClose(hq.getClose() / divider);
            hq.setOpen(hq.getOpen() / divider);
            hq.setHigh(hq.getHigh() / divider);
            hq.setLow(hq.getLow() / divider);
          }
        }
      }
      return quotes;
    } finally {
      releaseRateLimit();
    }
  }

  private Map<String, Double> fetchIntraday(GenericConnectorEndpoint endpoint, String ticker) throws Exception {
    Date from = null;
    Date to = null;
    if (endpoint.getUrlTemplate().contains("{fromDate}") || endpoint.getUrlTemplate().contains("{toDate}")) {
      from = new Date();
      to = new Date();
    }
    String url = buildUrl(endpoint, ticker, from, to);
    acquireRateLimit();
    try {
      String body = httpGet(url, endpoint);
      return parseIntradayResponse(endpoint, body);
    } finally {
      releaseRateLimit();
    }
  }

  // ======================== URL Building ========================

  private String buildUrl(GenericConnectorEndpoint endpoint, String ticker, Date from, Date to) {
    String template = endpoint.getUrlTemplate();

    template = template.replace("{ticker}", ticker != null ? ticker : "");

    if (apiKey != null) {
      template = template.replace("{apiKey}", apiKey);
    }

    if (from != null && to != null) {
      LocalDate fromDate = from.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
      LocalDate toDate = to.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
      long fromUnix = from.getTime() / 1000;
      long toUnix = to.getTime() / 1000;

      template = template.replace("{fromDate}", formatDate(endpoint, fromDate, false));
      template = template.replace("{toDate}", formatDate(endpoint, toDate, true));
      template = template.replace("{fromUnix}", String.valueOf(fromUnix));
      template = template.replace("{toUnix}", String.valueOf(toUnix));
      template = template.replace("{fromUnixMs}", String.valueOf(from.getTime()));
      template = template.replace("{toUnixMs}", String.valueOf(to.getTime()));
    }

    if (endpoint.getMaxDataPoints() != null) {
      template = template.replace("{limit}", String.valueOf(endpoint.getMaxDataPoints()));
    }

    return connectorDef.getDomainUrl() + template;
  }

  private String buildTicker(GenericConnectorEndpoint endpoint, String urlExtend, Currencypair currencyPair) {
    String ticker;
    if (endpoint.getTickerBuildStrategy() == TickerBuildStrategy.CURRENCY_PAIR && currencyPair != null) {
      String sep = endpoint.getCurrencyPairSeparator() != null ? endpoint.getCurrencyPairSeparator() : "";
      String suffix = endpoint.getCurrencyPairSuffix() != null ? endpoint.getCurrencyPairSuffix() : "";
      ticker = currencyPair.getFromCurrency() + sep + currencyPair.getToCurrency() + suffix;
    } else {
      ticker = urlExtend != null ? urlExtend : "";
    }
    if (endpoint.isTickerUppercase()) {
      ticker = ticker.toUpperCase();
    }
    return ticker;
  }

  private String formatDate(GenericConnectorEndpoint endpoint, LocalDate date, boolean isEndOfDay) {
    DateFormatType dft = endpoint.getDateFormatType();
    switch (dft) {
    case UNIX_SECONDS:
      return isEndOfDay
          ? String.valueOf(date.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toEpochSecond())
          : String.valueOf(date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond());
    case UNIX_MILLIS:
      return isEndOfDay
          ? String.valueOf(date.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
          : String.valueOf(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
    case PATTERN:
      return date.format(DateTimeFormatter.ofPattern(endpoint.getDateFormatPattern()));
    case ISO_DATE:
      return date.toString();
    case ISO_DATE_TIME:
      return isEndOfDay ? date.atTime(23, 59, 59).toString() : date.atStartOfDay().toString();
    default:
      return date.toString();
    }
  }

  // ======================== HTTP ========================

  private String httpGet(String url, GenericConnectorEndpoint endpoint) throws Exception {
    ensureTokenValid();
    HttpResponse<String> response = doHttpGet(url);
    if (response.statusCode() == 401 && tokenConfig != null) {
      log.info("Got 401 for {}, invalidating token and retrying", getShortID());
      synchronized (tokenLock) {
        this.apiKey = null;
        this.tokenExpiresAt = null;
      }
      ensureTokenValid();
      response = doHttpGet(url);
    }
    if (response.statusCode() != 200) {
      throw new RuntimeException(
          "HTTP " + response.statusCode() + " for " + hideApiKeyForError(url));
    }
    return response.body();
  }

  private HttpResponse<String> doHttpGet(String url) throws Exception {
    HttpClient client = getHttpClient();
    HttpRequest request = buildHttpGetRequest(url);
    try {
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      log.warn("HTTP request failed for {}, retrying with HTTP/1.1: {}", getShortID(), e.getMessage());
      HttpClient fallbackClient = HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
          .connectTimeout(Duration.ofSeconds(30))
          .build();
      return fallbackClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }

  private HttpRequest buildHttpGetRequest(String url) {
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT)
        .header("Accept-Language", "en")
        .timeout(Duration.ofSeconds(30));

    if (connectorDef.getHttpHeaders() != null) {
      for (GenericConnectorHttpHeader header : connectorDef.getHttpHeaders()) {
        String value = header.getHeaderValue();
        if (apiKey != null) {
          value = value.replace("{apiKey}", apiKey);
        }
        requestBuilder.header(header.getHeaderName(), value);
      }
    }

    requestBuilder.GET();
    return requestBuilder.build();
  }

  // ======================== Auto-Token Lifecycle ========================

  /**
   * Ensures the API key (JWT) is valid when auto-token is configured. In classic mode (tokenConfig == null),
   * this is a no-op. Thread-safe: multiple concurrent requests share a single token.
   */
  private void ensureTokenValid() throws Exception {
    if (tokenConfig == null) {
      return;
    }
    synchronized (tokenLock) {
      if (apiKey != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt)) {
        return;
      }
      if (cachedSid != null && tokenConfig.getRefresh() != null) {
        try {
          refreshToken();
          return;
        } catch (Exception e) {
          log.warn("Token refresh failed for {}, falling back to full acquire: {}", getShortID(), e.getMessage());
          cachedSid = null;
        }
      }
      acquireToken();
    }
  }

  /**
   * Full seed + login flow: GET the seed page, extract a value via regex, POST it to the login URL,
   * and parse the JWT (and optional session ID) from the JSON response.
   */
  private void acquireToken() throws Exception {
    // Use HTTP/1.1 for token operations — LSEG API drops HTTP/2 connections with EOF
    HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    String seedOrigin = URI.create(tokenConfig.getSeed().getUrl()).resolve("/").toString();

    // Step 1: GET seed page
    HttpRequest seedRequest = HttpRequest.newBuilder()
        .uri(URI.create(tokenConfig.getSeed().getUrl()))
        .header("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT)
        .timeout(Duration.ofSeconds(30))
        .GET().build();
    HttpResponse<String> seedResponse = client.send(seedRequest, HttpResponse.BodyHandlers.ofString());
    if (seedResponse.statusCode() != 200) {
      throw new RuntimeException("Seed page returned HTTP " + seedResponse.statusCode() + " for " + getShortID());
    }
    // Step 2: Extract seed value via regex (DOTALL so . matches newlines in multi-line content)
    String regex = tokenConfig.getSeed().getRegex();
    log.info("Auto-token seed regex for {}: [{}]", getShortID(), regex);
    Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
    Matcher matcher = pattern.matcher(seedResponse.body());
    if (!matcher.find()) {
      throw new RuntimeException("Seed regex [" + regex + "] did not match for " + getShortID());
    }
    String seedValue = matcher.group(1);

    // Step 3: POST login with seed value
    String processedSeedValue = seedValue;
    if (tokenConfig.getLogin().isBase64EncodeSeed()) {
      processedSeedValue = Base64.getEncoder().encodeToString(seedValue.getBytes(StandardCharsets.UTF_8));
    }
    String contentType = tokenConfig.getLogin().getContentType();
    String loginBody;
    if ("application/x-www-form-urlencoded".equals(contentType)) {
      // For form-encoded: URL-encode the seed value, then substitute into the body template
      String urlEncodedSeed = URLEncoder.encode(processedSeedValue, StandardCharsets.UTF_8);
      loginBody = tokenConfig.getLogin().getBody().replace("{seedValue}", urlEncodedSeed);
    } else {
      // For JSON: JSON-escape the seed value for safe embedding
      String escapedSeedValue = objectMapper.writeValueAsString(processedSeedValue);
      escapedSeedValue = escapedSeedValue.substring(1, escapedSeedValue.length() - 1);
      loginBody = tokenConfig.getLogin().getBody().replace("{seedValue}", escapedSeedValue);
    }
    HttpRequest loginRequest = HttpRequest.newBuilder()
        .uri(URI.create(tokenConfig.getLogin().getUrl()))
        .header("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT)
        .header("Content-Type", contentType)
        .header("Accept", "application/json")
        .header("Origin", seedOrigin)
        .header("Referer", tokenConfig.getSeed().getUrl())
        .timeout(Duration.ofSeconds(30))
        .POST(HttpRequest.BodyPublishers.ofString(loginBody)).build();
    HttpResponse<String> loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
    if (loginResponse.statusCode() != 200 && loginResponse.statusCode() != 201) {
      log.error("Login POST failed for {}: HTTP {}, body: {}", getShortID(), loginResponse.statusCode(),
          loginResponse.body() != null ? loginResponse.body().substring(0, Math.min(loginResponse.body().length(), 500)) : "null");
      throw new RuntimeException("Login returned HTTP " + loginResponse.statusCode() + " for " + getShortID());
    }

    // Step 4: Parse JWT and optional session ID
    JsonNode loginJson = objectMapper.readTree(loginResponse.body());
    JsonNode jwtNode = navigatePath(loginJson, tokenConfig.getLogin().getJwtPath());
    if (jwtNode == null || jwtNode.isNull()) {
      throw new RuntimeException("JWT not found at path '" + tokenConfig.getLogin().getJwtPath() + "' for " + getShortID());
    }
    this.apiKey = jwtNode.asText();
    this.tokenExpiresAt = Instant.now().plusSeconds(tokenConfig.getTtlSeconds());

    if (tokenConfig.getLogin().getSessionPath() != null) {
      JsonNode sidNode = navigatePath(loginJson, tokenConfig.getLogin().getSessionPath());
      if (sidNode != null && !sidNode.isNull()) {
        this.cachedSid = sidNode.asText();
      }
    }
    log.info("Auto-token acquired for {} (ttl={}s, sid={})", getShortID(), tokenConfig.getTtlSeconds(), cachedSid != null);
  }

  /**
   * Refreshes the JWT using the cached session ID, without re-running the seed step.
   */
  private void refreshToken() throws Exception {
    HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    TokenConfig.RefreshConfig rc = tokenConfig.getRefresh();
    HttpRequest refreshRequest = HttpRequest.newBuilder()
        .uri(URI.create(rc.getUrl()))
        .header("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT)
        .header("Accept", "application/json")
        .header(rc.getSidHeader(), cachedSid)
        .timeout(Duration.ofSeconds(30))
        .POST(HttpRequest.BodyPublishers.noBody()).build();
    HttpResponse<String> refreshResponse = client.send(refreshRequest, HttpResponse.BodyHandlers.ofString());
    if (refreshResponse.statusCode() != 200) {
      throw new RuntimeException("Token refresh returned HTTP " + refreshResponse.statusCode());
    }

    JsonNode refreshJson = objectMapper.readTree(refreshResponse.body());
    JsonNode jwtNode = navigatePath(refreshJson, tokenConfig.getLogin().getJwtPath());
    if (jwtNode == null || jwtNode.isNull()) {
      throw new RuntimeException("JWT not found in refresh response");
    }
    this.apiKey = jwtNode.asText();
    this.tokenExpiresAt = Instant.now().plusSeconds(tokenConfig.getTtlSeconds());
    log.debug("Auto-token refreshed for {} (ttl={}s)", getShortID(), tokenConfig.getTtlSeconds());
  }

  // ======================== Response Parsing ========================

  private List<Historyquote> parseHistoryResponse(GenericConnectorEndpoint endpoint, String body) throws Exception {
    ResponseFormatType format = endpoint.getResponseFormat();
    switch (format) {
    case JSON:
      return parseJsonHistory(endpoint, body);
    case CSV:
      return parseCsvHistory(endpoint, body);
    default:
      throw new UnsupportedOperationException("Response format " + format + " not supported for history");
    }
  }

  private Map<String, Double> parseIntradayResponse(GenericConnectorEndpoint endpoint, String body) throws Exception {
    ResponseFormatType format = endpoint.getResponseFormat();
    switch (format) {
    case JSON:
      return parseJsonIntraday(endpoint, body);
    case CSV:
      return parseCsvIntraday(endpoint, body);
    case HTML:
      return parseHtmlIntraday(endpoint, body);
    default:
      throw new UnsupportedOperationException("Response format " + format + " not supported for intraday");
    }
  }

  // ======================== JSON Parsing ========================

  private List<Historyquote> parseJsonHistory(GenericConnectorEndpoint endpoint, String body) throws Exception {
    JsonNode root = objectMapper.readTree(body);

    if (endpoint.getJsonStatusPath() != null) {
      JsonNode statusNode = navigatePath(root, endpoint.getJsonStatusPath());
      if (statusNode != null && endpoint.getJsonStatusOkValue() != null
          && !endpoint.getJsonStatusOkValue().equals(statusNode.asText())) {
        throw new RuntimeException("Data provider status error: " + statusNode.asText());
      }
    }

    JsonNode dataNode = endpoint.getJsonDataPath() != null ? navigatePath(root, endpoint.getJsonDataPath()) : root;
    List<Historyquote> quotes = new ArrayList<>();
    JsonDataStructure structure = endpoint.getJsonDataStructure();

    if (structure == JsonDataStructure.ARRAY_OF_OBJECTS && dataNode.isArray()) {
      for (JsonNode row : dataNode) {
        Historyquote hq = new Historyquote();
        for (GenericConnectorFieldMapping mapping : endpoint.getFieldMappings()) {
          JsonNode valueNode = navigatePath(row, mapping.getSourceExpression());
          if (valueNode != null && !valueNode.isNull()) {
            setHistoryField(hq, mapping, valueNode.asText(), endpoint);
          }
        }
        if (hq.getDate() != null) {
          quotes.add(hq);
        }
      }
    } else if (structure == JsonDataStructure.PARALLEL_ARRAYS && dataNode.isObject()) {
      Map<String, JsonNode> arrays = new HashMap<>();
      for (GenericConnectorFieldMapping mapping : endpoint.getFieldMappings()) {
        JsonNode arr = navigatePath(dataNode, mapping.getSourceExpression());
        if (arr != null && arr.isArray()) {
          arrays.put(mapping.getTargetField(), arr);
        }
      }
      int size = arrays.values().stream().mapToInt(JsonNode::size).min().orElse(0);
      for (int i = 0; i < size; i++) {
        Historyquote hq = new Historyquote();
        for (GenericConnectorFieldMapping mapping : endpoint.getFieldMappings()) {
          JsonNode arr = arrays.get(mapping.getTargetField());
          if (arr != null && i < arr.size()) {
            setHistoryField(hq, mapping, arr.get(i).asText(), endpoint);
          }
        }
        if (hq.getDate() != null) {
          quotes.add(hq);
        }
      }
    }

    return quotes;
  }

  private Map<String, Double> parseJsonIntraday(GenericConnectorEndpoint endpoint, String body) throws Exception {
    JsonNode root = objectMapper.readTree(body);

    if (endpoint.getJsonStatusPath() != null) {
      JsonNode statusNode = navigatePath(root, endpoint.getJsonStatusPath());
      if (statusNode != null && endpoint.getJsonStatusOkValue() != null
          && !endpoint.getJsonStatusOkValue().equals(statusNode.asText())) {
        throw new RuntimeException("Data provider status error: " + statusNode.asText());
      }
    }

    JsonNode dataNode = endpoint.getJsonDataPath() != null ? navigatePath(root, endpoint.getJsonDataPath()) : root;

    if (dataNode.isArray() && dataNode.size() > 0) {
      dataNode = dataNode.get(0);
    }

    Map<String, Double> values = new HashMap<>();
    for (GenericConnectorFieldMapping mapping : endpoint.getFieldMappings()) {
      JsonNode valueNode = navigatePath(dataNode, mapping.getSourceExpression());
      if (valueNode != null && !valueNode.isNull()) {
        double val = parseNumber(valueNode.asText(), endpoint.getNumberFormat());
        if (mapping.getDividerExpression() != null) {
          val /= Double.parseDouble(mapping.getDividerExpression());
        }
        values.put(mapping.getTargetField(), val);
      }
    }
    return values;
  }

  private JsonNode navigatePath(JsonNode node, String dotPath) {
    String[] parts = dotPath.split("\\.");
    for (String part : parts) {
      if (node == null) {
        return null;
      }
      try {
        int index = Integer.parseInt(part);
        node = node.get(index);
      } catch (NumberFormatException e) {
        node = node.get(part);
      }
    }
    return node;
  }

  // ======================== CSV Parsing ========================

  private List<Historyquote> parseCsvHistory(GenericConnectorEndpoint endpoint, String body) {
    List<Historyquote> quotes = new ArrayList<>();
    String[] lines = body.split("\\r?\\n");
    int skip = endpoint.getCsvSkipHeaderLines() != null ? endpoint.getCsvSkipHeaderLines() : 1;
    String delimiter = resolveCsvDelimiter(endpoint);

    for (int i = skip; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.isEmpty()) {
        continue;
      }
      String[] columns = line.split(delimiter, -1);
      Historyquote hq = new Historyquote();
      boolean valid = true;
      for (GenericConnectorFieldMapping mapping : endpoint.getFieldMappings()) {
        int colIdx = mapping.getCsvColumnIndex() != null ? mapping.getCsvColumnIndex() : 0;
        if (colIdx < columns.length) {
          try {
            setHistoryField(hq, mapping, columns[colIdx].trim(), endpoint);
          } catch (Exception e) {
            if (mapping.isRequired()) {
              valid = false;
            }
          }
        }
      }
      if (valid && hq.getDate() != null) {
        quotes.add(hq);
      }
    }
    return quotes;
  }

  private Map<String, Double> parseCsvIntraday(GenericConnectorEndpoint endpoint, String body) {
    String[] lines = body.split("\\r?\\n");
    int skip = endpoint.getCsvSkipHeaderLines() != null ? endpoint.getCsvSkipHeaderLines() : 1;
    String delimiter = resolveCsvDelimiter(endpoint);

    Map<String, Double> values = new HashMap<>();
    if (lines.length > skip) {
      String[] columns = lines[skip].trim().split(delimiter, -1);
      for (GenericConnectorFieldMapping mapping : endpoint.getFieldMappings()) {
        int colIdx = mapping.getCsvColumnIndex() != null ? mapping.getCsvColumnIndex() : 0;
        if (colIdx < columns.length && !"date".equals(mapping.getTargetField())
            && !"timestamp".equals(mapping.getTargetField())) {
          try {
            double val = parseNumber(columns[colIdx].trim(), endpoint.getNumberFormat());
            if (mapping.getDividerExpression() != null) {
              val /= Double.parseDouble(mapping.getDividerExpression());
            }
            values.put(mapping.getTargetField(), val);
          } catch (Exception e) {
            log.debug("Failed to parse CSV intraday column {} for {}", colIdx, getShortID());
          }
        }
      }
    }
    return values;
  }

  private String resolveCsvDelimiter(GenericConnectorEndpoint endpoint) {
    String delimiter = endpoint.getCsvDelimiter();
    if (delimiter == null) {
      return ",";
    }
    if ("\\t".equals(delimiter)) {
      return "\t";
    }
    return Pattern.quote(delimiter);
  }

  // ======================== HTML Parsing (JSoup) ========================

  private Map<String, Double> parseHtmlIntraday(GenericConnectorEndpoint endpoint, String body) throws Exception {
    Document doc = Jsoup.parse(body);
    HtmlExtractMode mode = endpoint.getHtmlExtractMode();

    switch (mode) {
    case REGEX_GROUPS:
      return parseHtmlRegexGroups(endpoint, doc);
    case SPLIT_POSITIONS:
      return parseHtmlSplitPositions(endpoint, doc);
    case MULTI_SELECTOR:
      return parseHtmlMultiSelector(endpoint, doc);
    default:
      throw new UnsupportedOperationException("HTML extract mode " + mode + " not supported");
    }
  }

  private Map<String, Double> parseHtmlRegexGroups(GenericConnectorEndpoint endpoint, Document doc) {
    Element element = doc.select(endpoint.getHtmlCssSelector()).first();
    if (element == null) {
      throw new RuntimeException("CSS selector '" + endpoint.getHtmlCssSelector() + "' found no element");
    }
    String text = element.text();
    if (endpoint.getHtmlTextCleanup() != null) {
      text = text.replaceAll(endpoint.getHtmlTextCleanup(), "").trim();
    }

    Pattern pattern = Pattern.compile(endpoint.getHtmlExtractRegex());
    Matcher matcher = pattern.matcher(text);
    Map<String, Double> values = new HashMap<>();
    if (matcher.find()) {
      for (GenericConnectorFieldMapping mapping : endpoint.getFieldMappings()) {
        try {
          int groupIndex = Integer.parseInt(mapping.getSourceExpression());
          if (groupIndex <= matcher.groupCount()) {
            String rawValue = matcher.group(groupIndex);
            double val = parseNumber(rawValue, endpoint.getNumberFormat());
            if (mapping.getDividerExpression() != null) {
              val /= Double.parseDouble(mapping.getDividerExpression());
            }
            values.put(mapping.getTargetField(), val);
          }
        } catch (Exception e) {
          log.debug("Failed to extract field {} from group '{}': {}", mapping.getTargetField(),
              mapping.getSourceExpression(), e.getMessage());
        }
      }
    }
    return values;
  }

  private Map<String, Double> parseHtmlSplitPositions(GenericConnectorEndpoint endpoint, Document doc) {
    Element element = doc.select(endpoint.getHtmlCssSelector()).first();
    if (element == null) {
      throw new RuntimeException("CSS selector '" + endpoint.getHtmlCssSelector() + "' found no element");
    }
    String text = element.text();
    if (endpoint.getHtmlTextCleanup() != null) {
      text = text.replaceAll(endpoint.getHtmlTextCleanup(), "").trim();
    }

    String delimiter = endpoint.getHtmlSplitDelimiter() != null ? endpoint.getHtmlSplitDelimiter() : "\\s+";
    String[] parts = text.split(delimiter);
    Map<String, Double> values = new HashMap<>();
    for (GenericConnectorFieldMapping mapping : endpoint.getFieldMappings()) {
      int idx = mapping.getCsvColumnIndex() != null ? mapping.getCsvColumnIndex() : 0;
      if (idx < parts.length) {
        try {
          double val = parseNumber(parts[idx].trim(), endpoint.getNumberFormat());
          if (mapping.getDividerExpression() != null) {
            val /= Double.parseDouble(mapping.getDividerExpression());
          }
          values.put(mapping.getTargetField(), val);
        } catch (Exception e) {
          log.debug("Failed to parse HTML split position {} for {}", idx, getShortID());
        }
      }
    }
    return values;
  }

  private Map<String, Double> parseHtmlMultiSelector(GenericConnectorEndpoint endpoint, Document doc) {
    Map<String, Double> values = new HashMap<>();
    for (GenericConnectorFieldMapping mapping : endpoint.getFieldMappings()) {
      Element element = doc.select(mapping.getSourceExpression()).first();
      if (element != null) {
        String text = element.text();
        if (endpoint.getHtmlTextCleanup() != null) {
          text = text.replaceAll(endpoint.getHtmlTextCleanup(), "").trim();
        }
        try {
          double val = parseNumber(text, endpoint.getNumberFormat());
          if (mapping.getDividerExpression() != null) {
            val /= Double.parseDouble(mapping.getDividerExpression());
          }
          values.put(mapping.getTargetField(), val);
        } catch (Exception e) {
          log.debug("Failed to parse HTML multi-selector for {}: {}", mapping.getTargetField(), e.getMessage());
        }
      }
    }
    return values;
  }

  // ======================== Field Mapping ========================

  private void setHistoryField(Historyquote hq, GenericConnectorFieldMapping mapping, String rawValue,
      GenericConnectorEndpoint endpoint) {
    if (rawValue == null || rawValue.isEmpty()) {
      return;
    }
    String target = mapping.getTargetField();
    try {
      if ("date".equals(target)) {
        hq.setDate(parseDate(rawValue, endpoint));
      } else {
        double val = parseNumber(rawValue, endpoint.getNumberFormat());
        if (mapping.getDividerExpression() != null) {
          val /= Double.parseDouble(mapping.getDividerExpression());
        }
        switch (target) {
        case "open":
          hq.setOpen(val);
          break;
        case "high":
          hq.setHigh(val);
          break;
        case "low":
          hq.setLow(val);
          break;
        case "close":
          hq.setClose(val);
          break;
        case "volume":
          hq.setVolume((long) val);
          break;
        }
      }
    } catch (Exception e) {
      log.debug("Failed to set history field '{}' from '{}': {}", target, rawValue, e.getMessage());
    }
  }

  private void applyIntradayValues(Security security, Map<String, Double> values) {
    if (values.containsKey("last")) {
      security.setSLast(values.get("last"));
    }
    if (values.containsKey("open")) {
      security.setSOpen(values.get("open"));
    }
    if (values.containsKey("high")) {
      security.setSHigh(values.get("high"));
    }
    if (values.containsKey("low")) {
      security.setSLow(values.get("low"));
    }
    if (values.containsKey("volume")) {
      security.setSVolume(values.get("volume").longValue());
    }
    if (values.containsKey("changePercentage")) {
      security.setSChangePercentage(values.get("changePercentage"));
    }
    if (values.containsKey("prevClose")) {
      security.setSPrevClose(values.get("prevClose"));
    }
    security.setSTimestamp(new Date());
  }

  private void applyIntradayValues(Currencypair currencyPair, Map<String, Double> values) {
    if (values.containsKey("last")) {
      currencyPair.setSLast(values.get("last"));
    }
    if (values.containsKey("open")) {
      currencyPair.setSOpen(values.get("open"));
    }
    if (values.containsKey("high")) {
      currencyPair.setSHigh(values.get("high"));
    }
    if (values.containsKey("low")) {
      currencyPair.setSLow(values.get("low"));
    }
    if (values.containsKey("changePercentage")) {
      currencyPair.setSChangePercentage(values.get("changePercentage"));
    }
    if (values.containsKey("prevClose")) {
      currencyPair.setSPrevClose(values.get("prevClose"));
    }
    currencyPair.setSTimestamp(new Date());
  }

  // ======================== Date & Number Parsing ========================

  private Date parseDate(String rawValue, GenericConnectorEndpoint endpoint) {
    DateFormatType dft = endpoint.getDateFormatType();
    try {
      switch (dft) {
      case UNIX_SECONDS:
        long epochSec = Long.parseLong(rawValue);
        return Date.from(Instant.ofEpochSecond(epochSec));
      case UNIX_MILLIS:
        long epochMs = Long.parseLong(rawValue);
        return Date.from(Instant.ofEpochMilli(epochMs));
      case PATTERN:
        LocalDate ld = LocalDate.parse(rawValue, DateTimeFormatter.ofPattern(endpoint.getDateFormatPattern()));
        return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
      case ISO_DATE:
        LocalDate isoDate = LocalDate.parse(rawValue);
        return Date.from(isoDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
      case ISO_DATE_TIME:
        LocalDate isoDt = LocalDate.parse(rawValue.substring(0, Math.min(rawValue.length(), 10)));
        return Date.from(isoDt.atStartOfDay(ZoneId.systemDefault()).toInstant());
      default:
        return null;
      }
    } catch (Exception e) {
      log.debug("Failed to parse date '{}' with format {}: {}", rawValue, dft, e.getMessage());
      return null;
    }
  }

  private double parseNumber(String rawValue, NumberFormatType numberFormat) throws ParseException {
    if (rawValue == null || rawValue.isBlank()) {
      return 0.0;
    }
    switch (numberFormat) {
    case GERMAN:
      return FeedConnectorHelper.parseDoubleGE(rawValue);
    case US:
      return FeedConnectorHelper.parseDoubleUS(rawValue);
    case SWISS:
      return FeedConnectorHelper.parseDoubleCH(rawValue);
    case PLAIN:
    default:
      return Double.parseDouble(rawValue.replace(",", "").trim());
    }
  }

  // ======================== Rate Limiting ========================

  private void acquireRateLimit() {
    if (rateLimitBucket != null) {
      waitForTokenOrGo(rateLimitBucket);
    }
    if (rateLimitSemaphore != null) {
      try {
        rateLimitSemaphore.acquire();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void releaseRateLimit() {
    if (rateLimitSemaphore != null) {
      rateLimitSemaphore.release();
    }
  }

  // ======================== Static Builders ========================

  private static Map<FeedSupport, FeedIdentifier[]> buildSupportedFeed(GenericConnectorDef def) {
    Map<FeedSupport, FeedIdentifier[]> map = new HashMap<>();
    if (def.getEndpoints() != null) {
      for (GenericConnectorEndpoint ep : def.getEndpoints()) {
        FeedSupport fs = "FS_HISTORY".equals(ep.getFeedSupport()) ? FeedSupport.FS_HISTORY : FeedSupport.FS_INTRA;
        boolean isSecurity = "SECURITY".equals(ep.getInstrumentType());
        boolean isCurrency = "CURRENCY".equals(ep.getInstrumentType());

        List<FeedIdentifier> identifiers = new ArrayList<>();
        FeedIdentifier[] existing = map.get(fs);
        if (existing != null) {
          for (FeedIdentifier fi : existing) {
            identifiers.add(fi);
          }
        }
        if (isSecurity) {
          identifiers.add(FeedIdentifier.SECURITY_URL);
        }
        if (isCurrency) {
          identifiers.add(ep.getTickerBuildStrategy() == TickerBuildStrategy.CURRENCY_PAIR
              ? FeedIdentifier.CURRENCY
              : FeedIdentifier.CURRENCY_URL);
        }
        map.put(fs, identifiers.toArray(new FeedIdentifier[0]));
      }
    }
    return map;
  }

  private static EnumSet<UrlCheck> buildUrlCheckSet(GenericConnectorDef def) {
    return EnumSet.noneOf(UrlCheck.class);
  }

  private static Bucket buildBucket(GenericConnectorDef def) {
    if (def.getRateLimitType() == RateLimitType.TOKEN_BUCKET
        && def.getRateLimitRequests() != null && def.getRateLimitPeriodSec() != null) {
      return Bucket.builder()
          .addLimit(Bandwidth.simple(def.getRateLimitRequests(), Duration.ofSeconds(def.getRateLimitPeriodSec())))
          .build();
    }
    return null;
  }

  private static Semaphore buildSemaphore(GenericConnectorDef def) {
    if (def.getRateLimitType() == RateLimitType.SEMAPHORE && def.getRateLimitConcurrent() != null) {
      return new Semaphore(def.getRateLimitConcurrent());
    }
    return null;
  }

  // ======================== Endpoint Lookup ========================

  private boolean needsDatePlaceholders(GenericConnectorEndpoint endpoint) {
    String tpl = endpoint.getUrlTemplate();
    return tpl.contains("{fromDate}") || tpl.contains("{toDate}");
  }

  private GenericConnectorEndpoint findEndpoint(String feedSupport, String instrumentType) {
    if (connectorDef.getEndpoints() != null) {
      for (GenericConnectorEndpoint ep : connectorDef.getEndpoints()) {
        if (feedSupport.equals(ep.getFeedSupport()) && instrumentType.equals(ep.getInstrumentType())) {
          return ep;
        }
      }
    }
    return null;
  }

  @JsonIgnore
  public GenericConnectorDef getConnectorDef() {
    return connectorDef;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }
}
