package grafioschtrader.connector.instrument;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.common.DateHelper;
import grafiosch.entities.User;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafiosch.types.Language;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

/**
 * Abstract base class providing common functionality for feed connectors that retrieve financial market data from
 * external data providers. This class implements the core infrastructure needed by all feed connectors including URL
 * validation, HTTP client management, internationalization support, and rate limiting capabilities.
 * 
 * <h3>Architecture Overview</h3>
 * <p>
 * This base class provides a template method pattern where common functionality is implemented while specific data
 * provider integration is left to concrete subclasses. Key features include:
 * </p>
 * <ul>
 * <li><strong>Feed Support Management:</strong> Defines what types of data the connector supports</li>
 * <li><strong>URL Validation Framework:</strong> Regex-based validation with connectivity testing</li>
 * <li><strong>HTTP Client Infrastructure:</strong> Configured HTTP clients with proper headers and cookie handling</li>
 * <li><strong>Internationalization:</strong> Multi-language description support from resource files</li>
 * <li><strong>Rate Limiting:</strong> Token bucket implementation for API rate limiting</li>
 * <li><strong>Error Handling:</strong> Standardized error handling and logging</li>
 * </ul>
 * 
 * <h3>Subclass Implementation</h3>
 * <p>
 * Concrete implementations should:
 * </p>
 * <ul>
 * <li>Override data retrieval methods (getEodSecurityHistory, updateSecurityLastPrice, etc.)</li>
 * <li>Implement URL generation methods for their specific provider</li>
 * <li>Handle provider-specific data formats and parsing</li>
 * <li>Override validation methods if custom validation is needed</li>
 * </ul>
 * 
 * <h3>URL Validation</h3>
 * <p>
 * The class provides a comprehensive URL validation framework that:
 * </p>
 * <ul>
 * <li>Validates URL patterns using regular expressions</li>
 * <li>Tests actual connectivity to data providers</li>
 * <li>Handles different feed identifier requirements (direct vs URL-based)</li>
 * <li>Supports different validation modes for historical vs intraday data</li>
 * </ul>
 */
public abstract class BaseFeedConnector implements IFeedConnector {

  /**
   * Standard prefix for all feed connector IDs in the system. Attention: Should not be changed, otherwise the
   * persistence must also be adjusted.
   */
  public static final String ID_PREFIX = "gt.datafeed.";

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  protected ResourceLoader resourceLoader;

  protected Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private String shortId;
  protected String readableName;
  protected String regexStrPattern;
  protected EnumSet<UrlCheck> urlCheckSet;

  /**
   * Constructs a new BaseFeedConnector with the specified configuration.
   * 
   * @param supportedFeed   mapping of feed support types to their identifier requirements
   * @param id              the short identifier for this connector (will be prefixed with ID_PREFIX)
   * @param readableName    the human-readable name for display purposes
   * @param regexStrPattern regular expression pattern for URL validation, or null if no pattern validation needed
   * @param urlCheckSet     set of URL check types to perform (HISTORY, INTRADAY, or both)
   */
  public BaseFeedConnector(final Map<FeedSupport, FeedIdentifier[]> supportedFeed, final String id,
      final String readableName, String regexStrPattern, EnumSet<UrlCheck> urlCheckSet) {
    this.supportedFeed = supportedFeed;
    this.shortId = id;
    this.readableName = readableName;
    this.regexStrPattern = regexStrPattern;
    this.urlCheckSet = urlCheckSet;
  }

  @Override
  public Map<FeedSupport, FeedIdentifier[]> getSecuritycurrencyFeedSupport() {
    return supportedFeed;
  }

  @Override
  public FeedIdentifier[] getSecuritycurrencyFeedSupport(final FeedSupport feedSupport) {
    return supportedFeed.get(feedSupport);
  }

  @Override
  public String getID() {
    return ID_PREFIX + shortId;
  }

  @Override
  public String getShortID() {
    return shortId;
  }

  @Override
  public boolean isActivated() {
    return true;
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    return null;
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    return null;
  }

  @Override
  public boolean needHistoricalGapFiller(final Security security) {
    return false;
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return null;
  }

  @Override
  public String getCurrencypairIntradayDownloadLink(final Currencypair currencypair) {
    return null;
  }

  /**
   * Loads and returns localized description text for this connector. Attempts to load description from HTML files in
   * the classpath under i18n/ directory. First tries language-specific file, then falls back to default. Supports
   * section markers [historical] and [intra] to separate content.
   * 
   * @return Description object containing localized help text
   */
  @Override
  public Description getDescription() {
    Language language = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getLanguage();
    boolean intraOn = true;
    boolean historicalOn = true;
    Description description = new Description();
    Resource resource = resourceLoader
        .getResource("classpath:i18n/" + this.getClass().getSimpleName() + "_" + language.getKey() + ".html");
    if (!resource.exists()) {
      resource = resourceLoader.getResource("classpath:i18n/" + this.getClass().getSimpleName() + ".html");
    }
    if (resource.exists()) {
      try (InputStreamReader isr = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
          BufferedReader br = new BufferedReader(isr)) {
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
        log.warn("No description for {}", this.getClass().getSimpleName());
      }
    }
    return description;
  }

  @Override
  public boolean supportsCurrency() {
    for (final FeedIdentifier[] feedIdentifiers : supportedFeed.values()) {
      if (Arrays.asList(feedIdentifiers).contains(FeedIdentifier.CURRENCY)
          || Arrays.asList(feedIdentifiers).contains(FeedIdentifier.CURRENCY_URL)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean supportsSecurity() {
    for (final FeedIdentifier[] feedIdentifiers : supportedFeed.values()) {
      final List<FeedIdentifier> feedIdentifierList = Arrays.asList(feedIdentifiers);
      if (feedIdentifierList.contains(FeedIdentifier.SECURITY)
          || feedIdentifierList.contains(FeedIdentifier.SECURITY_URL)
          || feedIdentifierList.contains(FeedIdentifier.DIVIDEND)
          || feedIdentifierList.contains(FeedIdentifier.DIVIDEND_URL)
          || feedIdentifierList.contains(FeedIdentifier.SPLIT)
          || feedIdentifierList.contains(FeedIdentifier.SPLIT_URL)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public EnumSet<DownloadLink> isDownloadLinkCreatedLazy() {
    return EnumSet.noneOf(DownloadLink.class);
  }

  @Override
  public boolean hasFeedIndentifier(FeedIdentifier feedIdentifier) {
    for (final FeedIdentifier[] feedIdentifiers : supportedFeed.values()) {
      if (Arrays.asList(feedIdentifiers).contains(feedIdentifier)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Validates URL extensions against configured regex patterns. Checks that at least one pattern matches the provided
   * URL extension. Throws an exception if validation fails.
   * 
   * @param patterns  array of regex patterns to validate against
   * @param urlExtend the URL extension to validate
   * @throws GeneralNotTranslatedWithArgumentsException if validation fails
   */
  protected void checkUrlExtendsionWithRegex(String[] patterns, String urlExtend) {
    boolean oneMatches = false;
    String notMatchingPattern = null;
    for (String pattern : patterns) {
      Pattern p = Pattern.compile(pattern);
      Matcher m = p.matcher(urlExtend);
      if (m.matches()) {
        oneMatches = true;
      } else {
        notMatchingPattern = pattern;
      }
    }
    if (!oneMatches) {
      throw new GeneralNotTranslatedWithArgumentsException("gt.connector.regex.url",
          new Object[] { shortId, notMatchingPattern });
    }

  }

  /**
   * Main validation entry point for security currency URL extensions. Performs comprehensive validation including
   * pattern matching and connectivity testing based on the feed support type. May clear invalid URL extensions.
   * 
   * @param securitycurrency the security or currency pair to validate
   * @param feedSupport      the type of feed being validated
   * @param <S>              the type of security currency
   * @throws GeneralNotTranslatedWithArgumentsException if validation fails
   */
  @Override
  public <S extends Securitycurrency<S>> void checkAndClearSecuritycurrencyUrlExtend(
      Securitycurrency<S> securitycurrency, FeedSupport feedSupport) {
    SpecialInvestmentInstruments specInst = securitycurrency instanceof Security security
        ? security.getAssetClass().getSpecialInvestmentInstrument()
        : null;
    AssetclassType assetclassType = specInst == null ? null
        : ((Security) securitycurrency).getAssetClass().getCategoryType();

    switch (feedSupport) {
    case FS_HISTORY:
      if (clearAndCheckUrlPatternSecuritycurrencyConnector(securitycurrency, feedSupport,
          securitycurrency.getUrlHistoryExtend(), "gt.connector.historical.url.failure",
          getFeedIdentifierWhenUrlRequired(feedSupport, specInst != null), specInst, assetclassType)) {
        securitycurrency.setUrlHistoryExtend(null);
        checkUrlForHistory(specInst, securitycurrency);
      } else {
        checkUrlForHistory(specInst, securitycurrency);
      }
      break;
    case FS_INTRA:
      if (clearAndCheckUrlPatternSecuritycurrencyConnector(securitycurrency, feedSupport,
          securitycurrency.getUrlIntraExtend(), "gt.connector.intra.url.failure",
          getFeedIdentifierWhenUrlRequired(feedSupport, specInst != null), specInst, assetclassType)) {
        securitycurrency.setUrlIntraExtend(null);
        checkUrlForIntraday(specInst, securitycurrency);
      } else {
        checkUrlForIntraday(specInst, securitycurrency);
      }
      break;
    case FS_DIVIDEND:
      if (clearAndCheckUrlPatternSecuritycurrencyConnector(securitycurrency, feedSupport,
          ((Security) securitycurrency).getUrlDividendExtend(), "gt.connector.dividend.url.failure",
          FeedIdentifier.DIVIDEND_URL, specInst, assetclassType)) {
        ((Security) securitycurrency).setUrlDividendExtend(null);
      }
      break;
    case FS_SPLIT:
      if (clearAndCheckUrlPatternSecuritycurrencyConnector(securitycurrency, feedSupport,
          ((Security) securitycurrency).getUrlSplitExtend(), "gt.connector.split.url.failure", FeedIdentifier.SPLIT_URL,
          specInst, assetclassType)) {
        ((Security) securitycurrency).setUrlSplitExtend(null);
      }
      break;
    }
  }

  /**
   * Performs URL connectivity validation for historical data feeds. Only validates if HISTORY is included in the
   * urlCheckSet and the instrument is active.
   * 
   * @param specInst         the special investment instrument type, null for currency pairs
   * @param securitycurrency the security or currency pair to validate
   * @param <S>              the type of security currency
   */
  private <S extends Securitycurrency<S>> void checkUrlForHistory(SpecialInvestmentInstruments specInst,
      Securitycurrency<S> securitycurrency) {
    if (urlCheckSet.contains(UrlCheck.HISTORY)) {
      if (specInst != null) {
        if (((Security) securitycurrency).isActiveForIntradayUpdate(new Date())) {
          checkUrl(getSecurityHistoricalDownloadLink((Security) securitycurrency),
              "gt.connector.historical.url.connect.failure", FeedSupport.FS_HISTORY);
        }
      } else {
        checkUrl(getCurrencypairHistoricalDownloadLink((Currencypair) securitycurrency),
            "gt.connector.historical.url.connect.failure", FeedSupport.FS_HISTORY);
      }
    }
  }

  /**
   * Performs URL connectivity validation for intraday data feeds. Only validates if INTRADAY is included in the
   * urlCheckSet and the instrument is active.
   * 
   * @param specInst         the special investment instrument type, null for currency pairs
   * @param securitycurrency the security or currency pair to validate
   * @param <S>              the type of security currency
   */
  private <S extends Securitycurrency<S>> void checkUrlForIntraday(SpecialInvestmentInstruments specInst,
      Securitycurrency<S> securitycurrency) {
    if (urlCheckSet.contains(UrlCheck.INTRADAY)) {
      if (specInst != null) {
        if (((Security) securitycurrency).isActiveForIntradayUpdate(new Date())) {
          checkUrl(getSecurityIntradayDownloadLink((Security) securitycurrency),
              "gt.connector.intra.url.connect.failure", FeedSupport.FS_INTRA);
        }
      } else {
        checkUrl(getCurrencypairIntradayDownloadLink((Currencypair) securitycurrency),
            "gt.connector.intra.url.connect.failure", FeedSupport.FS_INTRA);
      }
    }
  }

  /**
   * Determines the appropriate feed identifier when URL extension is required. Returns null if the feed support type
   * allows direct identification, otherwise returns the URL-based identifier type.
   * 
   * @param feedSupport the feed support type being checked
   * @param isSecurity  true if checking a security, false for currency pair
   * @return the required feed identifier type, or null if URL extension not needed
   */
  private FeedIdentifier getFeedIdentifierWhenUrlRequired(FeedSupport feedSupport, boolean isSecurity) {
    return isSecurity
        ? Arrays.asList(supportedFeed.get(feedSupport)).contains(FeedIdentifier.SECURITY) ? null
            : FeedIdentifier.SECURITY_URL
        : Arrays.asList(supportedFeed.get(feedSupport)).contains(FeedIdentifier.CURRENCY) ? null
            : FeedIdentifier.CURRENCY_URL;
  }

  /**
   * Core validation logic for URL patterns and connectivity. Determines whether to clear URL extensions and performs
   * regex validation. Calls extended validation hooks for subclass customization.
   * 
   * @param securitycurrency             the security or currency pair being validated
   * @param feedSupport                  the type of feed being validated
   * @param urlExtend                    the URL extension to validate
   * @param errorMsgKey                  the error message key for exceptions
   * @param feedIdentifier               the required feed identifier type
   * @param specialInvestmentInstruments the investment instrument type
   * @param assetclassType               the asset class type
   * @param <S>                          the type of security currency
   * @return true if URL extension should be cleared, false otherwise
   */
  protected <S extends Securitycurrency<S>> boolean clearAndCheckUrlPatternSecuritycurrencyConnector(
      Securitycurrency<S> securitycurrency, FeedSupport feedSupport, String urlExtend, String errorMsgKey,
      FeedIdentifier feedIdentifier, SpecialInvestmentInstruments specialInvestmentInstruments,
      AssetclassType assetclassType) {

    boolean clear = shouldClearUrlExtension(urlExtend, errorMsgKey, feedIdentifier, specialInvestmentInstruments,
        assetclassType);
    if (!clear && regexStrPattern != null) {
      checkUrlExtendsionWithRegex(new String[] { regexStrPattern }, urlExtend);
      extendedCheck(securitycurrency, feedSupport, urlExtend, errorMsgKey, feedIdentifier, specialInvestmentInstruments,
          assetclassType);
    }
    return clear;
  }

  /**
   * Extension point for subclasses to provide additional validation logic. Called after basic regex validation passes.
   * The base implementation is empty.
   * 
   * @param securitycurrency             the security or currency pair being validated
   * @param feedSupport                  the type of feed being validated
   * @param urlExtend                    the URL extension being validated
   * @param errorMsgKey                  the error message key for exceptions
   * @param feedIdentifier               the required feed identifier type
   * @param specialInvestmentInstruments the investment instrument type
   * @param assetclassType               the asset class type
   * @param <S>                          the type of security currency
   */
  protected <S extends Securitycurrency<S>> void extendedCheck(Securitycurrency<S> securitycurrency,
      FeedSupport feedSupport, String urlExtend, String errorMsgKey, FeedIdentifier feedIdentifier,
      SpecialInvestmentInstruments specialInvestmentInstruments, AssetclassType assetclassType) {
  }

  /**
   * Determines whether a URL extension should be cleared based on feed identifier requirements. Throws an exception if
   * a required URL extension is missing.
   * 
   * @param urlExtend                    the URL extension to check
   * @param errorMsgKey                  the error message key for exceptions
   * @param feedIdentifier               the required feed identifier type
   * @param specialInvestmentInstruments the investment instrument type (unused in base implementation)
   * @param assetclassType               the asset class type (unused in base implementation)
   * @param <S>                          the type of security currency
   * @return true if URL extension should be cleared, false if it should be kept
   * @throws GeneralNotTranslatedWithArgumentsException if required URL extension is missing
   */
  protected <S extends Securitycurrency<S>> boolean shouldClearUrlExtension(String urlExtend, String errorMsgKey,
      FeedIdentifier feedIdentifier, SpecialInvestmentInstruments specialInvestmentInstruments,
      AssetclassType assetclassType) {

    if (hasFeedIndentifier(feedIdentifier)) {
      if (StringUtils.isEmpty(urlExtend)) {
        throw new GeneralNotTranslatedWithArgumentsException(errorMsgKey, null);
      }
    } else {
      return true;
    }
    return false;
  }

  /**
   * Performs HTTP connectivity check for the specified URL. Sends a GET request and validates that a 200 status code is
   * returned. Logs errors and throws exceptions for connectivity failures.
   * 
   * @param url           the URL to test
   * @param failureMsgKey the error message key for connectivity failures
   * @param feedSupport   the feed support type being tested (for context)
   * @throws GeneralNotTranslatedWithArgumentsException if connectivity check fails
   */
  protected void checkUrl(String url, String failureMsgKey, FeedSupport feedSupport) {
    HttpClient client = getHttpClient();
    HttpRequest request = getRequest(url);
    try {
      HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
      int statusCode = response.statusCode();

      if (statusCode != 200) {
        throw new GeneralNotTranslatedWithArgumentsException(failureMsgKey, new Object[] { hideApiKeyForError(url) });
      }

    } catch (GeneralNotTranslatedWithArgumentsException ge) {
      throw ge;
    } catch (IOException | InterruptedException e) {
      log.error("URL: {}", url, e);
    }
  }

  /**
   * Creates and configures an HTTP client for making requests to data providers. Includes cookie management and
   * redirect handling suitable for web scraping.
   * 
   * @return configured HttpClient instance
   */
  protected HttpClient getHttpClient() {
    CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).cookieHandler(cookieManager).build();
  }

  /**
   * Creates an HTTP GET request with standard headers for financial data providers. Sets user agent and language
   * headers appropriate for the application.
   * 
   * @param url the URL to create a request for
   * @return configured HttpRequest instance
   */
  protected HttpRequest getRequest(String url) {
    return HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT)
        .header("Accept-Language", "en").GET().build();
  }

  /**
   * Retrieves the content of a web page for display in the frontend. Used when the frontend needs to show raw data
   * provider content. Returns "Failure!" if the request fails.
   * 
   * @param httpPageUrl the URL to fetch content from
   * @return the page content as a string, or "Failure!" if the request fails
   */
  @Override
  public String getContentOfPageRequest(String httpPageUrl) {
    String contentPage = null;
    try {
      contentPage = ConnectorHelper.getContentOfHttpRequestAsString(httpPageUrl, true);
    } catch (Exception e) {
      contentPage = "Failure!";
    }
    return contentPage;
  }

  /**
   * Hides sensitive information (like API keys) from URLs before displaying in error messages. The base implementation
   * returns the URL unchanged. Subclasses with API keys should override.
   * 
   * @param url the URL that may contain sensitive information
   * @return the URL with sensitive information hidden
   */
  public String hideApiKeyForError(String url) {
    return url;
  }

  /**
   * Implements rate limiting using a token bucket algorithm. Blocks until a token is available, respecting the bucket's
   * rate limits. Used by data providers that have strict rate limiting requirements.
   * 
   * @param bucket the token bucket to use for rate limiting
   */
  protected void waitForTokenOrGo(Bucket bucket) {
    do {
      ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
      if (probe.isConsumed()) {
        return;
      } else {
        long waitForRefill = TimeUnit.MILLISECONDS.convert(probe.getNanosToWaitForRefill(), TimeUnit.NANOSECONDS);
        try {
          Thread.sleep(waitForRefill);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    } while (true);
  }

  /**
   * Extension point for validating HTTP connections beyond status code checking. Some providers return 200 status even
   * when the instrument is not found, requiring response body validation. The base implementation always returns true.
   * 
   * @param huc the HTTP connection to validate
   * @return true if the connection is considered valid
   */
  protected boolean isConnectionOk(HttpURLConnection huc) {
    return true;
  }

  /**
   * Reads the complete response body from an HTTP connection as a string. Used for processing responses that require
   * body content validation.
   * 
   * @param huc the HTTP connection to read from
   * @return the response body as a string
   * @throws IOException if reading the response fails
   */
  protected String getBodyAsString(HttpURLConnection huc) throws IOException {
    var br = new BufferedReader(new InputStreamReader((huc.getInputStream())));
    var sb = new StringBuilder();
    String output;
    while ((output = br.readLine()) != null) {
      sb.append(output);
    }
    return sb.toString();
  }

  /**
   * Calculates the number of days to wait before the next attempt to retrieve split-adjusted historical data. Uses a
   * graduated approach based on time since split.
   * 
   * @param splitDate the date when the stock split occurred
   * @return number of days to wait, or null if no further attempts should be made
   */
  @Override
  public Integer getNextAttemptInDaysForSplitHistorical(Date splitDate) {
    Integer addDaysForNextAttempt = null;
    long diffNowSplitDate = DateHelper.getDateDiff(splitDate, new Date(), TimeUnit.DAYS);
    if (diffNowSplitDate < 5) {
      return 1;
    } else if (diffNowSplitDate < 10) {
      return 2;
    } else if (diffNowSplitDate < 30) {
      return 3;
    }
    return addDaysForNextAttempt;
  }

  @Override
  public String getReadableName() {
    return readableName;
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final Date from, final Date to)
      throws Exception {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void updateCurrencyPairLastPrice(final Currencypair currencyPair) throws Exception {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  @JsonIgnore
  public int getIntradayDelayedSeconds() {
    throw new UnsupportedOperationException("Not supported yet in " + shortId + ".");
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isDividendSplitAdjusted() {
    return false;
  }

  @Override
  public String getDividendHistoricalDownloadLink(Security security) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public List<Dividend> getDividendHistory(Security security, LocalDate fromDate) throws Exception {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public String getSplitHistoricalDownloadLink(Security security) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public List<Securitysplit> getSplitHistory(Security security, LocalDate fromDate, LocalDate toDate) throws Exception {
    throw new UnsupportedOperationException("Not supported yet.");
  }

}
