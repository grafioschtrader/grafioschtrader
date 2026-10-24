package grafioschtrader.connector.instrument.divvydiary;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.connector.instrument.DividendHistoryResult;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.types.CreateType;
import grafioschtrader.types.DistributionFrequency;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * ISIN-based dividend history without authentication and split history with an administrator-configured API key.
 * Dividend requests never send the key, even when it is configured for splits.
 */
@Component
public class DivvyDiaryConnector extends BaseFeedApiKeyConnector {

  private static final int MAX_REQUESTS_PER_MINUTE = 40;
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static final ObjectMapper objectMapper = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();
  private final Bucket bucket;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_DIVIDEND, new FeedIdentifier[] { FeedIdentifier.DIVIDEND });
    supportedFeed.put(FeedSupport.FS_SPLIT, new FeedIdentifier[] { FeedIdentifier.SPLIT });
  }

  private static final String DOMAIN_NAME = "https://api.divvydiary.com/";

  public DivvyDiaryConnector() {
    super(supportedFeed, "divvydiary", "DivvyDiary", null, EnumSet.noneOf(UrlCheck.class));
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.EQUITIES, AssetclassCategory.ETF);
    Bandwidth limit = Bandwidth.builder().capacity(MAX_REQUESTS_PER_MINUTE)
        .refillIntervally(MAX_REQUESTS_PER_MINUTE, Duration.ofMinutes(1)).build();
    this.bucket = Bucket.builder().addLimit(limit).build();
  }

  /** Dividends remain available without a split API key. */
  @Override
  public boolean isActivated() {
    return true;
  }

  @Override
  public boolean hasApiKeyInDownloadLink() {
    return false;
  }

  @Override
  public Map<FeedSupport, FeedIdentifier[]> getSecuritycurrencyFeedSupport() {
    Map<FeedSupport, FeedIdentifier[]> available = new HashMap<>(supportedFeed);
    if (!super.isActivated()) {
      available.remove(FeedSupport.FS_SPLIT);
    }
    return available;
  }

  @Override
  public FeedIdentifier[] getSecuritycurrencyFeedSupport(FeedSupport feedSupport) {
    return feedSupport == FeedSupport.FS_SPLIT && !super.isActivated() ? null : supportedFeed.get(feedSupport);
  }

  @Override
  public <S extends Securitycurrency<S>> void hasAPISubscriptionSupport(Securitycurrency<S> securitycurrency,
      FeedSupport feedSupport) {
    if (feedSupport == FeedSupport.FS_SPLIT && !super.isActivated()) {
      throw new GeneralNotTranslatedWithArgumentsException("gt.connector.subscription.failure",
          new Object[] { getReadableName(), feedSupport.name() });
    }
  }

  @Override
  public String getDividendHistoricalDownloadLink(Security security) {
    return DOMAIN_NAME + "symbols/" + security.getIsin();
  }

  @Override
  public List<Dividend> getDividendHistory(Security security, LocalDate fromDate) throws Exception {
    return toDividends(security, fetchDividends(security), fromDate);
  }

  /**
   * Returns the dividends together with DivvyDiary's own {@code dividendFrequency} from the same response.
   * {@code annually}, {@code biannually}, {@code quarterly}, {@code monthly} and {@code none} have a GT counterpart;
   * any other value leaves the frequency to the caller.
   */
  @Override
  public DividendHistoryResult getDividendHistoryWithFrequency(Security security, LocalDate fromDate) throws Exception {
    DividendHead dividendHead = fetchDividends(security);
    return new DividendHistoryResult(toDividends(security, dividendHead, fromDate),
        toDistributionFrequency(dividendHead.dividendFrequency));
  }

  @Override
  public String getSplitHistoricalDownloadLink(Security security) {
    return getDividendHistoricalDownloadLink(security);
  }

  @Override
  public List<Securitysplit> getSplitHistory(Security security, LocalDate fromDate, LocalDate toDate) throws Exception {
    String apiKey = getApiKey();
    if (apiKey == null || apiKey.isBlank()) {
      throw new IOException("DivvyDiary splits require an API key configured by an administrator.");
    }
    if (fromDate.isAfter(toDate)) {
      throw new IllegalArgumentException("Split history start date must not be after end date.");
    }
    String body = fetch(security, getSplitHistoricalDownloadLink(security), Map.of("X-API-Key", apiKey));
    SplitHead head = objectMapper.readValue(body, SplitHead.class);
    List<Securitysplit> splits = new ArrayList<>();
    for (SplitDetail split : head.splits == null ? List.<SplitDetail>of() : head.splits) {
      if (split == null || split.date == null || !validFactor(split.ratioFrom) || !validFactor(split.ratioTo)) {
        throw new IOException("Invalid DivvyDiary split for ISIN " + security.getIsin());
      }
      if (!split.date.isBefore(fromDate) && !split.date.isAfter(toDate)) {
        // DivvyDiary's ratio is new:old, whereas GT stores old:new.
        splits.add(new Securitysplit(security.getIdSecuritycurrency(), split.date, split.ratioTo, split.ratioFrom,
            CreateType.CONNECTOR_CREATED));
      }
    }
    splits.sort(Comparator.comparing(Securitysplit::getSplitDate));
    return splits;
  }

  private static boolean validFactor(Integer factor) {
    return factor != null && factor > 0 && factor <= 99_999_999;
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

  private DividendHead fetchDividends(Security security) throws Exception {
    // Keep the anonymous dividend request independent of the optional split credentials.
    return objectMapper.readValue(fetch(security, getDividendHistoricalDownloadLink(security), null),
        DividendHead.class);
  }

  private String fetch(Security security, String url, Map<String, String> headers) throws Exception {
    waitForTokenOrGo(bucket);
    // Sent with the user agent of the shared HTTP client: DivvyDiary answers the default agent of URL.openStream()
    // ("Java/25") with HTTP 403.
    HttpResponse<String> response = FeedConnectorHelper.getByHttpClient(url, null, headers);
    if (response.statusCode() != 200) {
      String movedTo = getMovedToIsin(response);
      if (movedTo != null) {
        // Not followed: the instrument got a new ISIN (ADR ratio change, reverse split), which GT has to adopt too.
        throw new IOException("DivvyDiary has moved ISIN " + security.getIsin() + " to " + movedTo
            + ". The ISIN of this security is probably outdated and should be changed to " + movedTo + ".");
      }
      // An unknown ISIN must fail, not look like an instrument without dividends.
      throw new IOException("HTTP " + response.statusCode() + " for " + url);
    }
    return response.body();
  }

  /**
   * Extracts the successor ISIN from a permanent redirect such as {@code Location: /symbols/US89686D3035}.
   *
   * @param response the response of the symbol request
   * @return the ISIN the redirect points to, or null when the response is no permanent redirect to another symbol
   */
  private static String getMovedToIsin(HttpResponse<String> response) {
    if (response.statusCode() != 301 && response.statusCode() != 308) {
      return null;
    }
    return response.headers().firstValue("Location").filter(location -> location.contains("/symbols/"))
        .map(location -> location.substring(location.lastIndexOf('/') + 1)).filter(isin -> !isin.isBlank())
        .orElse(null);
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

  private static class SplitHead {
    public List<SplitDetail> splits;
  }

  private static class SplitDetail {
    public LocalDate date;
    public Integer ratioFrom;
    public Integer ratioTo;
  }
}
