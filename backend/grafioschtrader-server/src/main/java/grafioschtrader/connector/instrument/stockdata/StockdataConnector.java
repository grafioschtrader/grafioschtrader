package grafioschtrader.connector.instrument.stockdata;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;

@Component
public class StockdataConnector extends BaseFeedConnector {

  private static final String DOMAIN_NAME_WITH_VERSION = "https://api.stockdata.org/v1/";
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;

  private static final ObjectMapper objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private String apiKey;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
    supportedFeed.put(FeedSupport.INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }

  public StockdataConnector() {
    super(supportedFeed, "stockdata", "Stockdata", null);
  }

  @Value("${gt.connector.stockdata.apikey}")
  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  @Override
  public boolean isActivated() {
    return !apiKey.isEmpty();
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    Date toDate = new Date();
    LocalDate fromLocalDate = DateHelper.getLocalDate(toDate).minusDays(7);
    return getSecurityHistoricalDownloadLink(security, DateHelper.getDateFromLocalDate(fromLocalDate), toDate);
  }

  private String getSecurityHistoricalDownloadLink(final Security security, Date from, Date to) {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
    return DOMAIN_NAME_WITH_VERSION + "data/eod?symbols=" + security.getUrlHistoryExtend().toUpperCase() + "&date_from="
        + dateFormat.format(from) + "&date_to=" + dateFormat.format(to) + getApiKeyString();
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    return DOMAIN_NAME_WITH_VERSION + "data/quote?symbol=" + security.getUrlIntraExtend().toUpperCase()
        + getApiKeyString();
  }

  private String getApiKeyString() {
    return "&api_token=" + apiKey;
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    final List<Historyquote> historyquotes = new ArrayList<>();

    URL url = new URL(getSecurityHistoricalDownloadLink(security, from, to));
    final EODMetaData eodData = objectMapper.readValue(url, EODMetaData.class);
    for (int i = 0; i < eodData.data.length; i++) {
      if (!(eodData.data[i].date.before(from) || eodData.data[i].date.after(to))) {
        final Historyquote histroyquote = new Historyquote();
        historyquotes.add(histroyquote);
        histroyquote.setDate(eodData.data[i].date);
        histroyquote.setClose(eodData.data[i].close);
        histroyquote.setOpen(eodData.data[i].open);
        histroyquote.setLow(eodData.data[i].low);
        histroyquote.setHigh(eodData.data[i].high);
        histroyquote.setVolume(eodData.data[i].volume);
      }
    }

    return historyquotes;
  }

  static class EODMetaData {
    public String ticker;
    public String name;
    public String timezone_name;
    public EODData[] data;
  }

  static class EODData {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    public Date date;
    public Double open;
    public Double high;
    public Double low;
    public Double close;
    public Long volume;
  }

}
