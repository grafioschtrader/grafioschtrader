package grafioschtrader.connector.instrument.xetra;

import java.net.URL;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;

@Component
public class XetraFeedConnector extends BaseFeedConnector {

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final ObjectMapper objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }

  public XetraFeedConnector() {
    super(supportedFeed, "xetra", "Xetra", null);
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    Date toDate = new Date();
    LocalDate fromLocalDate = DateHelper.getLocalDate(toDate).minusDays(7);
    return getSecurityHistoricalDownloadLink(security, DateHelper.getDateFromLocalDate(fromLocalDate), toDate);
  }

  private String getSecurityHistoricalDownloadLink(final Security security, Date from, Date to) {
    return "https://api.boerse-frankfurt.de/v1/tradingview/history?symbol=XFRA%3A" + security.getUrlHistoryExtend()
        + "&resolution=1D&from=" + (from.getTime() / 1000) + "&to=" + (to.getTime() / 1000);
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    final Quotes quotes = objectMapper.readValue(new URL(getSecurityHistoricalDownloadLink(security, from, to)),
        Quotes.class);
    final List<Historyquote> historyquotes = new ArrayList<>();
    for(int i = 0; i < quotes.t.length; i++) {
      final Historyquote historyquote = new Historyquote();
      historyquotes.add(historyquote);
      historyquote.setDate(new Date(quotes.t[i].getTime() * 1000));
      historyquote.setClose(quotes.c[i]);
      historyquote.setOpen(quotes.o[i]);
      historyquote.setHigh(quotes.h[i]);
      historyquote.setLow(quotes.l[i]);
      historyquote.setVolume(quotes.v[i]);
    }
    return historyquotes;
  }

  private static class Quotes {
    public String s;
    @JsonFormat(shape=JsonFormat.Shape.NUMBER, pattern="s")
    public Timestamp[] t;
    public double[] c;
    public double[] o;
    public double[] h;
    public double[] l;
    public long[] v;
  }

  
}
