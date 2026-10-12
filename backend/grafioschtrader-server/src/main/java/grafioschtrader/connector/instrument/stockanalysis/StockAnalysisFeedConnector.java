package grafioschtrader.connector.instrument.stockanalysis;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.MicProviderMapRepository;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Price connector for stockanalysis.com, which serves shares and ETFs of nearly every exchange GT knows with the full
 * daily history back to the listing (Apple from 1982, Nestlé and SAP from 1992) and no key.
 *
 * <p>
 * The URL extension is the instrument path of the site: {@code s/{ticker}} for a US listing (shares and ETFs alike,
 * {@code e/} is accepted as well) and {@code a/{EXCHANGE}-{TICKER}} elsewhere, e.g. {@code a/SWX-NESN},
 * {@code a/ETR-SAP}, {@code a/FRA-ZAL}. Case does not matter; share classes are written with a dot ({@code BRK.B},
 * {@code NOVO.B}). The {@code stockanalysis} rows of {@code mic_provider_map} hold the exchange code per MIC, with
 * {@code US} for the US exchanges, so that the security lookup derives the path from the ticker another provider
 * found.
 * </p>
 *
 * <p>
 * History comes from {@code api/charts/{path}/MAX/c?chartiq=true}, split-adjusted but not adjusted for dividends,
 * the last price from {@code api/quotes/{path}}. The site answers plain requests with a browser check, so every
 * request sends the {@code Accept} and {@code Referer} headers of its own page.
 * </p>
 */
@Component
public class StockAnalysisFeedConnector extends BaseFeedConnector {

  public static final String STOCK_ANALYSIS_ID = "stockanalysis";
  /** Code of the US exchanges in {@code mic_provider_map}; their path has no exchange part. */
  public static final String CODE_US = "US";

  private static final String DOMAIN_NAME = "https://stockanalysis.com/";
  private static final String API = DOMAIN_NAME + "api/";
  /** Exchanges with a {@code mic_provider_map} row, which are the coverage of the connector. */
  private static final String SUPPORTED_MICS = "XNAS XNYS XSWX XETR XFRA XPAR XAMS XBRU XLIS XMIL XMAD XVIE XLON XMSM "
      + "XTSX XASX XCSE XSTO XOSL XWAR XHKG XTKS XSHG XSHE XKRX XIDX BVMF XNZE XCAI";
  private static final Map<String, String> HEADERS = Map.of("Accept", "application/json, text/plain, */*", "Referer",
      DOMAIN_NAME);
  private static final ObjectMapper objectMapper = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  private static final Map<FeedSupport, FeedIdentifier[]> supportedFeed = new HashMap<>();

  static {
    supportedFeed.put(FeedSupport.FS_HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
    supportedFeed.put(FeedSupport.FS_INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }

  @Autowired(required = false)
  private MicProviderMapRepository micProviderMapRepository;

  /** Exchange code per MIC; read once from {@code mic_provider_map}. */
  private volatile Map<String, String> exchangeCodeByMic;

  public StockAnalysisFeedConnector() {
    super(supportedFeed, STOCK_ANALYSIS_ID, "Stock Analysis", "^[aAeEsS]/[A-Za-z0-9.-]+$",
        EnumSet.of(UrlCheck.HISTORY));
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.EQUITIES, AssetclassCategory.ETF);
    parseGeoRestrictions(SUPPORTED_MICS);
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    return API + "charts/" + security.getUrlHistoryExtend() + "/MAX/c?chartiq=true";
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return API + "quotes/" + security.getUrlIntraExtend();
  }

  /** The URL check of a setting is sent with the headers of the site, like every other request. */
  @Override
  protected HttpRequest getRequest(String url) {
    HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url))
        .header("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT);
    HEADERS.forEach(builder::header);
    return builder.GET().build();
  }

  @Override
  public boolean needHistoricalGapFiller(final Security security) {
    return true;
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  /**
   * Reads the whole daily history - the site offers no other range - and keeps the days from {@code from} to
   * {@code to}.
   */
  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final LocalDate from, final LocalDate to)
      throws Exception {
    String body = FeedConnectorHelper.getByHttpClient(getSecurityHistoricalDownloadLink(security), 30, HEADERS).body();
    return parseHistory(body, from, to, FeedConnectorHelper.getMinorUnitDivider(security));
  }

  /**
   * Converts the chart answer {@code {"data":[{"t":"2026-10-09","o":…,"h":…,"l":…,"c":…,"v":…}]}}.
   *
   * @param body    the JSON answer
   * @param from    first day to keep
   * @param to      last day to keep
   * @param divider divider of a quote in a minor unit, e.g. 100 for GBX
   * @return the daily quotes in the range, oldest first
   */
  static List<Historyquote> parseHistory(String body, LocalDate from, LocalDate to, double divider) {
    List<Historyquote> historyquotes = new ArrayList<>();
    for (JsonNode day : objectMapper.readTree(body).path("data")) {
      LocalDate date = LocalDate.parse(day.path("t").asString().substring(0, 10));
      if (date.isBefore(from) || date.isAfter(to) || !day.path("c").isNumber()) {
        continue;
      }
      Historyquote historyquote = new Historyquote();
      historyquote.setDate(date);
      historyquote.setClose(day.path("c").asDouble() / divider);
      if (day.path("o").isNumber()) {
        historyquote.setOpen(day.path("o").asDouble() / divider);
      }
      if (day.path("h").isNumber()) {
        historyquote.setHigh(day.path("h").asDouble() / divider);
      }
      if (day.path("l").isNumber()) {
        historyquote.setLow(day.path("l").asDouble() / divider);
      }
      if (day.path("v").isNumber()) {
        historyquote.setVolume(day.path("v").asLong());
      }
      historyquotes.add(historyquote);
    }
    return historyquotes;
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    String body = FeedConnectorHelper.getByHttpClient(getSecurityIntradayDownloadLink(security), 15, HEADERS).body();
    setLastPrice(security, body, FeedConnectorHelper.getMinorUnitDivider(security));
  }

  /**
   * Sets the last price from the quote answer: {@code p} last price, {@code o}, {@code h}, {@code l} of the day,
   * {@code v} volume, {@code cl} previous close, {@code cp} change in percent and {@code ts} time of the last price in
   * milliseconds.
   *
   * @param security the security to update
   * @param body     the JSON answer
   * @param divider  divider of a quote in a minor unit
   */
  static void setLastPrice(Security security, String body, double divider) {
    JsonNode quote = objectMapper.readTree(body).path("data");
    if (!quote.path("p").isNumber()) {
      return;
    }
    security.setSLast(quote.path("p").asDouble() / divider);
    if (quote.path("o").isNumber()) {
      security.setSOpen(quote.path("o").asDouble() / divider);
    }
    if (quote.path("h").isNumber()) {
      security.setSHigh(quote.path("h").asDouble() / divider);
    }
    if (quote.path("l").isNumber()) {
      security.setSLow(quote.path("l").asDouble() / divider);
    }
    if (quote.path("v").isNumber()) {
      security.setSVolume(quote.path("v").asLong());
    }
    if (quote.path("cl").isNumber()) {
      security.setSPrevClose(quote.path("cl").asDouble() / divider);
    }
    if (quote.path("cp").isNumber()) {
      security.setSChangePercentage(quote.path("cp").asDouble());
    }
    security.setSTimestamp(quote.path("ts").isNumber()
        ? LocalDateTime.ofInstant(Instant.ofEpochMilli(quote.path("ts").asLong()), ZoneId.systemDefault())
        : LocalDateTime.now());
  }

  /**
   * Builds the path from the exchange ticker and the exchange code of the MIC: {@code s/{ticker}} on a US exchange,
   * otherwise {@code a/{CODE}-{TICKER}}. A dash or blank of a share class becomes the dot the site uses
   * ({@code BRK-B} → {@code BRK.B}). History and intraday use the same path.
   */
  @Override
  public Optional<String> deriveUrlExtension(SecurityBaseDataDto baseData, FeedSupport feedSupport) {
    if (baseData.tickerSymbol() == null || baseData.tickerSymbol().isBlank() || baseData.mic() == null) {
      return Optional.empty();
    }
    String code = getExchangeCodeByMic().get(baseData.mic());
    if (code == null) {
      return Optional.empty();
    }
    String ticker = baseData.tickerSymbol().trim().replace('-', '.').replace(' ', '.');
    return Optional.of(CODE_US.equals(code) ? "s/" + ticker : "a/" + code + "-" + ticker);
  }

  private Map<String, String> getExchangeCodeByMic() {
    Map<String, String> codeByMic = exchangeCodeByMic;
    if (codeByMic == null) {
      if (micProviderMapRepository == null) {
        return Map.of();
      }
      codeByMic = new HashMap<>();
      for (MicProviderMap row : micProviderMapRepository.findByIdProviderMic_IdProvider(STOCK_ANALYSIS_ID)) {
        codeByMic.put(row.getIdProviderMic().getMic(), row.getCodeProvider());
      }
      exchangeCodeByMic = codeByMic;
    }
    return codeByMic;
  }
}
