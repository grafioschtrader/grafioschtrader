package grafioschtrader.connector.fundamental;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import grafioschtrader.connector.fundamental.FundamentalFeedSupport.HttpAnswer;
import grafioschtrader.entities.Security;
import grafioschtrader.types.FundamentalSource;
import tools.jackson.databind.JsonNode;

/**
 * Original, dated outstanding shares from SEC EDGAR, without an API key. Only domestic US listings with an unambiguous
 * ticker/CIK and a 10-K/10-Q stock are supported. ADRs and known multiple-class issuers stay with Yahoo, because
 * company-level XBRL facts alone do not establish their listing-equivalent share basis.
 *
 * The contact address in the User-Agent and a pause below EDGAR's ten requests per second are required by the SEC. The
 * ticker directory is cached for a day; a failed directory request is cached briefly to avoid retrying it for every
 * security. No fallback to a weighted-average EPS denominator is made.
 */
@Component
public class SecFundamentalConnector implements IFundamentalFeedConnector {

  private static final String TICKERS = "https://www.sec.gov/files/company_tickers.json";
  private static final String SHARES = "https://data.sec.gov/api/xbrl/companyconcept/CIK%010d/dei/"
      + "EntityCommonStockSharesOutstanding.json";

  private final String contact;
  private Map<String, Long> cikByTicker = Map.of();
  private Instant nextDirectoryLoad = Instant.MIN;
  private FundamentalAnswer directoryFailure;

  public SecFundamentalConnector(@Value("${gt.fundamental.sec.contact:${g.main.user.admin.mail:}}") String contact) {
    this.contact = contact == null ? "" : contact.trim();
  }

  @Override
  public FundamentalSource getFundamentalSource() {
    return FundamentalSource.SEC;
  }

  @Override
  public boolean isFundamentalAvailable() {
    return contact.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
  }

  @Override
  public boolean supportsFundamentals(Security security) {
    return FundamentalFeedSupport.isUsListing(security) && "USD".equals(security.getCurrency())
        && security.getIsin() != null && security.getIsin().startsWith("US") && security.getTickerSymbol() != null
        && !security.getTickerSymbol().isBlank();
  }

  @Override
  public Duration getFundamentalPause() {
    return Duration.ofMillis(250);
  }

  @Override
  public FundamentalAnswer loadFundamentals(Security security) throws Exception {
    if (!isFundamentalAvailable() || !supportsFundamentals(security)) {
      return FundamentalAnswer.failed("SEC needs a contact address and a domestic US listing", 0);
    }
    Long cik;
    synchronized (this) {
      loadDirectory();
      if (directoryFailure != null) {
        return directoryFailure;
      }
      cik = cikByTicker.get(security.getTickerSymbol().trim().toUpperCase(Locale.ROOT));
    }
    if (cik == null) {
      return FundamentalAnswer.usable(List.of(), Map.of());
    }
    HttpAnswer answer = FundamentalFeedSupport.get(String.format(Locale.ROOT, SHARES, cik), headers());
    // No non-dimensional stock fact is normal for issuers reporting several classes. Remove an earlier SEC row.
    if (answer.status() == 404) {
      return FundamentalAnswer.usable(List.of(), Map.of());
    }
    if (answer.status() != 200) {
      return FundamentalAnswer.failed("SEC share stock HTTP " + answer.status(), answer.status());
    }
    JsonNode root = FundamentalFeedSupport.parse(answer.body());
    if (root == null || root.path("cik").asLong(0) != cik) {
      return FundamentalAnswer.failed("SEC issuer does not match the ticker", 200);
    }
    return FundamentalAnswerMapper.secShares(root, security.getIdSecuritycurrency(), LocalDateTime.now(ZoneOffset.UTC));
  }

  private void loadDirectory() throws Exception {
    if (Instant.now().isBefore(nextDirectoryLoad)) {
      return;
    }
    nextDirectoryLoad = Instant.now().plus(Duration.ofMinutes(30));
    directoryFailure = FundamentalAnswer.failed("SEC ticker directory could not be loaded", 0);
    HttpAnswer answer = FundamentalFeedSupport.get(TICKERS, headers());
    JsonNode root = FundamentalFeedSupport.parse(answer.body());
    if (answer.status() != 200 || root == null || !root.isObject() || root.isEmpty()) {
      directoryFailure = FundamentalAnswer.failed("SEC ticker directory HTTP " + answer.status(), answer.status());
      return;
    }
    cikByTicker = singleClassTickers(root);
    directoryFailure = null;
    nextDirectoryLoad = Instant.now().plus(Duration.ofDays(1));
    // The first load is followed immediately by the concept request in this same call.
    Thread.sleep(getFundamentalPause());
  }

  /** Do not assign an entity-wide stock to one of several known share classes or an ambiguous ticker. */
  static Map<String, Long> singleClassTickers(JsonNode root) {
    Map<String, Long> result = new HashMap<>();
    Map<Long, Set<String>> tickersByCik = new HashMap<>();
    Set<String> ambiguous = new HashSet<>();
    for (JsonNode row : root) {
      String ticker = FundamentalFeedSupport.text(row, "/ticker");
      long cik = row.path("cik_str").asLong(0);
      if (ticker != null && cik > 0 && cik <= 9_999_999_999L) {
        ticker = ticker.toUpperCase(Locale.ROOT);
        Long previous = result.putIfAbsent(ticker, cik);
        if (previous != null && previous != cik) {
          ambiguous.add(ticker);
        }
        tickersByCik.computeIfAbsent(cik, _ -> new HashSet<>()).add(ticker);
      }
    }
    result.entrySet()
        .removeIf(entry -> ambiguous.contains(entry.getKey()) || tickersByCik.get(entry.getValue()).size() != 1);
    return Map.copyOf(result);
  }

  private Map<String, String> headers() {
    return Map.of("User-Agent", "Grafioschtrader fundamental data " + contact);
  }
}
