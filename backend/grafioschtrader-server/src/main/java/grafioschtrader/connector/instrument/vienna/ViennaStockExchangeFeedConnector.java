package grafioschtrader.connector.instrument.vienna;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Historical and intraday price data for the Vienna Stock Exchange is retrieved via the "Profi Chart" JSON endpoint.
 * The disadvantage there is that only figures for the trading days are delivered if they were traded on this day, so a
 * historical gap filler is always required. (The exchange formerly offered a CSV export for equities and indices, but
 * that download has been removed from the website.)
 *
 * The URL extension consists of a number and is therefore subjected to a regex check. The check via the connector
 * obviously always results in an HTTP OK. However, the body of the return contains an error code, which is checked
 * here.
 *
 */
@Component
public class ViennaStockExchangeFeedConnector extends BaseFeedConnector {
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final String DOMAIN_NAME = "https://www.wienerborse.at/";
  private static final String DOMAIN_NAME_WITH_CHART = DOMAIN_NAME + "interactive-chart/canvas/data/";
  private static final String QUERY_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
  private static final ObjectMapper objectMapper = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  private final Logger log = LoggerFactory.getLogger(this.getClass());

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
    supportedFeed.put(FeedSupport.FS_INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }

  public ViennaStockExchangeFeedConnector() {
    super(supportedFeed, "vienna", "Wiener Boerse", "[0-9]*", EnumSet.of(UrlCheck.INTRADAY, UrlCheck.HISTORY));
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.NON_INVESTABLE_INDICES, AssetclassCategory.EQUITIES,
        AssetclassCategory.FIXED_INCOME, AssetclassCategory.ETF, AssetclassCategory.MUTUAL_FUND,
        AssetclassCategory.ISSUER_RISK_PRODUCT);
    parseGeoRestrictions("XVIE");
  }

  @Override
  protected <S extends Securitycurrency<S>> void extendedCheck(Securitycurrency<S> securitycurrency,
      FeedSupport feedSupport, String urlExtend, String errorMsgKey, FeedIdentifier feedIdentifier,
      SpecialInvestmentInstruments specialInvestmentInstruments, AssetclassType assetclassType) {
    if (((Security) securitycurrency).getIsin().isBlank()) {
      throw new GeneralNotTranslatedWithArgumentsException("gt.connector.vienna.setting.failure", null);
    }
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern(QUERY_DATE_FORMAT);
    LocalDateTime now = LocalDateTime.now().minusHours(1);
    LocalDateTime yesterday = now.minusDays(1);
    return DOMAIN_NAME_WITH_CHART + "?DATETIME_TZ_START_RANGE=" + dtf.format(yesterday) + "&DATETIME_TZ_END_RANGE="
        + dtf.format(now) + "&ID_NOTATION=" + security.getUrlIntraExtend()
        + "&useExchanges=false&PRICE_TYPE=LAST&ID_SECURITY_TYPE=&GRANULARITY=60m&WITH_EARNINGS=0&INSTRUMENT_DATA_NEEDED="
        + "&ID_QUALITY_PRICE=2&MAX_HISTORY_YEARS=33&reqType=TSStimeSeries";
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    String url = getSecurityIntradayDownloadLink(security);
    final FullChartData fcd = getChartResponse(url);
    security.setSTimestamp(Instant.ofEpochMilli(fcd.currentPrice.DATETIME_PRICE)
        .atZone(ZoneId.systemDefault()).toLocalDateTime());
    security.setSOpen(fcd.currentPrice.FIRST);
    security.setSHigh(fcd.currentPrice.HIGH);
    security.setSLow(fcd.currentPrice.LOW);
    security.setSLast(fcd.currentPrice.LAST);
    // We do not have the information to calculate the percentage change, somewhere
    // else should that happened
    security.setSChangePercentage(null);
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 60;
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    LocalDate now = LocalDate.now();
    return getSecurityHistoricalDownloadLink(security, now.minusDays(10), now);
  }

  private String getSecurityHistoricalDownloadLink(final Security security, LocalDate from, LocalDate to) {
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern(QUERY_DATE_FORMAT);
    return DOMAIN_NAME_WITH_CHART + "?DATETIME_TZ_START_RANGE=" + dtf.format(from.atStartOfDay())
        + "&DATETIME_TZ_END_RANGE=" + dtf.format(to.atStartOfDay()) + "&ID_NOTATION=" + security.getUrlHistoryExtend()
        + "&useExchanges=false&PRICE_TYPE=LAST&ID_SECURITY_TYPE=&GRANULARITY=1D&WITH_EARNINGS=0&INSTRUMENT_DATA_NEEDED="
        + "&ID_QUALITY_PRICE=2&MAX_HISTORY_YEARS=33&reqType=TSStimeSeries";
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final LocalDate from, final LocalDate to)
      throws Exception {
    return getEodSecurityHistoryFromChart(security, from, to);
  }

  @Override
  public boolean needHistoricalGapFiller(final Security security) {
    // The chart endpoint only delivers quotes for days that were actually traded.
    return true;
  }

  @Override
  public EnumSet<DownloadLink> isDownloadLinkCreatedLazy() {
    return EnumSet.of(DownloadLink.DL_LAZY_HISTORY);
  }

  @Override
  protected boolean isConnectionOk(HttpURLConnection huc) {
    try {
      return getBodyAsString(huc).indexOf("\"errorCode\":\"-1\"") == -1;
    } catch (IOException e) {
      log.error("Could not open connection", e);
    }
    return true;
  }

  private List<Historyquote> getEodSecurityHistoryFromChart(final Security security, final LocalDate from,
      final LocalDate to) throws Exception {
    String url = getSecurityHistoricalDownloadLink(security, from, to.plusDays(1));
    final FullChartData fcd = getChartResponse(url);
    final List<Historyquote> historyquotes = new ArrayList<>();
    for (Quote quote : fcd.data) {
      // There is minimal data for two years, if the security can also be traded for
      // two years.
      LocalDate date = Instant.ofEpochMilli(quote.DATETIME_LAST).atZone(ZoneId.systemDefault()).toLocalDate();
      if (!date.isBefore(from) && !date.isAfter(to)) {
        final Historyquote historyquote = new Historyquote();
        historyquotes.add(historyquote);

        historyquote.setDate(date);
        historyquote.setClose(quote.LAST);
        historyquote.setOpen(quote.FIRST);
        historyquote.setHigh(quote.HIGH);
        historyquote.setLow(quote.LOW);
        if (quote.TOTAL_VOLUME != null && quote.TOTAL_VOLUME % 1 == 0) {
          historyquote.setVolume(quote.TOTAL_VOLUME.longValue());
        }
      }
    }
    return historyquotes;
  }

  private FullChartData getChartResponse(String urlStr) throws IOException, InterruptedException {
    return objectMapper.readValue(FeedConnectorHelper.getByHttpClient(urlStr).body(), FullChartData.class);
  }

  private static class FullChartData {
    public Quote[] data;
    public CurrentPrice currentPrice;
  }

  private static class Quote extends Ohlc {
    public long DATETIME_LAST;
    public Double TOTAL_VOLUME;
  }

  private static class CurrentPrice extends Ohlc {
    public long DATETIME_PRICE;
  }

  private static class Ohlc {
    public double FIRST;
    public double HIGH;
    public double LOW;
    public double LAST;
  }
}
