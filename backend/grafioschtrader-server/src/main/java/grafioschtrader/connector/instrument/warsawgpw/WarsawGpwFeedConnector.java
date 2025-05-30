package grafioschtrader.connector.instrument.warsawgpw;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.BaseConstants;
import grafiosch.common.DateHelper;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;

/**
 * It seems that all instruments on the Warsaw Stock Exchange have an ISIN. This makes it unnecessary to enter a URL
 * extension.
 *
 * The existence of an instrument cannot be checked via the connector.
 */
@Component
public class WarsawGpwFeedConnector extends BaseFeedConnector {

  private final Logger log = LoggerFactory.getLogger(this.getClass());
  private static String DOMAIN_NAME_INTRA = "https://gpw.pl/";
  private static String DOMAIN_NAME_INDEX = "https://gpwbenchmark.pl/";
  private static String DOMAIN_NAME_CHART = DOMAIN_NAME_INDEX + "chart-json.php?req=";
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final ObjectMapper objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY });
    supportedFeed.put(FeedSupport.FS_HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY });
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  public WarsawGpwFeedConnector() {
    super(supportedFeed, "warsawgpw", "Warsaw GPW", null, EnumSet.noneOf(UrlCheck.class));
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return getUrlIntradayPrefix(security) + "?isin=" + security.getIsin();
  }

  private String getUrlIntradayPrefix(final Security security) {
    String prefix = null;
    switch (security.getAssetClass().getSpecialInvestmentInstrument()) {
    case NON_INVESTABLE_INDICES:
      prefix = DOMAIN_NAME_INDEX + "karta-indeksu";
      break;
    case ETF:
      prefix = DOMAIN_NAME_INTRA + "etf";
      break;
    default:
      prefix = DOMAIN_NAME_INTRA + "spolka";
    }
    return prefix;
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    String url = getSecurityIntradayDownloadLink(security);
    Document doc = Jsoup.connect(url).timeout(60_000).userAgent(GlobalConstants.USER_AGENT_HTTPCLIENT)
        .header("Accept-Language", "en").get();
    final Element div = doc.select("div.PaL.header.text-right.text-left-xs").first();
    String[] maxMinValues = div.selectFirst(".max_min").text().split("max");
    security.setSLow(FeedConnectorHelper.parseDoubleGE(maxMinValues[0].replaceAll("[^\\d,]", "")));

    security.setSHigh(FeedConnectorHelper.parseDoubleGE(maxMinValues[1].replaceAll("[^\\d,]", "")));
    security.setSLast(FeedConnectorHelper.parseDoubleGE(div.selectFirst(".summary").text().replaceAll("[^\\d,]", "")));
    Element changeElement = div.selectFirst(".profit") == null ? div.selectFirst(".loss") : div.selectFirst(".profit");
    security.setSChangePercentage(FeedConnectorHelper.parseDoubleGE(changeElement.text().replaceAll("[^\\d,-]", "")));
    security.setSTimestamp(new Date(System.currentTimeMillis() - getIntradayDelayedSeconds() * 1000));
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    Date toDate = new Date();
    final SimpleDateFormat dateFormat = new SimpleDateFormat(BaseConstants.STANDARD_DATE_FORMAT);
    LocalDate fromLocalDate = DateHelper.getLocalDate(toDate).minusDays(7);
    return getSecurityHistoricalDownloadLink(security.getIsin(), DateHelper.getDateFromLocalDate(fromLocalDate), toDate,
        dateFormat);
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(BaseConstants.STANDARD_DATE_FORMAT);
    final List<Historyquote> historyquotes = new ArrayList<>();
    String urlStr = getSecurityHistoricalDownloadLink(security.getIsin(), from, to, dateFormat);
    Response[] response = objectMapper.readValue(new URI(urlStr).toURL(), Response[].class);
    int diffUTCSeconds = security.getStockexchange().getTimeDifferenceFromUTCInSeconds();
    for (SingleDay sd : response[0].data) {
      Historyquote historyquote = new Historyquote();
      Date quoteDate = DateHelper.setTimeToZeroAndAddDay(new Date((sd.t + diffUTCSeconds) * 1000), 0);
      historyquote.setDate(quoteDate);
      historyquote.setOpen(sd.o);
      historyquote.setHigh(sd.h);
      historyquote.setLow(sd.l);
      historyquote.setClose(sd.c);
      historyquote.setVolume(sd.v);
      historyquotes.add(historyquote);
    }
    return historyquotes;
  }

  private String getSecurityHistoricalDownloadLink(String isin, Date from, Date to, SimpleDateFormat dateFormat) {
    var rq = new RequestParam("RANGE", isin, dateFormat.format(from), dateFormat.format(to));
    String url = null;
    try {
      String encoded = URLEncoder.encode("[" + objectMapper.writeValueAsString(rq) + "]",
          StandardCharsets.UTF_8.name());
      url = DOMAIN_NAME_CHART + encoded + "&t=" + System.currentTimeMillis();
    } catch (Exception e) {
      log.error("Could not create JSON for ISIN {}", isin);
    }

    return url;
  }

  @SuppressWarnings("unused")
  private static class RequestParam {
    public String mode;
    public String isin;
    public String from;
    public String to;

    public RequestParam() {
    }

    public RequestParam(String mode, String isin, String from, String to) {
      this.mode = mode;
      this.isin = isin;
      this.from = from;
      this.to = to;
    }
  }

  private static class Response extends RequestParam {
    public SingleDay[] data;
  }

  private static class SingleDay {
    public long t;
    public double o;
    public double c;
    public double h;
    public double l;
    public long v;
  }
}
