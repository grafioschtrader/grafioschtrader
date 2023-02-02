package grafioschtrader.connector.instrument;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
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

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.User;
import grafioschtrader.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.Language;
import grafioschtrader.types.SpecialInvestmentInstruments;

public abstract class BaseFeedConnector implements IFeedConnector {

  private static final String ID_PREFIX = "gt.datafeed.";

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  protected ResourceLoader resourceLoader;

  protected Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private String shortId;
  protected String readableName;
  protected String regexStrPattern;

  public BaseFeedConnector(final Map<FeedSupport, FeedIdentifier[]> supportedFeed, final String id,
      final String readableNameKey, String regexStrPattern) {
    this.supportedFeed = supportedFeed;
    this.shortId = id;
    this.readableName = readableNameKey;
    this.regexStrPattern = regexStrPattern;
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
    case HISTORY:
      if (checkAndClearSecuritycurrencyConnector(securitycurrency, feedSupport, securitycurrency.getUrlHistoryExtend(),
          "gt.connector.historical.url.failure", getFeedIdentifierWhenUrlRequired(feedSupport, specInst != null),
          specInst, assetclassType)) {
        securitycurrency.setUrlHistoryExtend(null);
      } else {
        if (specInst != null) {
          if (((Security) securitycurrency).isActiveForIntradayUpdate(new Date())) {
            checkUrl(getSecurityHistoricalDownloadLink((Security) securitycurrency),
                "gt.connector.historical.url.connect.failure");
          }
        } else {
          checkUrl(getCurrencypairHistoricalDownloadLink((Currencypair) securitycurrency),
              "gt.connector.historical.url.connect.failure");
        }
      }
      break;
    case INTRA:
      if (checkAndClearSecuritycurrencyConnector(securitycurrency, feedSupport, securitycurrency.getUrlIntraExtend(),
          "gt.connector.intra.url.failure", getFeedIdentifierWhenUrlRequired(feedSupport, specInst != null), specInst,
          assetclassType)) {
        securitycurrency.setUrlIntraExtend(null);
      } else {
        if (specInst != null) {
          if (((Security) securitycurrency).isActiveForIntradayUpdate(new Date())) {
            checkUrl(getSecurityIntradayDownloadLink((Security) securitycurrency),
                "gt.connector.intra.url.connect.failure");
          }
        } else {
          checkUrl(getCurrencypairIntradayDownloadLink((Currencypair) securitycurrency),
              "gt.connector.intra.url.connect.failure");
        }
      }
      break;
    case DIVIDEND:
      if (checkAndClearSecuritycurrencyConnector(securitycurrency, feedSupport,
          ((Security) securitycurrency).getUrlDividendExtend(), "gt.connector.dividend.url.failure",
          FeedIdentifier.DIVIDEND_URL, specInst, assetclassType)) {
        ((Security) securitycurrency).setUrlDividendExtend(null);
      }
      break;
    case SPLIT:
      if (checkAndClearSecuritycurrencyConnector(securitycurrency, feedSupport,
          ((Security) securitycurrency).getUrlSplitExtend(), "gt.connector.split.url.failure", FeedIdentifier.SPLIT_URL,
          specInst, assetclassType)) {
        ((Security) securitycurrency).setUrlSplitExtend(null);
      }
      break;

    }
  }

  private FeedIdentifier getFeedIdentifierWhenUrlRequired(FeedSupport feedSupport, boolean isSecurity) {
    return isSecurity
        ? Arrays.asList(supportedFeed.get(feedSupport)).contains(FeedIdentifier.SECURITY) ? null
            : FeedIdentifier.SECURITY_URL
        : Arrays.asList(supportedFeed.get(feedSupport)).contains(FeedIdentifier.CURRENCY) ? null
            : FeedIdentifier.CURRENCY_URL;
  }

  protected <S extends Securitycurrency<S>> boolean checkAndClearSecuritycurrencyConnector(
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

  private void checkUrl(String url, String failureMsgKey) {
    try {
      URL u = new URL(url);
      HttpURLConnection.setFollowRedirects(true);
      HttpURLConnection huc = (HttpURLConnection) u.openConnection();
      huc.setRequestProperty("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT);
      huc.setRequestProperty("Accept-Language", "en");
      huc.setRequestMethod("GET");
      huc.connect();
      int code = huc.getResponseCode();
      if (code != HttpURLConnection.HTTP_OK) {
        throw new GeneralNotTranslatedWithArgumentsException(failureMsgKey, new Object[] { url });
      } else {
        if (!isConnectionOk(huc)) {
          throw new GeneralNotTranslatedWithArgumentsException(failureMsgKey, new Object[] { url });
        }
      }
    } catch (Exception e) {
      log.error("URL: {}", url);
    }
  }

  protected boolean isConnectionOk(HttpURLConnection huc) {
    return true;
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
  public List<Securitysplit> getSplitHistory(Security security, LocalDate fromDate) throws Exception {
    throw new UnsupportedOperationException("Not supported yet.");
  }

}
