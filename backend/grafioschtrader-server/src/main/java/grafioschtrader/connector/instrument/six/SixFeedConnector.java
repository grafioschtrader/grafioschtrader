/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package grafioschtrader.connector.instrument.six;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;

/*-
 * Stock, Bond, ETF:
 *
 * Dividend: TODO Implement Dividend
 * https://www.six-group.com/sheldon/share_details/v1/CH0244767585/share/dividend.json
 *
 * Splits: Not Supported
 *
 */
@Component
public class SixFeedConnector extends BaseFeedConnector {
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private final Logger log = LoggerFactory.getLogger(this.getClass());

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
    supportedFeed.put(FeedSupport.INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }

  private static final String DOMAIN_NAME_WITH_PROTO = "https://www.six-group.com/";
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final String FROM_DATE_FORMAT_SIX = GlobalConstants.SHORT_STANDARD_DATE_FORMAT;
  private static final String DATE_FORMAT_SIX = "yyyyMMdd HH:mm:ss";
  private static final String URL_EXTENDED_REGEX = "^([A-Z]{2})([A-Z0-9]{9})([0-9]{1})[A-Za-z]{3}\\d$";

  public SixFeedConnector() {
    super(supportedFeed, "six", "Swiss Stock Exchange", URL_EXTENDED_REGEX);
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    long date = System.currentTimeMillis();
    int offset = TimeZone.getDefault().getOffset(date);
    long zurichNowInMilli = date + offset;

    String url = DOMAIN_NAME_WITH_PROTO
        + "itf/fqs/delayed/movie.json?select=ValorSymbol,MarketDate,Currency,ClosingPrice,ClosingPerformance,WeekAgoPerformance,MonthAgoPerformance,YearAgoPerformance,"
        + "YearToDatePerformance,LatestTradeVolume,TotalVolume,PreviousClosingPrice,BidPrice,AskPrice,BidVolume,AskVolume,DailyLowPrice,DailyLowTime,DailyHighPrice,DailyHighTime,YearlyLowPrice,"
        + "YearlyLowDate,YearlyHighPrice,YearlyHighDate&where=ValorId=" + security.getUrlIntraExtend()
        + "&dojo.preventCache=" + zurichNowInMilli;
    log.info(url);
    return url;
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {

    HttpClient httpClient = HttpClient.newBuilder().build();
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getSecurityIntradayDownloadLink(security))).GET()
        .build();
    HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

    parseJsonData(security, response.body());
  }

  private void parseJsonData(final Security security, final String jsonData) throws JSONException, ParseException {
    /*-
     * 0: ValorSymbol
     * 1: MarketDate,
     * 2: Currency,
     * 3: ClosingPrice,
     * 4: ClosingPerformance,
     * 5: WeekAgoPerformance,
     * 6: MonthAgoPerformance,
     * 7: YearAgoPerformance,
     * 8: YearToDatePerformance
     * 9: LatestTradeVolume,
     * 10:TotalVolume,
     * 11:PreviousClosingPrice",
     * 12: BidPrice,
     * 13:AskPrice,
     * 14:BidVolume,
     * 15:AskVolume,
     * 16:DailyLowPrice,
     * 17:DailyLowTime,
     * 18:DailyHighPrice,
     * 19:DailyHighTime,
     * 20:YearlyLowPrice,
     * 21:YearlyLowDate,
     * 22:YearlyHighPrice,
     * 23:YearlyHighDate
     *
     * ["SMICHA",20170802,"CHF",93.56,0.56,1.35,2.01,13.86,12.13,7300,36145,93.04,93.63,93.68,15000,15000,93.27,92935.84
     * ,93.84,150552.39,76.9,20161104,93.85,20170626]
     */

    final JSONObject obj = new JSONObject(jsonData);

    // Set Time "delayedDateTime": "20170802T18:28:17.313",

    final String dateTimeString = obj.getString("delayedDateTime").replace('T', ' ').substring(0, 17);
    final DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_SIX);
    dateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Zurich"));
    security.setSTimestamp(dateFormat.parse(dateTimeString));

    // Set performance data
    final JSONArray rowDataJsonArrayOuter = obj.getJSONArray("rowData");
    final JSONArray rowDataJsonArrayInner = (JSONArray) rowDataJsonArrayOuter.get(0);
    security.setSLast(rowDataJsonArrayInner.getDouble(3));
    security.setSChangePercentage(rowDataJsonArrayInner.getDouble(4));
    security.setSPrevClose(rowDataJsonArrayInner.getDouble(11));

    security.setSLow(rowDataJsonArrayInner.get(16).toString().isEmpty() ? null : rowDataJsonArrayInner.getDouble(16));
    security.setSHigh(rowDataJsonArrayInner.get(18).toString().isEmpty() ? null : rowDataJsonArrayInner.getDouble(18));
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    return DOMAIN_NAME_WITH_PROTO + "itf/fqs/delayed/charts.json?select=ISIN,ClosingPrice,"
        + "ClosingPerformance,PreviousClosingPrice,LatestTradeTime,LatestTradeDate&where=ValorId="
        + security.getUrlHistoryExtend()
        + "&columns=Date,Time,Close,Open,Low,High,TotalVolume&netting=1440&nd=true&type=2";
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    final List<Historyquote> historyquotes = new ArrayList<>();
    final DateFormat dateFormat = new SimpleDateFormat(FROM_DATE_FORMAT_SIX);
    objectMapper.setDateFormat(dateFormat);
    final String urlStr = getSecurityHistoricalDownloadLink(security) + "&fromdate=" + dateFormat.format(from);
    URL url = new URL(urlStr);
    final HistoryQuote readHistoryquotes = objectMapper.readValue(url, HistoryQuote.class);

    DataValues dataValues = readHistoryquotes.valors[0].data;
    for (int i = 0; i < dataValues.Date.length; i++) {
      Date date = dateFormat.parse(dataValues.Date[i]);
      if (date.getTime() >= from.getTime() && date.getTime() <= to.getTime()) {
        final Historyquote histroyquote = new Historyquote();
        historyquotes.add(histroyquote);
        histroyquote.setDate(date);
        histroyquote.setClose(dataValues.Close[i]);
        histroyquote.setOpen(dataValues.Open[i]);
        histroyquote.setLow(dataValues.Low[i]);
        histroyquote.setHigh(dataValues.High[i]);
        histroyquote.setVolume(dataValues.TotalVolume[i]);
      }
    }
    return historyquotes;
  }

}

class HistoryQuote {
  public int delayMinutes;
  public String delayedDateTime;
  public Valors[] valors;

}

class Valors {
  public String ISIN;
  public double ClosingPrice;
  public double ClosingPerformance;
  public double PreviousClosingPrice;
  public String LatestTradeTime;
  public Date LatestTradeDate;
  public DataValues data;

}

class DataValues {
  public String[] Date;
  public String[] Time;
  public double[] Close;
  public double[] Open;
  public double[] Low;
  public double[] High;
  public long[] TotalVolume;
}