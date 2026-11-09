package grafioschtrader.instrument.lookup;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.DividendHistoryResult;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.dto.ConnectorAlternativeDto;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.BaseHistoryquote;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.ConnectorProbeStatus;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Tries a connector with a derived URL extension for an instrument that does not exist in GT yet. The trial runs on a
 * transient security that is built for this single probe and never saved. It first applies the same URL extension check
 * as saving a security does, then requests a small amount of data: the closing prices of the last
 * {@value #HISTORY_DAYS} days, the last price, the dividends since {@link DistributionFrequencyDeriver#historyStart} or
 * the splits of the last {@value #SPLIT_YEARS} years. From the dividends it derives the distribution frequency.
 *
 * <p>
 * A probe never throws. Its detail text is passed through {@link BaseFeedConnector#hideApiKeyForError(String)} where
 * the connector offers it, so that no API key reaches the user.
 * </p>
 */
@Component
public class ConnectorProbe {

  /** Closing prices of a rarely traded bond may be days apart, so the window is wider than a week. */
  static final int HISTORY_DAYS = 30;
  static final int SPLIT_YEARS = 10;
  private static final int MAX_DETAIL_LENGTH = 200;
  /** Last active day of a transient security whose master data names none. */
  private static final LocalDate OPEN_END = LocalDate.of(2099, 12, 31);

  private static final Logger log = LoggerFactory.getLogger(ConnectorProbe.class);

  /**
   * What a probe needs to know about the listing besides the transient security.
   *
   * @param listedSince  first trading day of the listing, null when unknown
   * @param distributing true for a distributing fund, false for an accumulating one, null when unknown
   * @param today        reference day of the requested period
   */
  public record ProbeContext(LocalDate listedSince, Boolean distributing, LocalDate today) {
  }

  /**
   * Builds the transient security a probe runs on. Every probe gets its own instance, because a probe sets the URL
   * extension and, for the intraday feed, the last price.
   *
   * @param baseData      master data of the listing
   * @param stockexchange exchange of the listing; when null, a transient one with the MIC of the master data is used
   * @param categoryType  derived asset class type; when unknown the checks treat the instrument as a share
   * @param sii           derived financial instrument; when unknown the checks treat the instrument as a share
   * @return a security that is never persisted
   */
  public static Security draftSecurity(SecurityBaseDataDto baseData, Stockexchange stockexchange,
      AssetclassType categoryType, SpecialInvestmentInstruments sii) {
    Assetclass assetclass = new Assetclass();
    assetclass.setCategoryType(categoryType == null ? AssetclassType.EQUITIES : categoryType);
    assetclass.setSpecialInvestmentInstrument(sii == null ? SpecialInvestmentInstruments.DIRECT_INVESTMENT : sii);
    Security security = new Security();
    security.setName(baseData.name());
    security.setIsin(baseData.isin());
    security.setCurrency(baseData.currency());
    security.setTickerSymbol(baseData.tickerSymbol());
    // URL checks of connectors such as Xetra test whether the security is still active
    security.setActiveFromDate(baseData.activeFromDate() != null ? baseData.activeFromDate()
        : LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY));
    security.setActiveToDate(baseData.activeToDate() != null ? baseData.activeToDate() : OPEN_END);
    security.setAssetClass(assetclass);
    if (stockexchange == null && baseData.mic() != null) {
      // Connectors such as Yahoo read the MIC of the exchange; a transient one suffices for the probe.
      stockexchange = new Stockexchange();
      stockexchange.setMic(baseData.mic());
    }
    security.setStockexchange(stockexchange);
    return security;
  }

  /**
   * Probes one connector for one feed.
   *
   * @param connector    the connector
   * @param feedSupport  the feed
   * @param draft        transient security from {@link #draftSecurity}; it is modified
   * @param urlExtension URL extension derived by the connector; null when the connector needs none for the feed
   * @param context      listing facts for the distribution frequency and the reference day
   * @return the connector with its URL extension and the outcome
   */
  public ConnectorAlternativeDto probe(IFeedConnector connector, FeedSupport feedSupport, Security draft,
      String urlExtension, ProbeContext context) {
    LocalDate today = context.today();
    setUrlExtension(draft, feedSupport, urlExtension);
    ConnectorAlternativeDto checkFailure = checkUrlExtension(connector, feedSupport, draft, urlExtension);
    if (checkFailure != null) {
      return checkFailure;
    }
    try {
      return switch (feedSupport) {
      case FS_HISTORY -> probeHistory(connector, draft, urlExtension, today);
      case FS_INTRA -> probeIntraday(connector, draft, urlExtension);
      case FS_DIVIDEND -> probeDividend(connector, draft, urlExtension, context);
      case FS_SPLIT -> probeSplit(connector, draft, urlExtension, today);
      };
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return failed(connector, urlExtension, ConnectorProbeStatus.REQUEST_FAILED, "Interrupted");
    } catch (Exception e) {
      log.debug("Probe of {} for {} failed: {}", connector.getID(), feedSupport, e.toString());
      return failed(connector, urlExtension, ConnectorProbeStatus.REQUEST_FAILED, describe(connector, e));
    }
  }

  /**
   * Builds the transient currency pair a probe runs on. Every probe gets its own instance, because a probe sets the URL
   * extension and, for the intraday feed, the last price.
   *
   * @param fromCurrency base currency of the pair
   * @param toCurrency   quote currency of the pair
   * @return a currency pair that is never persisted
   */
  public static Currencypair draftCurrencypair(String fromCurrency, String toCurrency) {
    return new Currencypair(fromCurrency, toCurrency);
  }

  /**
   * Probes one connector for one feed of a currency pair. The history probe requests the whole period GT loads for a
   * new currency pair, from {@link GlobalConstants#OLDEST_TRADING_DAY} on, through the same call as that first load;
   * the oldest closing price it delivers tells the user how far back the connector reaches. The intraday probe requests
   * the last price.
   *
   * @param connector    the connector
   * @param feedSupport  the feed, {@link FeedSupport#FS_HISTORY} or {@link FeedSupport#FS_INTRA}
   * @param draft        transient currency pair from {@link #draftCurrencypair}; it is modified
   * @param urlExtension URL extension derived by the connector; null when the connector needs none for the feed
   * @param today        reference day, the end of the requested history
   * @return the connector with its URL extension and the outcome
   */
  public ConnectorAlternativeDto probeCurrencypair(IFeedConnector connector, FeedSupport feedSupport,
      Currencypair draft, String urlExtension, LocalDate today) {
    setUrlExtension(draft, feedSupport, urlExtension);
    ConnectorAlternativeDto checkFailure = checkUrlExtension(connector, feedSupport, draft, urlExtension);
    if (checkFailure != null) {
      return checkFailure;
    }
    try {
      return feedSupport == FeedSupport.FS_HISTORY ? probeCurrencypairHistory(connector, draft, urlExtension, today)
          : probeCurrencypairIntraday(connector, draft, urlExtension);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return failed(connector, urlExtension, ConnectorProbeStatus.REQUEST_FAILED, "Interrupted");
    } catch (Exception e) {
      log.debug("Probe of {} for {} of {} failed: {}", connector.getID(), feedSupport, draft.getName(), e.toString());
      return failed(connector, urlExtension, ConnectorProbeStatus.REQUEST_FAILED, describe(connector, e));
    }
  }

  /**
   * Tells whether a history connector delivers closing prices from a given day on, e.g. from the first trading day of
   * the successor of an ISIN change. It requests the {@value #HISTORY_DAYS} days starting on that day, cut off at the
   * reference day. Unlike {@link #probe} it never throws and reports a failed request as false.
   *
   * @param connector    the connector
   * @param draft        transient security from {@link #draftSecurity}; its history URL extension is set
   * @param urlExtension URL extension derived by the connector; null when the connector needs none
   * @param from         first day the prices are required for
   * @param today        reference day, the end of the requested period at the latest
   * @return true when at least one closing price lies in the requested period
   */
  public boolean deliversHistoryFrom(IFeedConnector connector, Security draft, String urlExtension, LocalDate from,
      LocalDate today) {
    setUrlExtension(draft, FeedSupport.FS_HISTORY, urlExtension);
    LocalDate to = from.plusDays(HISTORY_DAYS).isAfter(today) ? today : from.plusDays(HISTORY_DAYS);
    try {
      List<Historyquote> quotes = connector.getEodSecurityHistory(draft, from, to);
      return quotes != null && !quotes.isEmpty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (Exception e) {
      log.debug("History of {} from {} failed: {}", connector.getID(), from, e.toString());
      return false;
    }
  }

  /**
   * Creates the result of a probe that did not deliver data.
   *
   * @param connector    the connector
   * @param urlExtension the URL extension that was tried
   * @param status       the failure status
   * @param detail       short explanation for the user
   * @return the alternative without sample values
   */
  static ConnectorAlternativeDto failed(IFeedConnector connector, String urlExtension, ConnectorProbeStatus status,
      String detail) {
    return new ConnectorAlternativeDto(connector.getID(), connector.getReadableName(), urlExtension, status, null, null,
        null, null, null, truncate(detail));
  }

  /**
   * Applies the same URL extension check as saving an instrument does.
   *
   * @return the failed alternative, or null when the check passed
   */
  private static <S extends Securitycurrency<S>> ConnectorAlternativeDto checkUrlExtension(IFeedConnector connector,
      FeedSupport feedSupport, Securitycurrency<S> draft, String urlExtension) {
    try {
      connector.checkAndClearSecuritycurrencyUrlExtend(draft, feedSupport);
      return null;
    } catch (GeneralNotTranslatedWithArgumentsException e) {
      ConnectorProbeStatus status = BaseFeedConnector.REGEX_URL_MESSAGE_KEY.equals(e.getMessageKey())
          ? ConnectorProbeStatus.REGEX_FAILED
          : ConnectorProbeStatus.CHECK_FAILED;
      return failed(connector, urlExtension, status, describeCheckFailure(connector, e));
    } catch (Exception e) {
      return failed(connector, urlExtension, ConnectorProbeStatus.REQUEST_FAILED, describe(connector, e));
    }
  }

  private ConnectorAlternativeDto probeCurrencypairHistory(IFeedConnector connector, Currencypair draft,
      String urlExtension, LocalDate today) throws Exception {
    List<Historyquote> quotes = connector.getEodCurrencyHistory(draft,
        LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY), today);
    if (quotes == null || quotes.isEmpty()) {
      return empty(connector, urlExtension);
    }
    Historyquote first = quotes.stream().min(Comparator.comparing(BaseHistoryquote::getDate)).get();
    Historyquote last = quotes.stream().max(Comparator.comparing(BaseHistoryquote::getDate)).get();
    return new ConnectorAlternativeDto(connector.getID(), connector.getReadableName(), urlExtension,
        ConnectorProbeStatus.OK, last.getDate(), last.getClose(), quotes.size(), first.getDate(), null, null);
  }

  private ConnectorAlternativeDto probeCurrencypairIntraday(IFeedConnector connector, Currencypair draft,
      String urlExtension) throws Exception {
    connector.updateCurrencyPairLastPrice(draft);
    return draft.getSLast() == null ? empty(connector, urlExtension)
        : ok(connector, urlExtension, draft.getSTimestamp() == null ? null : draft.getSTimestamp().toLocalDate(),
            draft.getSLast(), 1);
  }

  private ConnectorAlternativeDto probeHistory(IFeedConnector connector, Security draft, String urlExtension,
      LocalDate today) throws Exception {
    List<Historyquote> quotes = connector.getEodSecurityHistory(draft, today.minusDays(HISTORY_DAYS), today);
    return quotes == null || quotes.isEmpty() ? empty(connector, urlExtension)
        : quotes.stream().max(Comparator.comparing(BaseHistoryquote::getDate))
            .map(q -> ok(connector, urlExtension, q.getDate(), q.getClose(), quotes.size())).get();
  }

  private ConnectorAlternativeDto probeIntraday(IFeedConnector connector, Security draft, String urlExtension)
      throws Exception {
    connector.updateSecurityLastPrice(draft);
    return draft.getSLast() == null ? empty(connector, urlExtension)
        : ok(connector, urlExtension, draft.getSTimestamp() == null ? null : draft.getSTimestamp().toLocalDate(),
            draft.getSLast(), 1);
  }

  /**
   * Requests the dividends and determines the distribution frequency: the one stated by the provider, otherwise derived
   * from the ex-dates. Yahoo delivers only the split-adjusted amount, so the sample falls back to it.
   */
  private ConnectorAlternativeDto probeDividend(IFeedConnector connector, Security draft, String urlExtension,
      ProbeContext context) throws Exception {
    DividendHistoryResult history = connector.getDividendHistoryWithFrequency(draft,
        DistributionFrequencyDeriver.historyStart(context.today()));
    List<Dividend> dividends = history.dividends().stream().filter(d -> d.getExDate() != null).toList();
    DistributionFrequency frequency = DistributionFrequencyDeriver.combine(history.providerFrequency(),
        dividends.stream().map(Dividend::getExDate).toList(), context.listedSince(), context.distributing(),
        context.today());
    if (dividends.isEmpty()) {
      return new ConnectorAlternativeDto(connector.getID(), connector.getReadableName(), urlExtension,
          ConnectorProbeStatus.NO_DATA, null, null, 0, null, frequency, null);
    }
    Dividend last = dividends.stream().max(Comparator.comparing(Dividend::getExDate)).get();
    return new ConnectorAlternativeDto(connector.getID(), connector.getReadableName(), urlExtension,
        ConnectorProbeStatus.OK, last.getExDate(),
        last.getAmount() != null ? last.getAmount() : last.getAmountAdjusted(), dividends.size(), null, frequency,
        null);
  }

  private ConnectorAlternativeDto probeSplit(IFeedConnector connector, Security draft, String urlExtension,
      LocalDate today) throws Exception {
    List<Securitysplit> splits = connector.getSplitHistory(draft, today.minusYears(SPLIT_YEARS), today);
    return splits == null || splits.isEmpty() ? empty(connector, urlExtension)
        : splits.stream().max(Comparator.comparing(Securitysplit::getSplitDate))
            .map(s -> ok(connector, urlExtension, s.getSplitDate(), null, splits.size())).get();
  }

  private static void setUrlExtension(Security draft, FeedSupport feedSupport, String urlExtension) {
    switch (feedSupport) {
    case FS_HISTORY -> draft.setUrlHistoryExtend(urlExtension);
    case FS_INTRA -> draft.setUrlIntraExtend(urlExtension);
    case FS_DIVIDEND -> draft.setUrlDividendExtend(urlExtension);
    case FS_SPLIT -> draft.setUrlSplitExtend(urlExtension);
    }
  }

  private static void setUrlExtension(Currencypair draft, FeedSupport feedSupport, String urlExtension) {
    if (feedSupport == FeedSupport.FS_HISTORY) {
      draft.setUrlHistoryExtend(urlExtension);
    } else {
      draft.setUrlIntraExtend(urlExtension);
    }
  }

  private static ConnectorAlternativeDto ok(IFeedConnector connector, String urlExtension, LocalDate date, Double value,
      int count) {
    return new ConnectorAlternativeDto(connector.getID(), connector.getReadableName(), urlExtension,
        ConnectorProbeStatus.OK, date, value, count, null, null, null);
  }

  private static ConnectorAlternativeDto empty(IFeedConnector connector, String urlExtension) {
    return new ConnectorAlternativeDto(connector.getID(), connector.getReadableName(), urlExtension,
        ConnectorProbeStatus.NO_DATA, null, null, 0, null, null, null);
  }

  /**
   * The check exception carries an untranslated message key and its arguments, e.g. the URL that failed; without this
   * the user would see no reason at all.
   */
  private static String describeCheckFailure(IFeedConnector connector, GeneralNotTranslatedWithArgumentsException e) {
    String detail = e.getMessageKey()
        + (e.getArguments() == null || e.getArguments().length == 0 ? "" : " " + Arrays.toString(e.getArguments()));
    return connector instanceof BaseFeedConnector baseFeedConnector ? baseFeedConnector.hideApiKeyForError(detail)
        : detail;
  }

  private static String describe(IFeedConnector connector, Exception e) {
    String detail = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
    return connector instanceof BaseFeedConnector baseFeedConnector ? baseFeedConnector.hideApiKeyForError(detail)
        : detail;
  }

  private static String truncate(String detail) {
    return detail == null || detail.length() <= MAX_DETAIL_LENGTH ? detail : detail.substring(0, MAX_DETAIL_LENGTH);
  }
}
