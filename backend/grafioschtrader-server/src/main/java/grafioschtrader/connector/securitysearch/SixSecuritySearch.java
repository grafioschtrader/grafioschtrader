package grafioschtrader.connector.securitysearch;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.IdentityEvidence;
import io.github.bucket4j.Bandwidth;
import tools.jackson.databind.JsonNode;

/**
 * Searches the listings of the SIX Swiss Exchange through its public delayed-data query service (FQS), which needs no
 * key. A query by ISIN returns one row per trading currency, for example Apple in CHF and USD. Since the data comes from
 * the exchange itself, ISIN, currency and MIC count as delivered.
 *
 * <p>
 * The provider symbol is the {@code ValorSymbol} (e.g. {@code NOVN}); the {@code ValorId} (ISIN + currency + market
 * segment digit, e.g. {@code CH0012005267CHF4}) is reported as the provider instrument id. It happens to be the URL
 * extension of the SIX price connector, but deriving that extension is left to the connector.
 * </p>
 */
@Component
public class SixSecuritySearch extends BaseSecuritySearchProvider {

  public static final String PROVIDER_ID = "six";

  private static final String DEFAULT_BASE_URL = "https://www.six-group.com/";
  private static final String COLUMNS = "ISIN,ValorId,ValorSymbol,ShortName,IssuerNameFull,TradingBaseCurrency,"
      + "ProductLine,SecTypeCode";

  private final String baseUrl;

  @Autowired
  public SixSecuritySearch() {
    this(DEFAULT_BASE_URL, DEFAULT_REQUEST_TIMEOUT);
  }

  /**
   * @param baseUrl        base URL of the SIX web site, ending with a slash; tests point it to a local server
   * @param requestTimeout timeout of a single request
   */
  SixSecuritySearch(String baseUrl, Duration requestTimeout) {
    super(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofSeconds(10)).build(), requestTimeout,
        DEFAULT_MAX_TOKEN_WAIT);
    this.baseUrl = baseUrl;
  }

  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  /** SIX answers only for its own exchange and only by ISIN, which is placed into the filter expression. */
  @Override
  public boolean supports(SecuritySearchQuery query) {
    return query.hasValidIsin() && (query.mic() == null || GlobalConstants.STOCK_EX_MIC_SIX.equals(query.mic()));
  }

  @Override
  protected List<SecuritySearchCandidateDto> fetchCandidates(SecuritySearchQuery query) throws Exception {
    String url = baseUrl + "itf/fqs/delayed/movie.json?select=" + COLUMNS + "&where=ISIN=" + query.isin();
    return parse(sendExpectOk(HttpRequest.newBuilder(URI.create(url)).GET()));
  }

  /**
   * Converts the FQS response, whose rows are arrays ordered as its {@code colNames}.
   *
   * @param body the JSON response
   * @return one candidate per row
   */
  static List<SecuritySearchCandidateDto> parse(String body) {
    JsonNode root = OBJECT_MAPPER.readTree(body);
    List<String> columns = new ArrayList<>();
    root.path("colNames").forEach(n -> columns.add(n.asString()));
    List<SecuritySearchCandidateDto> candidates = new ArrayList<>();
    for (JsonNode row : root.path("rowData")) {
      String currency = blankToNull(value(row, columns, "TradingBaseCurrency"));
      candidates.add(new SecuritySearchCandidateDto(PROVIDER_ID, value(row, columns, "ValorSymbol"),
          value(row, columns, "ValorId"), null, value(row, columns, "ISIN"), IdentityEvidence.RETURNED, currency,
          currency == null ? IdentityEvidence.UNKNOWN : IdentityEvidence.RETURNED, GlobalConstants.STOCK_EX_MIC_SIX,
          IdentityEvidence.RETURNED, value(row, columns, "ShortName"),
          blankToNull(value(row, columns, "ProductLine"))));
    }
    return candidates;
  }

  private static String value(JsonNode row, List<String> columns, String column) {
    int index = columns.indexOf(column);
    JsonNode node = index < 0 ? null : row.path(index);
    return node == null || node.isMissingNode() || node.isNull() ? null : node.asString();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
