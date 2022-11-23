package grafioschtrader.connector.instrument.warsawgpw;

import java.net.URL;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.validation.ISINValidator;

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
    supportedFeed.put(FeedSupport.INTRA,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
    supportedFeed.put(FeedSupport.HISTORY,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }
  
  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }
  
  public WarsawGpwFeedConnector() {
    super(supportedFeed, "warsawgpw", "Warsaw GPW", "Dummy pattern");
  }
  
  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return getUrlPrefix(security) + "?isin=" + security.getUrlIntraExtend(); 
  }

  
  private String getUrlPrefix(final Security security) {
    String prefix = null;
    switch(security.getAssetClass().getSpecialInvestmentInstrument()) {
      case NON_INVESTABLE_INDICES:
        prefix = DOMAIN_NAME_INDEX + "karta-indeksu";
        break;
      default:
        prefix = DOMAIN_NAME_INTRA + "spolka";
    }
    return prefix;
  }
  
  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    String url = getSecurityIntradayDownloadLink(security);
    Document doc = Jsoup.connect(url).userAgent(GlobalConstants.USER_AGENT).header("Accept-Language", "en").get();
    final Element div = doc.select("div.PaL.header.text-right.text-left-xs").first();
    String[] maxMinValues = div.selectFirst(".max_min").text().split("max");
    security.setSLow(FeedConnectorHelper.parseDoubleGE(maxMinValues[0].replaceAll("[^\\d,]", "")));;
    security.setSHigh(FeedConnectorHelper.parseDoubleGE(maxMinValues[1].replaceAll("[^\\d,]", "")));
    security.setSLast(FeedConnectorHelper.parseDoubleGE(div.selectFirst(".summary").text().replaceAll("[^\\d,]", "")));
    Element changeElement = div.selectFirst(".profit") == null? div.selectFirst(".loss"): div.selectFirst(".profit");  
    security.setSChangePercentage(FeedConnectorHelper.parseDoubleGE(changeElement.text().replaceAll("[^\\d,-]", "")));
    security.setSTimestamp(new Date(System.currentTimeMillis() - getIntradayDelayedSeconds() * 1000));
    
  }
  
  @Override
  protected void checkUrlExtendsionWithRegex(String[] patterns, String urlExtend) {
    ISINValidator iSINValidator = new ISINValidator();
    if (!iSINValidator.isValid(urlExtend, null)) {
      throw new IllegalArgumentException(urlExtend + " not accepted");
    }
  }
  
  
  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    Date toDate = new Date();
    final SimpleDateFormat dateFormat = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
    LocalDate fromLocalDate = DateHelper.getLocalDate(toDate).minusDays(7);
    return getSecurityHistoricalDownloadLink(security.getUrlHistoryExtend(),
        DateHelper.getDateFromLocalDate(fromLocalDate), toDate, dateFormat);
  }
  
  
  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
    final List<Historyquote> historyquotes = new ArrayList<>();
    Response[] response = objectMapper.readValue(new URL(getSecurityHistoricalDownloadLink(security.getUrlHistoryExtend(),
        from, to, dateFormat)), Response[].class);
    
    for(SingleDay sd :response[0].data) {
      Historyquote historyquote = new Historyquote();
      var ts = new Timestamp(sd.t * 1000);
      historyquote.setDate(new Date(ts.getTime()));
      historyquote.setOpen(sd.o);
      historyquote.setHigh(sd.h);
      historyquote.setLow(sd.l);
      historyquote.setClose(sd.c);
      historyquote.setVolume(sd.v);
      historyquotes.add(historyquote);
    }
    return historyquotes;
  }
  
  
  private String getSecurityHistoricalDownloadLink(String isin,
      Date from, Date to, SimpleDateFormat dateFormat) {
    var rq = new RequestParam("RANGE", isin, dateFormat.format(from), dateFormat.format(to));
    String url = null;
    try {
      url = DOMAIN_NAME_CHART + "[" + objectMapper.writeValueAsString(rq) + "]&t=" + System.currentTimeMillis();
    } catch (JsonProcessingException e) {
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
