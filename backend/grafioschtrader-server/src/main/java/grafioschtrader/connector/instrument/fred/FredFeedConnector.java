package grafioschtrader.connector.instrument.fred;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Connector for the FRED (Federal Reserve Economic Data) API at <a href="https://api.stlouisfed.org">St. Louis Fed</a>.
 * Designed to provide historical risk-free interest-rate series (used for Sharpe ratio and similar risk metrics).
 *
 * <p>
 * Each FRED series id (e.g. {@code DGS3MO}, {@code ECBESTRVOLWGTTRMDMNRT}, {@code IRLTLT01CHM156N}) is wired up as the
 * {@code url_history_extend} of a synthetic Security with asset-class category
 * {@link grafioschtrader.connector.instrument.IFeedConnector.AssetclassCategory#NON_INVESTABLE_INDICES}. The connector
 * only supports historical (EOD) retrieval — there is no intraday concept for these series.
 *
 * <p>
 * The FRED endpoint returns rates as percent (e.g. {@code "4.32"} meaning 4.32%). They are stored in the
 * {@code historyquote.close} column as a decimal fraction (i.e. divided by 100, so 4.32% → 0.0432). Missing
 * observations (encoded as {@code "."} in the FRED response) are skipped.
 *
 * <p>
 * Authentication is via a free API key registered through the existing "Connector API Keys" admin UI
 * ({@code id_provider = "fred"}). FRED's free tier permits 120 requests/minute, which is comfortable for this use case
 * (one call per series per EOD run).
 */
@Component
public class FredFeedConnector extends BaseFeedApiKeyConnector {

  private static final Logger log = LoggerFactory.getLogger(FredFeedConnector.class);

  private static final String DOMAIN = "https://api.stlouisfed.org/fred/series/observations";
  private static final String API_KEY_PARAM = "api_key";
  private static final String URL_SERIES_REGEX = "^[A-Z0-9]+$";
  private static final LocalDate OLDEST_TRADING_DAY = LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY);

  private static final ObjectMapper objectMapper = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }

  public FredFeedConnector() {
    super(supportedFeed, "fred", "FRED (St. Louis Fed)", URL_SERIES_REGEX, EnumSet.noneOf(UrlCheck.class));
    supportedAssetclassCategories = EnumSet.of(AssetclassCategory.NON_INVESTABLE_INDICES);
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    LocalDate to = LocalDate.now();
    LocalDate from = to.minusDays(30);
    return buildHistoricalUrl(security, from, to);
  }

  private String buildHistoricalUrl(final Security security, final LocalDate from, final LocalDate to) {
    DateTimeFormatter d = DateTimeFormatter.ISO_LOCAL_DATE;
    return DOMAIN + "?series_id=" + security.getUrlHistoryExtend()
        + "&" + API_KEY_PARAM + "=" + getApiKey()
        + "&file_type=json"
        + "&observation_start=" + d.format(from)
        + "&observation_end=" + d.format(to);
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final LocalDate from, final LocalDate to)
      throws Exception {
    final List<Historyquote> historyquotes = new ArrayList<>();

    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder().GET().header("accept", "application/json")
        .uri(URI.create(buildHistoricalUrl(security, from, to))).build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      log.warn("FRED returned status {} for series {}: {}", response.statusCode(), security.getUrlHistoryExtend(),
          hideApiKeyForError(buildHistoricalUrl(security, from, to)));
      return historyquotes;
    }

    final FredResponse parsed = objectMapper.readValue(response.body(), FredResponse.class);
    if (parsed == null || parsed.observations == null) {
      return historyquotes;
    }

    for (FredObservation obs : parsed.observations) {
      if (obs.value == null || ".".equals(obs.value)) {
        continue;
      }
      try {
        LocalDate date = LocalDate.parse(obs.date);
        // Drop observations outside requested window, and any earlier than GT's OLDEST_TRADING_DAY —
        // FRED dates monthly observations on the 1st of each month (e.g. 2000-01-01), which would
        // fail the @AfterEqual constraint on Historyquote.date when OLDEST_TRADING_DAY is later.
        if (date.isBefore(from) || date.isAfter(to) || date.isBefore(OLDEST_TRADING_DAY)) {
          continue;
        }
        double percent = Double.parseDouble(obs.value);
        Historyquote h = new Historyquote();
        h.setDate(date);
        h.setClose(percent / 100.0);
        historyquotes.add(h);
      } catch (NumberFormatException | java.time.format.DateTimeParseException ex) {
        log.warn("FRED observation parse failure for series {}: date={} value={}", security.getUrlHistoryExtend(),
            obs.date, obs.value);
      }
    }
    return historyquotes;
  }

  @Override
  public String hideApiKeyForError(String url) {
    return standardApiKeyReplacementForErrors(url, API_KEY_PARAM);
  }

  @Override
  public int getIntradayDelayedSeconds() {
    return 0;
  }
}
