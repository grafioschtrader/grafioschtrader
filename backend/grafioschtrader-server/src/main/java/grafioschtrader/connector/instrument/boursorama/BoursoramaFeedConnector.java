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

import org.apache.commons.lang3.time.DateUtils;
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
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

@Component
public class BoursoramaFeedConnector extends BaseFeedConnector {

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static List<BoursoramaPeriodEOD> boursoramaPeriods;
  private static String BASE_URL = "https://www.boursorama.com/bourse/action/graph/ws/";

  // Intraday main contain only a empty array when there was no trading. Because
  // of that ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT is enabled
  private static final ObjectMapper objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .enable(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT);

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

  private String getSecurityCurrecnypairHistoricalDownloadLink(final Securitycurrency<?> securitycurrency, Date from,
      boolean useHistoricalExtend) {
    long daysUntilNow = DateHelper.getDateDiff(from, new Date(), TimeUnit.DAYS);
    BoursoramaPeriodEOD boursoramaPeriod = boursoramaPeriods.stream().filter(bp -> daysUntilNow <= bp.maxDays)
        .findFirst().orElse(boursoramaPeriods.get(boursoramaPeriods.size() - 1));
    return BASE_URL + "GetTicksEOD?symbol="
        + (useHistoricalExtend ? securitycurrency.getUrlHistoryExtend() : securitycurrency.getUrlIntraExtend())
        + "&length=" + boursoramaPeriod.lengthParam + "&period=" + boursoramaPeriod.period + "&guid=";
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    Date toDate = new Date();
    LocalDate fromLocalDate = DateHelper.getLocalDate(toDate).minusDays(7);
    return getSecurityCurrecnypairHistoricalDownloadLink(security, DateHelper.getDateFromLocalDate(fromLocalDate),
        true);
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    return getEodSecurityCurrencypairHistory(from, to,
        getSecurityCurrecnypairHistoricalDownloadLink(security, from, true),
        FeedConnectorHelper.getGBXLondonDivider(security));
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    Date toDate = new Date();
    LocalDate fromLocalDate = DateHelper.getLocalDate(toDate).minusDays(7);
    return getSecurityCurrecnypairHistoricalDownloadLink(currencypair, DateHelper.getDateFromLocalDate(fromLocalDate),
        true);
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final Date from, final Date to)
      throws IOException, ParseException, URISyntaxException, InterruptedException {
    return getEodSecurityCurrencypairHistory(from, to,
        getSecurityCurrecnypairHistoricalDownloadLink(currencyPair, from, true), 1.0);
  }

  private List<Historyquote> getEodSecurityCurrencypairHistory(final Date from, final Date to, String urlStr,
      double divider) throws StreamReadException, DatabindException, IOException, InterruptedException {

    long startDay = DateHelper.getLocalDate(from).toEpochDay();
    long endDay = DateHelper.getLocalDate(to).toEpochDay();

    final List<Historyquote> historyquotes = new ArrayList<>();
    final HeaderEOD header = objectMapper.readValue(FeedConnectorHelper.getByHttpClient(urlStr).body(),
        HeaderEOD.class);
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
    return isYearSecurityForLastPrice(security)
        ? getSecurityCurrecnypairHistoricalDownloadLink(security, DateUtils.addDays(new Date(), -190), false)
        : getUpdateSecurityCurrecnypairLastPriceLink(security);
  }

  private boolean isYearSecurityForLastPrice(Security security) {
    return security.getAssetClass().getCategoryType() == AssetclassType.FIXED_INCOME
        && security.getAssetClass().getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT;
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
    if (isYearSecurityForLastPrice(security)) {
      getLastPriceFromHistoryQuotes(security, getSecurityIntradayDownloadLink(security));
    } else {
      updateSecuritycurrency(security, getUpdateSecurityCurrecnypairLastPriceLink(security),
          FeedConnectorHelper.getGBXLondonDivider(security));
    }
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

  private void getLastPriceFromHistoryQuotes(Security security, String urlStr) throws Exception {
    final HeaderEOD header = objectMapper.readValue(new URL(urlStr), HeaderEOD.class);
    QuoteTabEOD lastPrice = header.d.QuoteTab[header.d.QuoteTab.length - 1];
    lastPrice.setValues(security, FeedConnectorHelper.getGBXLondonDivider(security));
    setSTimestamp(security);
  }

  private void setSTimestamp(Securitycurrency<?> securitycurrency) {
    securitycurrency.setSTimestamp(new Date(new Date().getTime() - getIntradayDelayedSeconds() * 1000));
  }

  private static class BoursoramaPeriodEOD {
    // Maximum days this period can handle
    public int maxDays;
    // This number is taken over as a value in the query for the "length" parameter
    public int lengthParam;
    // This value is probably always 0
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

  private static class QuoteTabEOD extends QuoteTab {
    public int d;

  }

  private static class HeaderIntra {
    public QuoteTabIntra[] d;
  }

  private static class QuoteTabIntra extends QuoteTab {
    public double var;
    // public QtIntra[] qt;

    public void setValues(Securitycurrency<?> securitycurrency, double divider) {
      super.setValues(securitycurrency, divider);
      if (o > 0 && var != 0.0) {
        securitycurrency.setSChangePercentage(DataHelper.roundStandard(var / o * 100));
      } else {
        securitycurrency.setSChangePercentage(0.0);
      }
    }

  }

  private static class QuoteTab {
    public double o;
    public double h;
    public double l;
    public double c;
    public long v;

    public void setValues(Securitycurrency<?> securitycurrency, double divider) {
      securitycurrency.setSLast(c / divider);
      if (o > 0d) {
        securitycurrency.setSOpen(o / divider);
      }
      if (l > 0d) {
        securitycurrency.setSLow(l / divider);
      }
      if (h > 0d) {
        securitycurrency.setSHigh(h / divider);
      }
      if (o > 0d && c > 0d) {
        securitycurrency.setSChangePercentage((c - o) / o * 100);
      }
    }

  }

}
