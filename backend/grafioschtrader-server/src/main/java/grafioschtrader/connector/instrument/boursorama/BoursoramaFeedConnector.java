package grafioschtrader.connector.instrument.boursorama;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * A regex check of the URL extension is not active. The connector for checking the instrument always returns an HTTP
 * OK. In such a case, the body of the response is "[]", so there is a special implementation here.
 */
@Component
public class BoursoramaFeedConnector extends BaseFeedConnector {

  private static String BOURSORAMA_ID = "boursorama";

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static List<BoursoramaPeriodEOD> boursoramaPeriods;
  private static String BASE_URL = "https://www.boursorama.com/bourse/action/graph/ws/";

  // Intraday main contain only a empty array when there was no trading. Because
  // of that ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT is enabled
  private static final ObjectMapper objectMapper = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT).build();
  private final Logger log = LoggerFactory.getLogger(this.getClass());

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_HISTORY,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY_URL });
    supportedFeed.put(FeedSupport.FS_INTRA,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY_URL });

    boursoramaPeriods = Arrays.asList(new BoursoramaPeriodEOD(30, 30, 0), new BoursoramaPeriodEOD(90, 90, 0),
        new BoursoramaPeriodEOD(180, 180, 0), new BoursoramaPeriodEOD(365, 365, 0),
        new BoursoramaPeriodEOD(1825, 1825, 0), new BoursoramaPeriodEOD(3650, 3650, 0),
        new BoursoramaPeriodEOD(7300, 7300, 0));
  }

  public BoursoramaFeedConnector() {
    super(supportedFeed, BOURSORAMA_ID, "Boursorama", null, EnumSet.of(UrlCheck.HISTORY, UrlCheck.INTRADAY));
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.CURRENCY_PAIR, AssetclassCategory.NON_INVESTABLE_INDICES,
        AssetclassCategory.EQUITIES, AssetclassCategory.FIXED_INCOME, AssetclassCategory.ETF, AssetclassCategory.MUTUAL_FUND);
  }

  private String getSecurityCurrecnypairHistoricalDownloadLink(final Securitycurrency<?> securitycurrency,
      LocalDate from, boolean useHistoricalExtend) {
    long daysUntilNow = ChronoUnit.DAYS.between(from, LocalDate.now());
    BoursoramaPeriodEOD boursoramaPeriod = boursoramaPeriods.stream().filter(bp -> daysUntilNow <= bp.maxDays)
        .findFirst().orElse(boursoramaPeriods.get(boursoramaPeriods.size() - 1));
    return BASE_URL + "GetTicksEOD?symbol="
        + (useHistoricalExtend ? securitycurrency.getUrlHistoryExtend() : securitycurrency.getUrlIntraExtend())
        + "&length=" + boursoramaPeriod.lengthParam + "&period=" + boursoramaPeriod.period + "&guid=";
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    LocalDate fromLocalDate = LocalDate.now().minusDays(7);
    return getSecurityCurrecnypairHistoricalDownloadLink(security, fromLocalDate, true);
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final LocalDate from, final LocalDate to)
      throws Exception {
    return getEodSecurityCurrencypairHistory(from, to,
        getSecurityCurrecnypairHistoricalDownloadLink(security, from, true),
        FeedConnectorHelper.getGBXLondonDivider(security));
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    LocalDate fromLocalDate = LocalDate.now().minusDays(7);
    return getSecurityCurrecnypairHistoricalDownloadLink(currencypair, fromLocalDate, true);
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final LocalDate from,
      final LocalDate to) throws Exception {
    return getEodSecurityCurrencypairHistory(from, to,
        getSecurityCurrecnypairHistoricalDownloadLink(currencyPair, from, true), 1.0);
  }

  private List<Historyquote> getEodSecurityCurrencypairHistory(final LocalDate from, final LocalDate to, String urlStr,
      double divider) throws Exception {

    long startDay = from.toEpochDay();
    long endDay = to.toEpochDay();

    final List<Historyquote> historyquotes = new ArrayList<>();
    final HeaderEOD header = objectMapper.readValue(FeedConnectorHelper.getByHttpClient(urlStr, 40).body(),
        HeaderEOD.class);
    for (QuoteTabEOD data : header.d.QuoteTab) {
      if (data.d >= startDay && data.d <= endDay) {
        final Historyquote histroyquote = new Historyquote();
        historyquotes.add(histroyquote);
        LocalDate localDate = LocalDate.ofEpochDay(data.d);
        histroyquote.setDate(localDate);
        histroyquote.setClose(data.c / divider);
        histroyquote.setOpen(data.o / divider);
        histroyquote.setLow(data.l / divider);
        histroyquote.setHigh(data.h / divider);
        histroyquote.setVolume(data.v);
      }
    }
    return historyquotes;
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return isYearSecurityForLastPrice(security)
        ? getSecurityCurrecnypairHistoricalDownloadLink(security, LocalDate.now().minusDays(190), false)
        : getUpdateSecurityCurrecnypairLastPriceLink(security);
  }

  private boolean isYearSecurityForLastPrice(Security security) {
    return security.getAssetClass().getCategoryType() == AssetclassType.FIXED_INCOME
        && security.getAssetClass().getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT;
  }

  @Override
  public String getCurrencypairIntradayDownloadLink(final Currencypair currencypair) {
    return getUpdateSecurityCurrecnypairLastPriceLink(currencypair);
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  private String getUpdateSecurityCurrecnypairLastPriceLink(final Securitycurrency<?> securitycurrency) {
    return BASE_URL + "UpdateCharts?symbol=" + securitycurrency.getUrlIntraExtend() + "&period=-1";
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    if (isYearSecurityForLastPrice(security)) {
      getLastPriceFromHistoryQuotes(security, getSecurityIntradayDownloadLink(security));
    } else {
      updateSecuritycurrency(security, getUpdateSecurityCurrecnypairLastPriceLink(security),
          FeedConnectorHelper.getGBXLondonDivider(security));
    }
  }

  @Override
  public void updateCurrencyPairLastPrice(final Currencypair currencypair) throws Exception {
    updateSecuritycurrency(currencypair, getUpdateSecurityCurrecnypairLastPriceLink(currencypair), 1.0);
  }

  private <T extends Securitycurrency<T>> void updateSecuritycurrency(T securitycurrency, String urlStr, double divider)
      throws Exception {
    final HeaderIntra header = objectMapper.readValue(FeedConnectorHelper.getByHttpClient(urlStr, 10).body(),
        HeaderIntra.class);
    if (header != null) {
      header.d[0].setValues(securitycurrency, divider);
    } else {
      securitycurrency.setSChangePercentage(0.0);
    }
    setSTimestamp(securitycurrency);
  }

  private void getLastPriceFromHistoryQuotes(Security security, String urlStr) throws Exception {
    final HeaderEOD header = objectMapper.readValue(new URI(urlStr).toURL().openStream(), HeaderEOD.class);
    QuoteTabEOD lastPrice = header.d.QuoteTab[header.d.QuoteTab.length - 1];
    lastPrice.setValues(security, FeedConnectorHelper.getGBXLondonDivider(security));
    setSTimestamp(security);
  }

  @Override
  protected boolean isConnectionOk(HttpURLConnection huc) {
    try {
      return !"[]".equals(getBodyAsString(huc));
    } catch (IOException e) {
      log.error("Could not open connection", e);
    }
    return true;
  }

  private void setSTimestamp(Securitycurrency<?> securitycurrency) {
    securitycurrency.setSTimestamp(LocalDateTime.now().minusSeconds(getIntradayDelayedSeconds()));
  }

  private static class BoursoramaPeriodEOD {
    // Maximum days this period can handle
    public int maxDays;
    // This number is taken over as a value in the query for the "length" parameter
    public int lengthParam;
    // This value is probably always 0
    public int period;

    public BoursoramaPeriodEOD(int maxDays, int lengthParam, int period) {
      this.maxDays = maxDays;
      this.lengthParam = lengthParam;
      this.period = period;
    }
  }

  private static class HeaderEOD {
    public HeaderInstrumentEOD d;
  }

  private static class HeaderInstrumentEOD {
    // public String Name;
    // public String SymbolId;
    // public int Xperiod;
    public QuoteTabEOD[] QuoteTab;
  }

  private static class QuoteTabEOD extends QuoteTab {
    public int d;

  }

  private static class HeaderIntra {
    public QuoteTabIntra[] d;
  }

  private static class QuoteTabIntra extends QuoteTab {
    public double var;
    // public QtIntra[] qt;

    @Override
    public void setValues(Securitycurrency<?> securitycurrency, double divider) {
      super.setValues(securitycurrency, divider);
      if (o > 0 && var != 0.0) {
        securitycurrency.setSChangePercentage(DataBusinessHelper.roundStandard(var / o * 100));
      } else {
        securitycurrency.setSChangePercentage(0.0);
      }
    }
  }

  private static class QuoteTab {
    public double o;
    public double h;
    public double l;
    public double c;
    public long v;

    public void setValues(Securitycurrency<?> securitycurrency, double divider) {
      securitycurrency.setSLast(c / divider);
      if (o > 0d) {
        securitycurrency.setSOpen(o / divider);
      }
      if (l > 0d) {
        securitycurrency.setSLow(l / divider);
      }
      if (h > 0d) {
        securitycurrency.setSHigh(h / divider);
      }
      if (o > 0d && c > 0d) {
        securitycurrency.setSChangePercentage((c - o) / o * 100);
      }
    }
  }

}
