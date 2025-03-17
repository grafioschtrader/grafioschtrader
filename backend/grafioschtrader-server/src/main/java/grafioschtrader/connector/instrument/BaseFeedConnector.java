package grafioschtrader.connector.instrument;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
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

public abstract class BaseFeedConnector implements IFeedConnector {

  public static final String ID_PREFIX = "gt.datafeed.";

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  protected ResourceLoader resourceLoader;

  protected Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private String shortId;
  protected String readableName;
  protected String regexStrPattern;
  protected EnumSet<UrlCheck> urlCheckSet;

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

  private FeedIdentifier getFeedIdentifierWhenUrlRequired(FeedSupport feedSupport, boolean isSecurity) {
    return isSecurity
        ? Arrays.asList(supportedFeed.get(feedSupport)).contains(FeedIdentifier.SECURITY) ? null
            : FeedIdentifier.SECURITY_URL
        : Arrays.asList(supportedFeed.get(feedSupport)).contains(FeedIdentifier.CURRENCY) ? null
            : FeedIdentifier.CURRENCY_URL;
  }

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

  protected <S extends Securitycurrency<S>> void extendedCheck(Securitycurrency<S> securitycurrency,
      FeedSupport feedSupport, String urlExtend, String errorMsgKey, FeedIdentifier feedIdentifier,
      SpecialInvestmentInstruments specialInvestmentInstruments, AssetclassType assetclassType) {

  }

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

  protected void checkUrl(String url, String failureMsgKey, FeedSupport feedSupport) {
    try {
      URL u = new URI(url).toURL();
      HttpURLConnection.setFollowRedirects(true);
      HttpURLConnection huc = (HttpURLConnection) u.openConnection();
      huc.setRequestProperty("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT);
      huc.setRequestProperty("Accept-Language", "en");
      huc.setRequestMethod("GET");
      huc.connect();
      int code = huc.getResponseCode();
      if (code != HttpURLConnection.HTTP_OK) {
        throw new GeneralNotTranslatedWithArgumentsException(failureMsgKey, new Object[] { hideApiKeyForError(url) });
      } else {
        if (!isConnectionOk(huc)) {
          throw new GeneralNotTranslatedWithArgumentsException(failureMsgKey, new Object[] { hideApiKeyForError(url) });
        }
      }
    } catch (GeneralNotTranslatedWithArgumentsException ge) {
      throw ge;
    } catch (Exception e) {
      log.error("URL: {}", url);
    }
  }

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

  public String hideApiKeyForError(String url) {
    return url;
  }

  /**
   * During a URL check, HTTP status 200 may be returned even though the
   * corresponding instrument was not found. Therefore, the body of the response
   * should also be evaluated according to the data provider.
   *
   * @param huc
   * @return
   */
  protected boolean isConnectionOk(HttpURLConnection huc) {
    return true;
  }

  protected String getBodyAsString(HttpURLConnection huc) throws IOException {
    var br = new BufferedReader(new InputStreamReader((huc.getInputStream())));
    var sb = new StringBuilder();
    String output;
    while ((output = br.readLine()) != null) {
      sb.append(output);
    }
    return sb.toString();
  }

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
