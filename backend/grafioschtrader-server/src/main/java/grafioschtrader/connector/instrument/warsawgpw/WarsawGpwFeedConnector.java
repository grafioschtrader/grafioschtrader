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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.validation.ISINValidator;

@Component
public class WarsawGpwFeedConnector extends BaseFeedConnector {

  private final Logger log = LoggerFactory.getLogger(this.getClass());
  private static String DOMAIN_NAME_URL = "https://gpwbenchmark.pl/chart-json.php?req=";
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final ObjectMapper objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  
  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }
  
  public WarsawGpwFeedConnector() {
    super(supportedFeed, "warsawgpw", "Warsaw GPW", "Dummy pattern");
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
      url = DOMAIN_NAME_URL + "[" + objectMapper.writeValueAsString(rq) + "]&t=" + System.currentTimeMillis();
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
