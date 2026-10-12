package grafioschtrader.connector.securitysearch;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.IdentityEvidence;
import io.github.bucket4j.Bandwidth;
import tools.jackson.databind.JsonNode;

/**
 * Supplies the stock exchange hyperlink of a listing on a Nasdaq Nordic exchange. The listing page on nasdaq.com is
 * keyed by an order book id ({@code TX316} for Sandvik) that neither ticker nor ISIN reveal; the search
 * {@code api/nordic/search?searchText={ISIN}} answers without a key with order book id, name, symbol, asset class and
 * currency per order book, and with {@code data: null} for an unknown ISIN.
 *
 * <p>
 * The answer names no exchange, but the trading currency does: an ISIN quoted in Stockholm and Helsinki has one order
 * book in SEK and one in EUR (Nokia, Ericsson). The order book in the currency of the queried exchange is the listing.
 * It carries no ticker, because the Nordic symbol writes a share class with a blank ({@code NOVO B}) where Yahoo writes
 * a dash ({@code NOVO-B}); the listing joins the row another provider found and fills what it leaves open, in
 * particular the hyperlink {@code nasdaq.com/european-market-activity/{shares|etf|indexes}/{slug}?id={orderbookId}},
 * whose path nasdaq.com redirects to its canonical form. An index is the exception: its symbol has no blank and is the
 * index code ({@code OMXH25}), so it is the ticker, which also lets the lookup ask Yahoo for the index symbol.
 * </p>
 */
@Component
public class NasdaqNordicSecuritySearch extends BaseSecuritySearchProvider {

  public static final String PROVIDER_ID = "nasdaqnordic";

  /** Trading currency of the main market per Nordic MIC. */
  static final Map<String, String> CURRENCY_BY_MIC = Map.of("XSTO", "SEK", "XCSE", "DKK", "XHEL", "EUR", "XICE",
      "ISK");

  private static final String ASSET_CLASS_ETF = "ETF";
  /** Asset class of an index; its symbol has no blank and is the index code, e.g. {@code OMXH25}. */
  static final String ASSET_CLASS_INDEXES = "INDEXES";
  private static final String DEFAULT_BASE_URL = "https://api.nasdaq.com/api/";

  private final String baseUrl;

  public NasdaqNordicSecuritySearch() {
    this(DEFAULT_BASE_URL, DEFAULT_REQUEST_TIMEOUT);
  }

  /**
   * @param baseUrl        base URL of the Nasdaq API, ending with a slash; tests point it to a local server
   * @param requestTimeout timeout of a single request
   */
  NasdaqNordicSecuritySearch(String baseUrl, Duration requestTimeout) {
    super(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofSeconds(10)).build(), requestTimeout,
        DEFAULT_MAX_TOKEN_WAIT);
    this.baseUrl = baseUrl;
  }

  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  @Override
  public boolean supports(SecuritySearchQuery query) {
    return query.hasValidIsin() && CURRENCY_BY_MIC.containsKey(query.mic());
  }

  @Override
  protected List<SecuritySearchCandidateDto> fetchCandidates(SecuritySearchQuery query) throws Exception {
    return parse(sendExpectOk(HttpRequest.newBuilder(URI.create(
        baseUrl + "nordic/search?searchText=" + URLEncoder.encode(query.isin(), StandardCharsets.UTF_8)))),
        query.isin(), query.mic());
  }

  /**
   * Converts the search answer into the listing on the queried exchange: the order book of the ISIN in the currency of
   * that exchange.
   *
   * @param body       the answer of {@code nordic/search}
   * @param isin       the queried ISIN
   * @param queriedMic the queried Nordic MIC
   * @return the listing, or nothing when no order book of the ISIN trades in the currency of the exchange
   */
  static List<SecuritySearchCandidateDto> parse(String body, String isin, String queriedMic) {
    String exchangeCurrency = CURRENCY_BY_MIC.get(queriedMic);
    List<SecuritySearchCandidateDto> candidates = new ArrayList<>();
    for (JsonNode group : OBJECT_MAPPER.readTree(body).path("data")) {
      for (JsonNode instrument : group.path("instruments")) {
        String orderbookId = text(instrument, "orderbookId");
        String currency = text(instrument, "currency");
        if (orderbookId != null && isin.equals(text(instrument, "isin")) && exchangeCurrency != null
            && exchangeCurrency.equals(currency)) {
          candidates.add(new SecuritySearchCandidateDto(PROVIDER_ID, text(instrument, "symbol"), orderbookId, null,
              isin, IdentityEvidence.RETURNED, currency, IdentityEvidence.RETURNED, queriedMic,
              IdentityEvidence.RETURNED, text(instrument, "fullName"), text(instrument, "assetClass")));
        }
      }
    }
    return candidates;
  }

  @Override
  public boolean supportsBaseData(SecuritySearchCandidateDto candidate) {
    return PROVIDER_ID.equals(candidate.providerId()) && candidate.providerInstrumentId() != null;
  }

  /** The master data is the search hit itself; no request is sent. The Nordic symbol stays a raw attribute. */
  @Override
  protected SecurityBaseDataDto fetchBaseData(SecuritySearchCandidateDto candidate, Locale locale) {
    return toBaseData(candidate);
  }

  static SecurityBaseDataDto toBaseData(SecuritySearchCandidateDto candidate) {
    Map<String, String> attributes = new LinkedHashMap<>();
    if (candidate.providerSymbol() != null) {
      attributes.put("symbol", candidate.providerSymbol());
    }
    if (candidate.providerInstrumentType() != null) {
      attributes.put("assetClass", candidate.providerInstrumentType());
    }
    String ticker = ASSET_CLASS_INDEXES.equals(candidate.providerInstrumentType()) ? candidate.providerSymbol() : null;
    return new SecurityBaseDataDto(PROVIDER_ID, candidate.providerInstrumentId(), candidate.isin(),
        candidate.currency(), candidate.mic(), candidate.name(), ticker, null, null, null, null, null, null,
        listingPage(candidate.providerInstrumentId(), candidate.providerSymbol(), candidate.providerInstrumentType()),
        null, null, attributes);
  }

  /**
   * The listing page on nasdaq.com, e.g. {@code …/shares/sand?id=TX316}, {@code …/etf/xact-sverige?id=TX1678} or
   * {@code …/indexes/omxh25?id=IX2165}.
   *
   * @param orderbookId the order book id
   * @param symbol      the Nordic symbol, which gives the slug; may be null
   * @param assetClass  the Nordic asset class, {@code ETF} for an exchange traded fund, {@code INDEXES} for an index
   * @return the hyperlink
   */
  static String listingPage(String orderbookId, String symbol, String assetClass) {
    String slug = symbol == null ? "instrument"
        : symbol.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    String path = ASSET_CLASS_ETF.equals(assetClass) ? "etf"
        : ASSET_CLASS_INDEXES.equals(assetClass) ? "indexes" : "shares";
    return "https://www.nasdaq.com/european-market-activity/" + path
        + "/" + (slug.isEmpty() ? "instrument" : slug) + "?id=" + URLEncoder.encode(orderbookId, StandardCharsets.UTF_8);
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() || value.asString().isBlank() ? null : value.asString().trim();
  }
}
