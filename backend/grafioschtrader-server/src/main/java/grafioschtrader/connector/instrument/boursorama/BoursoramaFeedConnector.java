package grafioschtrader.connector.instrument.boursorama;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.connector.securitysearch.BoursoramaSecuritySearch;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * A regex check of the URL extension is not active.
 *
 * Boursorama's EOD endpoint ({@code .../graph/ws/GetTicksEOD}) is gated behind an anti-scraping check: it only answers
 * requests that carry the {@code X-Requested-With: XMLHttpRequest} header that its own browser JavaScript sends. Plain
 * requests without that header receive HTTP 410 (Gone). Therefore every request issued by this connector sets
 * {@link #XHR_HEADER}, including the URL-connectivity check inherited from {@link BaseFeedConnector} (see the
 * {@link #getRequest(String)} override).
 *
 * <p>
 * The last price comes from {@code UpdateCharts}, which holds the trades of the last session. An instrument not
 * traded in that session - a rarely traded bond, a small share - gets an empty array there; the last price is then
 * the last daily close of {@code GetTicksEOD}. A bond takes its last price from the daily closes in any case. Since
 * {@code UpdateCharts} answers an unknown symbol with the same empty array, the intraday setting is checked against
 * the daily closes as well, which are empty only for an unknown symbol.
 * </p>
 */
@Component
public class BoursoramaFeedConnector extends BaseFeedConnector {

  private static String BOURSORAMA_ID = "boursorama";

  /**
   * Request header required by Boursorama's graph/ws endpoints. Without it the EOD endpoint returns HTTP 410.
   */
  private static final Map<String, String> XHR_HEADER = Map.of("X-Requested-With", "XMLHttpRequest");

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;

  @Autowired(required = false)
  private MicProviderMapRepository micProviderMapRepository;

  /** Market row of the Boursorama symbols per MIC; read once from {@code mic_provider_map}. */
  private volatile Map<String, MicProviderMap> marketByMic;
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
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.CURRENCY_PAIR,
        AssetclassCategory.NON_INVESTABLE_INDICES, AssetclassCategory.EQUITIES, AssetclassCategory.FIXED_INCOME,
        AssetclassCategory.ETF, AssetclassCategory.MUTUAL_FUND);
  }

  /**
   * The Boursorama symbol is a market prefix, the ticker and, for London, the suffix {@code .L}: {@code 1zSAP},
   * {@code 2aNESN}, {@code 1uTTE.L}; a US symbol has no prefix ({@code AAPL}). Master data of the Boursorama search
   * carries it; for a listing another provider found it is built from the exchange ticker and the {@code boursorama}
   * row of the MIC in {@code mic_provider_map}. History and intraday use the same symbol.
   */
  @Override
  public Optional<String> deriveUrlExtension(SecurityBaseDataDto baseData, FeedSupport feedSupport) {
    if (BoursoramaSecuritySearch.PROVIDER_ID.equals(baseData.providerId()) && baseData.providerInstrumentId() != null) {
      return Optional.of(baseData.providerInstrumentId());
    }
    if (baseData.tickerSymbol() == null || baseData.tickerSymbol().isBlank() || baseData.mic() == null) {
      return Optional.empty();
    }
    MicProviderMap row = getMarketByMic().get(baseData.mic());
    if (row == null) {
      return Optional.empty();
    }
    String prefix = BoursoramaSecuritySearch.CODE_US.equals(row.getCodeProvider()) ? "" : row.getCodeProvider();
    return Optional.of(prefix + baseData.tickerSymbol().trim()
        + (row.getSymbolSuffix() == null ? "" : row.getSymbolSuffix()));
  }

  private Map<String, MicProviderMap> getMarketByMic() {
    Map<String, MicProviderMap> marketByMic = this.marketByMic;
    if (marketByMic == null) {
      if (micProviderMapRepository == null) {
        return Map.of();
      }
      marketByMic = new HashMap<>();
      for (MicProviderMap row : micProviderMapRepository
          .findByIdProviderMic_IdProvider(BoursoramaSecuritySearch.PROVIDER_ID)) {
        marketByMic.put(row.getIdProviderMic().getMic(), row);
      }
      this.marketByMic = marketByMic;
    }
    return marketByMic;
  }

  /**
   * Adds the {@code X-Requested-With: XMLHttpRequest} header to the URL-connectivity check inherited from
   * {@link BaseFeedConnector}. Without it Boursorama's EOD endpoint returns HTTP 410 and the check would fail.
   */
  @Override
  protected HttpRequest getRequest(String url) {
    return HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT)
        .header("Accept-Language", "en").header("X-Requested-With", "XMLHttpRequest").GET().build();
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
        FeedConnectorHelper.getMinorUnitDivider(security));
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
    final HeaderEOD header = objectMapper.readValue(FeedConnectorHelper.getByHttpClient(urlStr, 40, XHR_HEADER).body(),
        HeaderEOD.class);
    for (QuoteTabEOD data : header.d.QuoteTab) {
      if (data.d >= startDay && data.d <= endDay) {
        final Historyquote histroyquote = new Historyquote();
        historyquotes.add(histroyquote);
        LocalDate localDate = LocalDate.ofEpochDay(data.d);
        histroyquote.setDate(localDate);
        histroyquote.setClose(data.c / divider);
        // QuoteTab fields are primitives, so JSON keys missing in the response arrive as 0 — only a
        // positive value carries information (same rule as setValues() for the intraday update).
        if (data.o > 0d) {
          histroyquote.setOpen(data.o / divider);
        }
        if (data.l > 0d) {
          histroyquote.setLow(data.l / divider);
        }
        if (data.h > 0d) {
          histroyquote.setHigh(data.h / divider);
        }
        if (data.v > 0) {
          histroyquote.setVolume(data.v);
        }
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
      getLastPriceFromHistoryQuotes(security, getSecurityIntradayDownloadLink(security),
          FeedConnectorHelper.getMinorUnitDivider(security));
    } else {
      updateSecuritycurrency(security, getUpdateSecurityCurrecnypairLastPriceLink(security),
          FeedConnectorHelper.getMinorUnitDivider(security));
    }
  }

  @Override
  public void updateCurrencyPairLastPrice(final Currencypair currencypair) throws Exception {
    updateSecuritycurrency(currencypair, getUpdateSecurityCurrecnypairLastPriceLink(currencypair), 1.0);
  }

  /**
   * Sets the last price from the trades of the last session; without trades it falls back to the last daily close.
   */
  private <T extends Securitycurrency<T>> void updateSecuritycurrency(T securitycurrency, String urlStr, double divider)
      throws Exception {
    final HeaderIntra header = objectMapper
        .readValue(FeedConnectorHelper.getByHttpClient(urlStr, 10, XHR_HEADER).body(), HeaderIntra.class);
    if (header != null && header.d != null && header.d.length > 0) {
      header.d[0].setValues(securitycurrency, divider);
      setSTimestamp(securitycurrency);
    } else {
      getLastPriceFromHistoryQuotes(securitycurrency, getLastCloseLink(securitycurrency), divider);
    }
  }

  /** Daily closes of the last 190 days with the intraday symbol, enough to find the last close of a rare trade. */
  private String getLastCloseLink(Securitycurrency<?> securitycurrency) {
    return getSecurityCurrecnypairHistoricalDownloadLink(securitycurrency, LocalDate.now().minusDays(190), false);
  }

  /**
   * Sets the last price from the last daily close. Without any close in the period the price stays unset and only the
   * change is reset, as for an instrument without trades.
   */
  private void getLastPriceFromHistoryQuotes(Securitycurrency<?> securitycurrency, String urlStr, double divider)
      throws Exception {
    final HeaderEOD header = objectMapper.readValue(FeedConnectorHelper.getByHttpClient(urlStr, 40, XHR_HEADER).body(),
        HeaderEOD.class);
    if (header == null || header.d == null || header.d.QuoteTab == null || header.d.QuoteTab.length == 0) {
      securitycurrency.setSChangePercentage(0.0);
    } else {
      header.d.QuoteTab[header.d.QuoteTab.length - 1].setValues(securitycurrency, divider);
    }
    setSTimestamp(securitycurrency);
  }

  /** Checks the intraday setting against the daily closes, see the class documentation. */
  @Override
  protected String getSecurityIntradayCheckLink(Security security) {
    return getLastCloseLink(security);
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
        securitycurrency.setSChangePercentage(DataBusinessHelper.roundPercentage(var / o * 100));
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
