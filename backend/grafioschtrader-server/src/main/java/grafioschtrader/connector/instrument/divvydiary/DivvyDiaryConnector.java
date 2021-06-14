package grafioschtrader.connector.instrument.divvydiary;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Security;
import grafioschtrader.types.CreateType;

/**
 * Only for dividents.
 * 
 * Share splits have no effect on the dividend payment.
 * 
 * 
 * @author Hugo Graf
 *
 */
@Component
public class DivvyDiaryConnector extends BaseFeedConnector {

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final ObjectMapper objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.DIVIDEND, new FeedIdentifier[] { FeedIdentifier.DIVIDEND });
  }

  private static final String DOMAIN_NAME_WITH_VERSION = "https://api.divvydiary.com/";

  public DivvyDiaryConnector() {
    super(supportedFeed, "divvydiary", "DivvyDiary", null);
  }

  @Override
  public String getDividendHistoricalDownloadLink(Security security) {
    return DOMAIN_NAME_WITH_VERSION + "symbols/" + security.getIsin();
  }

  @Override
  public List<Dividend> getDividendHistory(Security security, LocalDate fromDate) throws Exception {
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    List<Dividend> dividends = new ArrayList<>();
    URL url = new URL(getDividendHistoricalDownloadLink(security));
    final DividendHead dividendHead = objectMapper.readValue(url, DividendHead.class);

    for (DividendDetail dd : dividendHead.dividends) {
      LocalDate exDate = dd.exDate.toLocalDate();
      if (!exDate.isBefore(fromDate)) {
        dividends.add(0, new Dividend(security.getIdSecuritycurrency(), dd.exDate.toLocalDate(), dd.payDate.toLocalDate(),
            dd.amount, null, dd.currency, CreateType.CONNECTOR_CREATED));
      }
    }
    return dividends;
  }

  private static class DividendHead {
    public String name;
    public String symbol;
    public String isin;
    public String wkn;
    public String exchange;
    public List<DividendDetail> dividends;
  }

  private static class DividendDetail {
    // @JsonFormat(shape = JsonFormat.Shape.STRING, pattern =
    // "yyyy-MM-dd'T'HH:mm:ssZ")
    public LocalDateTime exDate;
    // @JsonFormat(shape = JsonFormat.Shape.STRING, pattern =
    // "yyyy-MM-dd'T'HH:mm:ssZ")
    public LocalDateTime payDate;
    public Double amount;
    public String currency;
  }
}
