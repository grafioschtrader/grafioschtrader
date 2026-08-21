package grafioschtrader.connector.instrument.divvydiary;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Security;
import grafioschtrader.types.CreateType;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Only for dividends.
 *
 * Share splits have no effect on the dividend payment.
 */
@Component
public class DivvyDiaryConnector extends BaseFeedConnector {

  private static final int MAX_REQUESTS_PER_MINUTE = 40;
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final ObjectMapper objectMapper = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  private final Bucket bucket;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_DIVIDEND, new FeedIdentifier[] { FeedIdentifier.DIVIDEND });
  }

  private static final String DOMAIN_NAME = "https://api.divvydiary.com/";

  public DivvyDiaryConnector() {
    super(supportedFeed, "divvydiary", "DivvyDiary", null, EnumSet.noneOf(UrlCheck.class));
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.EQUITIES, AssetclassCategory.ETF);
    Bandwidth limit = Bandwidth.builder().capacity(MAX_REQUESTS_PER_MINUTE)
        .refillIntervally(MAX_REQUESTS_PER_MINUTE, Duration.ofMinutes(1)).build();
    this.bucket = Bucket.builder().addLimit(limit).build();
  }

  @Override
  public String getDividendHistoricalDownloadLink(Security security) {
    return DOMAIN_NAME + "symbols/" + security.getIsin();
  }

  @Override
  public List<Dividend> getDividendHistory(Security security, LocalDate fromDate) throws Exception {
    List<Dividend> dividends = new ArrayList<>();
    URL url = new URI(getDividendHistoricalDownloadLink(security)).toURL();
    waitForTokenOrGo(bucket);
    final DividendHead dividendHead = objectMapper.readValue(url.openStream(), DividendHead.class);

    for (DividendDetail dd : dividendHead.dividends) {
      if (!dd.exDate.isBefore(fromDate)) {
        dividends.add(0, new Dividend(security.getIdSecuritycurrency(), dd.exDate, dd.payDate, dd.amount, null,
            dd.currency, CreateType.CONNECTOR_CREATED));
      }
    }
    return dividends;
  }

  private static class DividendHead {
    public List<DividendDetail> dividends;
  }

  private static class DividendDetail {
    public LocalDate exDate;
    public LocalDate payDate;
    public Double amount;
    public String currency;
  }
}
