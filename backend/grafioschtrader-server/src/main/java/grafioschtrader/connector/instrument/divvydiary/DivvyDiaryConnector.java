package grafioschtrader.connector.instrument.divvydiary;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.DividendHistoryResult;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Security;
import grafioschtrader.types.CreateType;
import grafioschtrader.types.DistributionFrequency;
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
    return toDividends(security, fetch(security), fromDate);
  }

  /**
   * Returns the dividends together with DivvyDiary's own {@code dividendFrequency} from the same response.
   * {@code annually}, {@code biannually}, {@code quarterly}, {@code monthly} and {@code none} have a GT counterpart;
   * any other value leaves the frequency to the caller.
   */
  @Override
  public DividendHistoryResult getDividendHistoryWithFrequency(Security security, LocalDate fromDate) throws Exception {
    DividendHead dividendHead = fetch(security);
    return new DividendHistoryResult(toDividends(security, dividendHead, fromDate),
        toDistributionFrequency(dividendHead.dividendFrequency));
  }

  /**
   * Maps DivvyDiary's frequency to the GT one.
   *
   * @param dividendFrequency the value of {@code dividendFrequency}, may be null
   * @return the frequency, or null for a missing or unknown value
   */
  static DistributionFrequency toDistributionFrequency(String dividendFrequency) {
    return dividendFrequency == null ? null : switch (dividendFrequency) {
    case "annually" -> DistributionFrequency.DF_YEAR;
    case "biannually" -> DistributionFrequency.DF_SEMI_ANNUAL;
    case "quarterly" -> DistributionFrequency.DF_QUARTERLY;
    case "monthly" -> DistributionFrequency.DF_MONTHLY;
    case "none" -> DistributionFrequency.DF_NONE;
    default -> null;
    };
  }

  private DividendHead fetch(Security security) throws Exception {
    waitForTokenOrGo(bucket);
    // Sent with the user agent of the shared HTTP client: DivvyDiary answers the default agent of URL.openStream()
    // ("Java/25") with HTTP 403.
    HttpResponse<String> response = FeedConnectorHelper.getByHttpClient(getDividendHistoricalDownloadLink(security));
    if (response.statusCode() != 200) {
      // An unknown ISIN must fail, not look like an instrument without dividends.
      throw new IOException("HTTP " + response.statusCode() + " for " + getDividendHistoricalDownloadLink(security));
    }
    return objectMapper.readValue(response.body(), DividendHead.class);
  }

  private static List<Dividend> toDividends(Security security, DividendHead dividendHead, LocalDate fromDate) {
    List<Dividend> dividends = new ArrayList<>();
    for (DividendDetail dd : dividendHead.dividends == null ? List.<DividendDetail>of() : dividendHead.dividends) {
      if (dd.exDate != null && !dd.exDate.isBefore(fromDate)) {
        dividends.add(0, new Dividend(security.getIdSecuritycurrency(), dd.exDate, dd.payDate, dd.amount, null,
            dd.currency, CreateType.CONNECTOR_CREATED));
      }
    }
    return dividends;
  }

  private static class DividendHead {
    public String dividendFrequency;
    public List<DividendDetail> dividends;
  }

  private static class DividendDetail {
    public LocalDate exDate;
    public LocalDate payDate;
    public Double amount;
    public String currency;
  }
}
