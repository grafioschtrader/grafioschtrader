package grafioschtrader.connector.instrument.finnhub;

import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.types.CreateType;

/**
 * Finnhub connector
 *
 * Stock, Bond, ETF:</br>
 * Splits were once free but anymore. The implementation was not removed.
 *
 *
 */
@Component
public class FinnhubConnector extends BaseFeedApiKeyConnector {
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;

  private static final ObjectMapper objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
    supportedFeed.put(FeedSupport.INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
    // supportedFeed.put(FeedSupport.SPLIT, new FeedIdentifier[] {
    // FeedIdentifier.SPLIT_URL });
  }

  private static final String DOMAIN_NAME_WITH_VERSION = "https://finnhub.io/api/v1/";

  public FinnhubConnector() {
    super(supportedFeed, "finnhub", "Finnhub", null);
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    Date toDate = new Date();
    LocalDate fromLocalDate = DateHelper.getLocalDate(toDate).minusDays(7);
    return getSecurityHistoricalDownloadLink(security, DateHelper.getDateFromLocalDate(fromLocalDate), toDate);
  }

  private String getSecurityHistoricalDownloadLink(final Security security, Date from, Date to) {
    return DOMAIN_NAME_WITH_VERSION + "stock/candle?symbol=" + security.getUrlHistoryExtend().toUpperCase() + "&from="
        + (from.getTime() / 1000) + "&to=" + (to.getTime() / 1000) + "&resolution=D&token=" + getApiKey();
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return DOMAIN_NAME_WITH_VERSION + "quote?symbol=" + security.getUrlIntraExtend().toUpperCase() + "&token="
        + getApiKey();
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    final List<Historyquote> historyquotes = new ArrayList<>();

    URL url = new URL(getSecurityHistoricalDownloadLink(security, from, to));
    final CandleData candleData = objectMapper.readValue(url, CandleData.class);

    if (candleData.s.equals("ok")) {
      for (int i = 0; i < candleData.t.length; i++) {
        Date date = new Date(candleData.t[i] * 1000);
        if (date.getTime() >= from.getTime() && date.getTime() <= to.getTime()) {
          final Historyquote histroyquote = new Historyquote();
          historyquotes.add(histroyquote);
          histroyquote.setDate(date);
          histroyquote.setClose(candleData.c[i]);
          histroyquote.setOpen(candleData.o[i]);
          histroyquote.setLow(candleData.l[i]);
          histroyquote.setHigh(candleData.h[i]);
          histroyquote.setVolume(candleData.v[i]);
        }
      }
    }
    return historyquotes;
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    URL url = new URL(getSecurityIntradayDownloadLink(security));
    final Quote quote = objectMapper.readValue(url, Quote.class);
    security.setSLast(quote.c);
    security.setSOpen(quote.o);
    security.setSLow(quote.l);
    security.setSHigh(quote.h);
    security.setSPrevClose(quote.pc);
    security.setSTimestamp(new Date(quote.t * 1000));
    security.setSChangePercentage(quote.dp);
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 0;
  }

  @Override
  public String getSplitHistoricalDownloadLink(Security security) {
    return getSplitHistoricalDownloadLink(security, LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY));
  }

  private String getSplitHistoricalDownloadLink(Security security, LocalDate from) {
    return DOMAIN_NAME_WITH_VERSION + "stock/split?symbol=" + security.getUrlSplitExtend().toUpperCase() + "&from="
        + from + "&to=" + LocalDate.now() + "&token=" + this.getApiKey();
  }

  @Override
  public List<Securitysplit> getSplitHistory(Security security, LocalDate fromDate) throws Exception {
    URL url = new URL(getSplitHistoricalDownloadLink(security, fromDate));
    final List<Securitysplit> securitysplits = new ArrayList<>();
    final Split[] splits = objectMapper.readValue(url, Split[].class);
    for (Split split : splits) {
      Securitysplit securitysplit = new Securitysplit(security.getIdSecuritycurrency(), split.date, split.fromFactor,
          split.toFactor, CreateType.CONNECTOR_CREATED);
      securitysplits.add(securitysplit);

    }
    return securitysplits;
  }

  private static class CandleData {
    /**
     * List of close prices for returned candles.
     */
    public double[] c;

    /**
     * List of high prices for returned candles.
     */
    public double[] h;
    /**
     * List of low prices for returned candles.
     */
    public double[] l;

    /**
     * List of open prices for returned candles.
     */
    public double[] o;
    /**
     * Status of the response. This field can either be ok or no_data.
     */
    public String s;
    /**
     * List of timestamp for returned candles.
     */
    public long[] t;
    /**
     * List of volume data for returned candles.
     */
    public long[] v;

  }

  private static class Quote {
    public double o;
    public double h;
  //  public double d;
    public double dp;
    public double l;
    public double c;
    public double pc;
    public long t;

  }

  private static class Split {
  //  public String symbol;
    public Date date;
    public int fromFactor;
    public int toFactor;

  }

}
