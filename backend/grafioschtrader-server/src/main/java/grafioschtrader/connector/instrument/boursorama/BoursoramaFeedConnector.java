package grafioschtrader.connector.instrument.boursorama;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;

@Component
public class BoursoramaFeedConnector extends BaseFeedConnector {

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static List<BoursoramaPeriodEOD> boursoramaPeriods;
  private static String BASE_URL = "https://www.boursorama.com/bourse/action/graph/ws/";

  // Intraday main contain only a empty array when there was no trading. Because of that ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT is enabled
  private static final ObjectMapper objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).enable(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT);

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY_URL });
    supportedFeed.put(FeedSupport.INTRA,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL, FeedIdentifier.CURRENCY_URL });

    boursoramaPeriods = Arrays.asList(new BoursoramaPeriodEOD(30, 30, 0), new BoursoramaPeriodEOD(90, 90, 0),
        new BoursoramaPeriodEOD(180, 180, 0), new BoursoramaPeriodEOD(365, 365, 0),
        new BoursoramaPeriodEOD(1825, 1825, 0), new BoursoramaPeriodEOD(3650, 3650, 0),
        new BoursoramaPeriodEOD(7300, 7300, 0));
  }

  public BoursoramaFeedConnector() {
    super(supportedFeed, "boursorama", "Boursorama", null);
  }

  private String getSecurityCurrecnypairHistoricalDownloadLink(final Securitycurrency<?> securitycurrency, Date from) {
    long daysUntilNow = DateHelper.getDateDiff(from, new Date(), TimeUnit.DAYS);
    BoursoramaPeriodEOD boursoramaPeriod = boursoramaPeriods.stream().filter(bp -> daysUntilNow <= bp.maxDays)
        .findFirst().orElse(boursoramaPeriods.get(boursoramaPeriods.size() - 1));
    return BASE_URL + "GetTicksEOD?symbol=" + securitycurrency.getUrlHistoryExtend() + "&length="
        + boursoramaPeriod.lengthParam + "&period=" + boursoramaPeriod.period + "guid=";
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    Date toDate = new Date();
    LocalDate fromLocalDate = DateHelper.getLocalDate(toDate).minusDays(7);
    return getSecurityCurrecnypairHistoricalDownloadLink(security, DateHelper.getDateFromLocalDate(fromLocalDate));
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    return getEodSecurityCurrencypairHistory(from, to,
        new URL(getSecurityCurrecnypairHistoricalDownloadLink(security, from)),
        FeedConnectorHelper.getGBXLondonDivider(security));
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    Date toDate = new Date();
    LocalDate fromLocalDate = DateHelper.getLocalDate(toDate).minusDays(7);
    return getSecurityCurrecnypairHistoricalDownloadLink(currencypair, DateHelper.getDateFromLocalDate(fromLocalDate));
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final Date from, final Date to)
      throws IOException, ParseException, URISyntaxException {
    return getEodSecurityCurrencypairHistory(from, to,
        new URL(getSecurityCurrecnypairHistoricalDownloadLink(currencyPair, from)), 1.0);
  }

  private List<Historyquote> getEodSecurityCurrencypairHistory(final Date from, final Date to, URL url, double divider)
      throws StreamReadException, DatabindException, IOException {

    long startDay = DateHelper.getLocalDate(from).toEpochDay();
    long endDay = DateHelper.getLocalDate(to).toEpochDay();

    final List<Historyquote> historyquotes = new ArrayList<>();
    final HeaderEOD header = objectMapper.readValue(url, HeaderEOD.class);
    for (QuoteTabEOD data : header.d.QuoteTab) {
      if (data.d >= startDay && data.d <= endDay) {
        final Historyquote histroyquote = new Historyquote();
        historyquotes.add(histroyquote);
        LocalDate localDate = LocalDate.ofEpochDay(data.d);
        histroyquote.setDate(DateHelper.getDateFromLocalDate(localDate));
        histroyquote.setClose(data.c / divider);
        histroyquote.setOpen(data.o / divider);
        histroyquote.setLow(data.l / divider);
        histroyquote.setHigh(data.h / divider);
        histroyquote.setVolume(data.v);
      }
    }
    return historyquotes;
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return getUpdateSecurityCurrecnypairLastPriceLink(security);
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
    updateSecuritycurrency(security, getUpdateSecurityCurrecnypairLastPriceLink(security),
        FeedConnectorHelper.getGBXLondonDivider(security));
  }

  @Override
  public void updateCurrencyPairLastPrice(final Currencypair currencypair) throws IOException, ParseException {
    updateSecuritycurrency(currencypair, getUpdateSecurityCurrecnypairLastPriceLink(currencypair), 1.0);
  }

  private <T extends Securitycurrency<T>> void updateSecuritycurrency(T securitycurrency, String urlStr, double divider)
      throws IOException {
   
    final HeaderIntra header = objectMapper.readValue(new URL(urlStr), HeaderIntra.class);
    if (header != null) {
      header.d[0].setValues(securitycurrency, divider);
    } else {
      securitycurrency.setSChangePercentage(0.0);
    }
      
    setSTimestamp(securitycurrency);
  }

  private void setSTimestamp(Securitycurrency<?> securitycurrency) {
    securitycurrency.setSTimestamp(new Date(new Date().getTime() - getIntradayDelayedSeconds() * 1000));
  }
  
  private static class BoursoramaPeriodEOD {
    public int maxDays;
    public int lengthParam;
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

  private static class QuoteTabEOD {
    public int d;
    public double o;
    public double h;
    public double l;
    public double c;
    public long v;
  }

  private static class HeaderIntra {
    public QuoteTabIntra[] d;
  }

  private static class QuoteTabIntra {
    public double o;
    public double h;
    public double l;
    public double c;
    // public long v;
    public double var;
    // public QtIntra[] qt;

    public void setValues(Securitycurrency<?> securitycurrency, double divider) {
      securitycurrency.setSLast(c / divider);
      securitycurrency.setSOpen(o / divider);
      securitycurrency.setSLow(l / divider);
      securitycurrency.setSHigh(h / divider);
      if(o > 0 && var != 0.0) {
         securitycurrency.setSChangePercentage(DataHelper.roundStandard(var / o * 100));
      } else {
        securitycurrency.setSChangePercentage(0.0);
      }
    }
   
  }
/*
  private static class QtIntra {
    // public double d;
    // public double o;
  }
*/
}
