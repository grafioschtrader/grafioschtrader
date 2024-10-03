package grafioschtrader.connector.instrument.xetra;

import java.net.URI;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;

/**
 * No regex check of the URL extension is performed. This is usually the ISIN,
 * but a URL such as "ARIVA:US631101102" is also possible. The connector has not
 * yet been checked for functioning URL extensions. The check of the connector
 * with the URL extension provides the expected response and is therefore used.
 */
@Component
public class XetraFeedConnector extends BaseFeedConnector {

  public static final String STOCK_EX_MIC_XETRA = "XETR";
  public static final String STOCK_EX_MIC_FRANKFURT = "XFRA";

  private static final String DOMAIN_VERSION = "https://api.boerse-frankfurt.de/v1/";
  private static final String DAY_RESOLUTION = "1D";
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final ObjectMapper objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
    supportedFeed.put(FeedSupport.FS_HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }

  public XetraFeedConnector() {
    super(supportedFeed, "xetra", "Xetra", null, EnumSet.of(UrlCheck.INTRADAY, UrlCheck.HISTORY));
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    Date toDate = new Date();
    LocalDate fromLocalDate = DateHelper.getLocalDate(toDate).minusDays(10);
    return getSecurityDownloadLink(security, DateHelper.getDateFromLocalDate(fromLocalDate), toDate,
        security.getUrlIntraExtend(), DAY_RESOLUTION, 2);
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    final Quotes quotes = objectMapper.readValue(new URI(getSecurityIntradayDownloadLink(security)).toURL(),
        Quotes.class);
    if (quotes.s.equals("ok")) {
      for (int i = quotes.t.length - 1; i >= quotes.t.length - 2; i--) {
        if (i == quotes.t.length - 1) {
          security.setSLast(quotes.c[i]);
          security.setSLow(quotes.l[i]);
          security.setSHigh(quotes.h[i]);
          security.setSVolume(quotes.v[i]);
          security.setSTimestamp(new Date(new Date().getTime() - getIntradayDelayedSeconds() * 1000));
        } else {
          security.setSPrevClose(quotes.c[i]);
          security.setSChangePercentage(DataHelper
              .roundStandard((security.getSLast() - security.getSPrevClose()) / security.getSPrevClose() * 100));
        }
      }
    }
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    Date toDate = new Date();
    LocalDate fromLocalDate = DateHelper.getLocalDate(toDate).minusDays(7);
    return getSecurityDownloadLink(security, DateHelper.getDateFromLocalDate(fromLocalDate), toDate,
        security.getUrlHistoryExtend(), DAY_RESOLUTION, null);
  }

  private String getSecurityDownloadLink(final Security security, Date from, Date to, String urlExtend,
      String resolution, Integer countback) {
    String prefix = DOMAIN_VERSION + "tradingview/history?symbol="
        + (urlExtend.contains(":") ? urlExtend : security.getStockexchange().getMic() + ":" + urlExtend);
    return prefix + "&resolution=" + resolution + "&from=" + (from.getTime() / 1000) + "&to=" + (to.getTime() / 1000)
        + (countback == null ? "" : "&countback=" + countback);
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    final Quotes quotes = objectMapper.readValue(
        new URI(getSecurityDownloadLink(security, from, to, security.getUrlHistoryExtend(), DAY_RESOLUTION, null))
            .toURL(),
        Quotes.class);
    final List<Historyquote> historyquotes = new ArrayList<>();
    if (quotes.s.equals("ok")) {
      for (int i = 0; i < quotes.t.length; i++) {
        final Historyquote historyquote = new Historyquote();
        historyquotes.add(historyquote);
        historyquote.setDate(DateHelper.setTimeToZeroAndAddDay(new Date(quotes.t[i].getTime() * 1000), 0));
        historyquote.setClose(quotes.c[i]);
        historyquote.setOpen(quotes.o[i]);
        historyquote.setHigh(quotes.h[i]);
        historyquote.setLow(quotes.l[i]);
        historyquote.setVolume(quotes.v[i]);
      }
    }
    return historyquotes;
  }

  private static class Quotes {
    public String s;
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "s")
    public Timestamp[] t;
    public double[] c;
    public double[] o;
    public double[] h;
    public double[] l;
    public long[] v;
  }

}
