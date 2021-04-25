package grafioschtrader.connector.instrument;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector.FeedIdentifier;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.User;
import grafioschtrader.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.types.Language;

public abstract class BaseFeedConnector implements IFeedConnector {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private static final String ID_PREFIX = "gt.datafeed.";

  @Autowired
  protected ResourceLoader resourceLoader;

  protected Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  protected String id;
  protected String readableName;

  public BaseFeedConnector(final Map<FeedSupport, FeedIdentifier[]> supportedFeed, final String id,
      final String readableNameKey) {
    this.supportedFeed = supportedFeed;
    this.id = ID_PREFIX + id;
    this.readableName = readableNameKey;
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
    return id;
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
      try (InputStreamReader isr = new InputStreamReader(resource.getInputStream(), StandardCharsets.ISO_8859_1);
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

  public boolean hasFeedIndentifier(FeedIdentifier feedIdentifier) {
    for (final FeedIdentifier[] feedIdentifiers : supportedFeed.values()) {
      if (Arrays.asList(feedIdentifiers).contains(feedIdentifier)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public <S extends Securitycurrency<S>> void checkAndClearSecuritycurrencyUrlExtend(
      Securitycurrency<S> securitycurrency, FeedSupport feedSupport) {
    switch (feedSupport) {
    case HISTORY:
      if (checkAndClearSecuritycurrencyConnector(securitycurrency.getUrlHistoryExtend(),
          "gt.connector.historical.url.failure",
          securitycurrency instanceof Security ? FeedIdentifier.SECURITY_URL : FeedIdentifier.CURRENCY_URL)) {
        securitycurrency.setUrlHistoryExtend(null);
      }
      break;
    case INTRA:
      if (checkAndClearSecuritycurrencyConnector(securitycurrency.getUrlIntraExtend(), "gt.connector.intra.url.failure",
          securitycurrency instanceof Security ? FeedIdentifier.SECURITY_URL : FeedIdentifier.CURRENCY_URL)) {
        securitycurrency.setUrlIntraExtend(null);
      }
      break;
    case DIVIDEND:
      if (checkAndClearSecuritycurrencyConnector(((Security) securitycurrency).getUrlDividendExtend(),
          "gt.connector.dividend.url.failure", FeedIdentifier.DIVIDEND_URL)) {
        ((Security) securitycurrency).setUrlDividendExtend(null);
      }
      break;
    case SPLIT:
      if (checkAndClearSecuritycurrencyConnector(((Security) securitycurrency).getUrlSplitExtend(),
          "gt.connector.split.url.failure", FeedIdentifier.SPLIT_URL)) {
        ((Security) securitycurrency).setUrlSplitExtend(null);
      }
      break;
      
    }
  }

  protected <S extends Securitycurrency<S>> boolean checkAndClearSecuritycurrencyConnector(String urlExtend,
      String errorMsgKey, FeedIdentifier feedIdentifier) {

    if (hasFeedIndentifier(feedIdentifier)) {
      if (StringUtils.isEmpty(urlExtend)) {
        throw new GeneralNotTranslatedWithArgumentsException(errorMsgKey, null);
      }
    } else {
      return true;
    }
    return false;
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
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    throw new UnsupportedOperationException("Not supported yet.");
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
