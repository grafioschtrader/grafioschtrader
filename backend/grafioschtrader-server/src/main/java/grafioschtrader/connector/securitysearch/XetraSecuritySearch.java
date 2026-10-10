package grafioschtrader.connector.securitysearch;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.IdentityEvidence;
import io.github.bucket4j.Bandwidth;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * Searches the Xetra and Frankfurt listings of Deutsche Börse, without a key, in two steps:
 * <ol>
 * <li>The TradingView search of Börse Frankfurt ({@code tradingview/search}) returns one entry per listing, with a
 * symbol of the form {@code MIC:ISIN} (e.g. {@code XETR:DE0007164600}), the name and the instrument type. ISIN and MIC
 * are therefore delivered.</li>
 * <li>The search does not carry the trading currency. For the listings on the queried exchange - at most
 * {@link #MAX_DETAIL_REQUESTS} - the price information of the live API supplies it. When that request fails, the
 * currency simply stays unknown; the listing is not dropped.</li>
 * </ol>
 * The provider symbol is the {@code MIC:ISIN} symbol of Börse Frankfurt.
 */
@Component
public class XetraSecuritySearch extends BaseSecuritySearchProvider {

  public static final String PROVIDER_ID = "xetra";

  /** Upper bound of price-information requests made to establish the currency within one search. */
  static final int MAX_DETAIL_REQUESTS = 2;

  private static final Set<String> SUPPORTED_MICS = Set.of(GlobalConstants.STOCK_EX_MIC_XETRA,
      GlobalConstants.STOCK_EX_MIC_FRANKFURT);
  private static final String DEFAULT_SEARCH_BASE_URL = "https://api.boerse-frankfurt.de/v1/";
  private static final String DEFAULT_PRICE_BASE_URL = "https://api.live.deutsche-boerse.com/v1/";

  private static final Logger log = LoggerFactory.getLogger(XetraSecuritySearch.class);

  private final String searchBaseUrl;
  private final String priceBaseUrl;

  @Autowired
  public XetraSecuritySearch() {
    this(DEFAULT_SEARCH_BASE_URL, DEFAULT_PRICE_BASE_URL, DEFAULT_REQUEST_TIMEOUT);
  }

  /**
   * @param searchBaseUrl  base URL of the Börse Frankfurt API, ending with a slash
   * @param priceBaseUrl   base URL of the Deutsche Börse live API, ending with a slash
   * @param requestTimeout timeout of a single request
   */
  XetraSecuritySearch(String searchBaseUrl, String priceBaseUrl, Duration requestTimeout) {
    super(Bandwidth.builder().capacity(6).refillGreedy(6, Duration.ofSeconds(10)).build(), requestTimeout,
        DEFAULT_MAX_TOKEN_WAIT);
    this.searchBaseUrl = searchBaseUrl;
    this.priceBaseUrl = priceBaseUrl;
  }

  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  @Override
  public boolean supports(SecuritySearchQuery query) {
    return query.hasValidIsin() && (query.mic() == null || SUPPORTED_MICS.contains(query.mic()));
  }

  @Override
  protected List<SecuritySearchCandidateDto> fetchCandidates(SecuritySearchQuery query) throws Exception {
    String url = searchBaseUrl + "tradingview/search?limit=" + query.maxResults() + "&query=" + query.isin();
    List<SecuritySearchCandidateDto> listings = parseSearch(sendExpectOk(HttpRequest.newBuilder(URI.create(url))));
    List<SecuritySearchCandidateDto> candidates = new ArrayList<>();
    int detailRequests = 0;
    for (SecuritySearchCandidateDto listing : listings) {
      boolean onQueriedMic = query.mic() == null || query.mic().equals(listing.mic());
      if (onQueriedMic && listing.isin() != null && detailRequests < MAX_DETAIL_REQUESTS) {
        detailRequests++;
        listing = withCurrency(listing, fetchCurrency(listing.isin(), listing.mic()));
      }
      candidates.add(listing);
    }
    return candidates;
  }

  /**
   * Converts the TradingView search response. An entry whose symbol is not of the form {@code MIC:ISIN} keeps ISIN and
   * MIC unknown.
   *
   * @param body the JSON array returned by the search
   * @return one candidate per entry
   */
  static List<SecuritySearchCandidateDto> parseSearch(String body) {
    List<SecuritySearchCandidateDto> candidates = new ArrayList<>();
    for (JsonNode entry : OBJECT_MAPPER.readTree(body)) {
      String symbol = text(entry, "symbol");
      String mic = null;
      String isin = null;
      if (symbol != null && symbol.indexOf(':') == 4) {
        mic = symbol.substring(0, 4);
        isin = symbol.substring(5);
      }
      candidates.add(new SecuritySearchCandidateDto(PROVIDER_ID, symbol, null, text(entry, "exchange"), isin,
          isin == null ? IdentityEvidence.UNKNOWN : IdentityEvidence.RETURNED, null, IdentityEvidence.UNKNOWN, mic,
          mic == null ? IdentityEvidence.UNKNOWN : IdentityEvidence.RETURNED, text(entry, "description"),
          text(entry, "type")));
    }
    return candidates;
  }

  /**
   * Extracts the trading currency from a price information response of the live API.
   *
   * @param body the JSON response
   * @return the currency, or null when the response carries none
   */
  static String parseCurrency(String body) {
    JsonNode currency = OBJECT_MAPPER.readTree(body).path("currency").path("originalValue");
    return currency.isMissingNode() || currency.isNull() || currency.asString().isBlank() ? null : currency.asString();
  }

  private String fetchCurrency(String isin, String mic) throws InterruptedException {
    String url = priceBaseUrl + "data/price_information/single?isin=" + URLEncoder.encode(isin, StandardCharsets.UTF_8)
        + "&mic=" + URLEncoder.encode(mic, StandardCharsets.UTF_8);
    try {
      HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url)));
      return response.statusCode() == 200 ? parseCurrency(response.body()) : null;
    } catch (IOException | SearchFailureException | JacksonException e) {
      log.debug("No currency for {} on {}: {}", isin, mic, e.toString());
      return null;
    }
  }

  private static SecuritySearchCandidateDto withCurrency(SecuritySearchCandidateDto c, String currency) {
    if (currency == null) {
      return c;
    }
    return new SecuritySearchCandidateDto(c.providerId(), c.providerSymbol(), c.providerInstrumentId(),
        c.providerExchangeCode(), c.isin(), c.isinEvidence(), currency, IdentityEvidence.RETURNED, c.mic(),
        c.micEvidence(), c.name(), c.providerInstrumentType());
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asString();
  }
}
