package grafioschtrader.connector.instrument.xetra;

import java.net.URI;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.common.DateHelper;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;

/**
 * No regex check of the URL extension is performed. This is usually the ISIN,
 * but a URL such as "ARIVA:US631101102" is also possible. The connector has not
 * yet been checked for functioning URL extensions. The check of the connector
 * with the URL extension provides the expected response and is therefore used.
 * 
 * A maximum of 10 years with historical daily rates can be read in. However, a
 * longer period can be divided into several chunks.
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

  private final Logger log = LoggerFactory.getLogger(this.getClass());

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
          security.setSChangePercentage(DataBusinessHelper
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
    List<Historyquote> historyquotes = new ArrayList<>();
    Date currentFrom = from;

    // Calculate the end date for the first chunk (up to 10 years from 'from')
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(from);
    calendar.add(Calendar.YEAR, 10);
    Date chunkEndDate = calendar.getTime();

    // Ensure the chunk end date does not exceed the overall 'to' date
    if (chunkEndDate.after(to)) {
      chunkEndDate = to;
    }

    while (!currentFrom.after(to)) {
      // Get the download link for the current chunk
      String url = getSecurityDownloadLink(security, currentFrom, chunkEndDate, security.getUrlHistoryExtend(),
          DAY_RESOLUTION, null);
      log.debug("Fetching data for period: {} to {} using URL: {}", currentFrom, chunkEndDate, url);

      try {
        final Quotes quotes = objectMapper.readValue(new URI(url).toURL(), Quotes.class);

        if (quotes.s.equals("ok")) {
          for (int i = 0; i < quotes.t.length; i++) {
            final Historyquote historyquote = new Historyquote();
            // Add only if the date is within the requested range (can happen with chunking)
            Date quoteDate = DateHelper.setTimeToZeroAndAddDay(new Date(quotes.t[i].getTime() * 1000), 0);
            if (!quoteDate.before(from) && !quoteDate.after(to)) {
              historyquotes.add(historyquote);
              historyquote.setDate(quoteDate);
              historyquote.setClose(quotes.c[i]);
              historyquote.setOpen(quotes.o[i]);
              historyquote.setHigh(quotes.h[i]);
              historyquote.setLow(quotes.l[i]);
              historyquote.setVolume(quotes.v[i]);
            } else {
              // Log if a date outside the original range was potentially returned by the API
              log.debug("Skipping quote outside requested range: {} (requested: {} to {})", quoteDate, from, to);
            }
          }
        } else {
          log.error("Failed to fetch data for period: {} to {}. Status: {}", currentFrom, chunkEndDate, quotes.s);
          throw new RuntimeException("Failed to fetch historical data from Xetra connector. Status: " + quotes.s);
        }
      } catch (Exception e) {
        log.error("Error fetching data for period: {} to {}", currentFrom, chunkEndDate, e);
        throw e; // Re-throw the exception for now
      }

      // Move to the next chunk
      currentFrom = DateHelper.setTimeToZeroAndAddDay(chunkEndDate, 1);

      // Calculate the end date for the next chunk
      calendar.setTime(currentFrom);
      calendar.add(Calendar.YEAR, 10);
      chunkEndDate = calendar.getTime();

      // Ensure the next chunk end date does not exceed the overall 'to' date
      if (chunkEndDate.after(to)) {
        chunkEndDate = to;
      }
    }

    // Sort the history quotes by date, as fetching in chunks might not guarantee
    // order
    historyquotes.sort((hq1, hq2) -> hq1.getDate().compareTo(hq2.getDate()));

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
