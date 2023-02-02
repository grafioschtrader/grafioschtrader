package grafioschtrader.connector.instrument.vienna;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * The Vienna Stock Exchange provides for different asset classes the download
 * of a CSV file of the historical price data. Unfortunately, for some asset
 * classes a rounding to integers is performed for the CSV export. Another
 * access to more precise figures is available via the "Profi Chart". The
 * disadvantage there is that only figures for the trading days are delivered if
 * they were traded on this day.
 *
 */
@Component
public class ViennaStockExchangeFeedConnector extends BaseFeedConnector {
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final String DOMAIN_NAME = "https://www.wienerborse.at/";
  private static final String DOMAIN_NAME_WITH_CHART = DOMAIN_NAME + "interactive-chart/canvas/data/";
  private static final String QUERY_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
  private static final String QUERY_CSV_DATE_FORMAT = "MM/dd/yyyy";
  private static final ObjectMapper objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
    supportedFeed.put(FeedSupport.INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }

  public ViennaStockExchangeFeedConnector() {
    super(supportedFeed, "vienna", "Wiener Boerse", "[0-9]*");
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
    security.setSTimestamp(fcd.currentPrice.DATETIME_PRICE);
    security.setSOpen(fcd.currentPrice.FIRST);
    security.setSHigh(fcd.currentPrice.HIGH);
    security.setSLow(fcd.currentPrice.LOW);
    security.setSLast(fcd.currentPrice.LAST);
    // We do not have the information to calculate the percentage change, somewhere
    // else should that happened
    security.setSChangePercentage(null);
  }

  public int getIntradayDelayedSeconds() {
    return 60;
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    return getSecurityHistoricalDownloadLink(security, DateHelper.setTimeToZeroAndAddDay(new Date(), -10),
        DateHelper.setTimeToZeroAndAddDay(new Date(), 0));
  }

  private String getSecurityHistoricalDownloadLink(final Security security, Date from, Date to) {
    if (useCSVEOD(security)) {
      SimpleDateFormat sdf = new SimpleDateFormat(QUERY_CSV_DATE_FORMAT);
      try {
        return createUrlForHistoricalData(security, from, to, sdf);
      } catch (Exception e) {
        return null;
      }
    } else {
      SimpleDateFormat sdf = new SimpleDateFormat(QUERY_DATE_FORMAT);
      return DOMAIN_NAME_WITH_CHART + "?DATETIME_TZ_START_RANGE=" + sdf.format(from) + "&DATETIME_TZ_END_RANGE="
          + sdf.format(to) + "&ID_NOTATION=" + security.getUrlHistoryExtend()
          + "&useExchanges=false&PRICE_TYPE=LAST&ID_SECURITY_TYPE=&GRANULARITY=1D&WITH_EARNINGS=0&INSTRUMENT_DATA_NEEDED="
          + "&ID_QUALITY_PRICE=2&MAX_HISTORY_YEARS=33&reqType=TSStimeSeries";
    }
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    return useCSVEOD(security) ? getEodSecurityHistoryFromHistoricalCSV(security, from, to)
        : getEodSecurityHistoryFromChart(security, from, to);

  }

  public boolean needHistoricalGapFiller(final Security security) {
    return useCSVEOD(security)? false: true;
  }
  
  private boolean useCSVEOD(Security security) {
    return security.getAssetClass().getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT
        && security.getAssetClass().getCategoryType() == AssetclassType.EQUITIES
        || security.getAssetClass()
            .getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.NON_INVESTABLE_INDICES;
  }

  /**
   * Currently, the CSV download should be used only for stocks and indices. For
   * other asset classes the price data is too inaccurately rounded.
   */
  private List<Historyquote> getEodSecurityHistoryFromHistoricalCSV(final Security security, final Date from,
      final Date to) throws Exception {

    SimpleDateFormat sdf = new SimpleDateFormat(QUERY_CSV_DATE_FORMAT);
    String csvUrl = createUrlForHistoricalData(security, from, to, sdf);

    final List<Historyquote> historyquotes = new ArrayList<>();

    URLConnection connection = new URL(csvUrl).openConnection();
    try (InputStreamReader inputStream = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
        BufferedReader bufferedReader = new BufferedReader(inputStream)) {

      String inputLine;
      int columns = 0;
      while ((inputLine = bufferedReader.readLine()) != null) {
        inputLine = inputLine.replace("\"", "");
        if (inputLine.trim().length() == 0 || columns == 0) {
          // First line throw away
          columns = inputLine.split(";").length;
          continue;
        }
        final String[] items = inputLine.split(";");
        historyquotes.add(parseResponseLineItems(items, sdf));
      }
    }
    return historyquotes;
  }

  /**
   * Index: Date;Open;High;Low;Last Close;Chg.%;</br>
   * Bond: Date;Open;High;Low;LastClose;Chg.%;Total Value<sup>1</sup>;Total
   * Volume<sup>1</sup>;</br>
   * Stock: Date;Open;High;Low;Last Close;Chg.%;Total Value<sup>1</sup>;Total
   * Volume<sup>1</sup>;</br>
   */
  private Historyquote parseResponseLineItems(final String[] items, SimpleDateFormat sdf) throws ParseException {
    Historyquote historyquote = new Historyquote();
    historyquote.setDate(sdf.parse(items[0]));
    historyquote.setOpen(Double.parseDouble(items[1]));
    historyquote.setHigh(Double.parseDouble(items[2]));
    historyquote.setLow(Double.parseDouble(items[3]));
    historyquote.setClose(Double.parseDouble(items[4]));
    if (items.length == 8) {
      historyquote.setVolume(Long.parseLong(items[7].replace(",", "")));
    }
    return historyquote;
  }

  private String createUrlForHistoricalData(final Security security, final Date from, final Date to,
      SimpleDateFormat sdf) throws Exception {
    String urlPrefix = "market-data/";
    // Currently only shares and indices pass thru here
    switch (security.getAssetClass().getSpecialInvestmentInstrument()) {
    case ETF:
      urlPrefix += "exchange-traded-funds";
      break;
    case NON_INVESTABLE_INDICES:
      urlPrefix = "indices/index-values";
      break;
    case DIRECT_INVESTMENT:
      urlPrefix += (security.getAssetClass().getCategoryType() == AssetclassType.EQUITIES ? "shares-others" : "bonds");
      break;
    case ISSUER_RISK_PRODUCT:
      urlPrefix += "certificates";
      break;
    }

    String url = "https://www.wienerborse.at/en/" + urlPrefix + "/historical-data/?ISIN=" + security.getIsin()
        + "&ID_NOTATION=" + security.getUrlHistoryExtend();

    Document doc = Jsoup.connect(url).userAgent(GlobalConstants.USER_AGENT).get();
    Element link = doc.select("a:contains(csv-file)").first();
    String linkHref = UriUtils.decode(link.attr("href"), "UTF-8");
    linkHref = linkHref.replaceFirst("(.*\\[DATETIME_TZ_START_RANGE\\]=)([0-9/])*(.*)", "$1" + sdf.format(from) + "$3");
    linkHref = DOMAIN_NAME
        + linkHref.replaceFirst("(.*\\[DATETIME_TZ_END_RANGE\\]=)([0-9/])*(.*)", "$1" + sdf.format(to) + "$3");
    return linkHref;

  }

  private List<Historyquote> getEodSecurityHistoryFromChart(final Security security, final Date from, final Date to)
      throws Exception {
    String url = getSecurityHistoricalDownloadLink(security, from, DateHelper.setTimeToZeroAndAddDay(to, 1));
    final FullChartData fcd = getChartResponse(url);
    final List<Historyquote> historyquotes = new ArrayList<>();
    for (int i = 0; i < fcd.data.length; i++) {
      Quote quote = fcd.data[i];
      // There is minimal data for two years, if the security can also be traded for
      // two years.
      Date date = DateHelper.setTimeToZeroAndAddDay(quote.DATETIME_LAST, 0);
      if (!date.before(from) && !date.after(to)) {
        final Historyquote historyquote = new Historyquote();
        historyquotes.add(historyquote);

        historyquote.setDate(date);
        historyquote.setClose(quote.LAST);
        historyquote.setOpen(quote.FIRST);
        historyquote.setHigh(quote.HIGH);
        historyquote.setLow(quote.LOW);
        if (quote.TOTAL_VOLUME % 1 == 0) {
          historyquote.setVolume((long) quote.TOTAL_VOLUME);
        }
      }
    }
    return historyquotes;
  }

  private FullChartData getChartResponse(String url) throws IOException, InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder().GET().header("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT)
        .uri(URI.create(url)).build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    return objectMapper.readValue(response.body(), FullChartData.class);
  }

  private static class FullChartData {
    public Quote[] data;
    public CurrentPrice currentPrice;
  }

  private static class Quote extends Ohlc {
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "s")
    public Date DATETIME_LAST;
    public double TOTAL_VOLUME;
  }

  private static class CurrentPrice extends Ohlc {
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "s")
    public Date DATETIME_PRICE;
  }

  private static class Ohlc {
    public double FIRST;
    public double HIGH;
    public double LOW;
    public double LAST;
  }
}
