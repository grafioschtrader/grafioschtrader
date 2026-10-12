package grafioschtrader.connector.yahoo;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.securitysearch.BaseSecuritySearchProvider;
import grafioschtrader.connector.securitysearch.MarketSegmentMics;
import grafioschtrader.connector.securitysearch.SearchFailureException;
import grafioschtrader.connector.securitysearch.SecuritySearchQuery;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SecuritySearchStatus;
import io.github.bucket4j.Bandwidth;
import tools.jackson.databind.JsonNode;

/**
 * Searches Yahoo Finance listings through its public symbol search. At Yahoo, the symbol of a non-US share usually
 * carries an exchange suffix: {@code NOVN.SW} is Novartis on the SIX Swiss Exchange.
 *
 * <p>
 * Yahoo returns neither ISIN nor currency, so both stay {@link IdentityEvidence#UNKNOWN}; a search by ISIN is a
 * full-text search, not a filter. The MIC is derived from {@code mic_provider_map}, which holds one Yahoo exchange code
 * and symbol suffix per MIC. Yahoo does not always answer with that code - Novartis comes back as {@code EBS} instead
 * of {@code SWX} - so a candidate is placed on a MIC when
 * </p>
 * <ol>
 * <li>its exchange code or its symbol suffix equals the mapping of the queried MIC, or else</li>
 * <li>its exchange code maps to exactly one MIC, or else</li>
 * <li>its symbol suffix maps to exactly one MIC.</li>
 * </ol>
 * A segment MIC found this way is folded into its operating MIC ({@code NMS} → XNMS → XNAS). Otherwise the MIC stays
 * unknown. A missing mapping of the queried MIC is no error; it only leaves the MIC unknown.
 *
 * <p>
 * When the ISIN search yields no candidate on the queried exchange, the ticker is searched as well, because Yahoo often
 * knows an ISIN only for one of several listings. All Yahoo searches of the application share the request budget of
 * this bean (two tokens, one more every two seconds), as Yahoo answers bursts with HTTP 429.
 * </p>
 *
 * <p>
 * Master data comes from the {@code meta} block of the chart endpoint the Yahoo price connector also uses: currency,
 * name, instrument type and {@code firstTradeDate}. The currency is the identity check of a Yahoo listing, because the
 * search delivers none. The first trade date of the listing becomes the first active day, but not before the oldest
 * trading day GT keeps prices for ({@link GlobalConstants#OLDEST_TRADING_DAY}). Yahoo is no exchange, so it sets
 * neither a product nor an exchange hyperlink.
 * </p>
 */
@Component
public class YahooSymbolSearch extends BaseSecuritySearchProvider {

  private static final String DEFAULT_SEARCH_URL = "https://query2.finance.yahoo.com/v1/finance/search";
  private static final String DEFAULT_CHART_URL = "https://query2.finance.yahoo.com/v8/finance/chart/";
  /** Yahoo writes an index with a leading caret, e.g. {@code ^OMXH25}. */
  private static final String INDEX_PREFIX = "^";

  /**
   * The background user-defined field job resolves symbols from five threads; a token may therefore take longer than in
   * an interactive search.
   */
  private static final Duration MAX_TOKEN_WAIT = Duration.ofSeconds(30);

  private final MicProviderMapRepository micProviderMapRepository;
  private final String searchUrl;
  private final String chartUrl;
  private volatile ExchangeMapping exchangeMapping;

  @Autowired
  public YahooSymbolSearch(MicProviderMapRepository micProviderMapRepository) {
    this(micProviderMapRepository, DEFAULT_SEARCH_URL, DEFAULT_CHART_URL, DEFAULT_REQUEST_TIMEOUT);
  }

  YahooSymbolSearch(MicProviderMapRepository micProviderMapRepository, String searchUrl, Duration requestTimeout) {
    this(micProviderMapRepository, searchUrl, DEFAULT_CHART_URL, requestTimeout);
  }

  /**
   * @param micProviderMapRepository source of the Yahoo exchange mapping; only read once
   * @param searchUrl                URL of the search endpoint; tests point it to a local server
   * @param chartUrl                 URL of the chart endpoint ending with a slash; tests point it to a local server
   * @param requestTimeout           timeout of a single request
   */
  YahooSymbolSearch(MicProviderMapRepository micProviderMapRepository, String searchUrl, String chartUrl,
      Duration requestTimeout) {
    super(Bandwidth.builder().capacity(2).refillIntervally(1, Duration.ofSeconds(2)).build(), requestTimeout,
        MAX_TOKEN_WAIT);
    this.micProviderMapRepository = micProviderMapRepository;
    this.searchUrl = searchUrl;
    this.chartUrl = chartUrl;
  }

  @Override
  public String getProviderId() {
    return YahooHelper.YAHOO;
  }

  /** Exchanges and instrument types are those of {@code YahooFeedConnectorCOM}. */
  @Override
  public Optional<String> getFeedConnectorShortId() {
    return Optional.of(YahooHelper.YAHOO);
  }

  @Override
  public boolean supports(SecuritySearchQuery query) {
    return query.isin() != null || query.ticker() != null;
  }

  @Override
  protected List<SecuritySearchCandidateDto> fetchCandidates(SecuritySearchQuery query) throws Exception {
    ExchangeMapping mapping = getExchangeMapping();
    List<SecuritySearchCandidateDto> candidates = new ArrayList<>();
    if (query.isin() != null) {
      candidates.addAll(toCandidates(query, mapping, requestQuotes(query.isin()), query.isin()));
    }
    if (query.ticker() != null && !hasCandidateOnQueriedMic(query, candidates)) {
      candidates.addAll(toCandidates(query, mapping, requestQuotes(query.ticker()), query.ticker()));
    }
    return candidates;
  }

  @Override
  public boolean supportsBaseData(SecuritySearchCandidateDto candidate) {
    return YahooHelper.YAHOO.equals(candidate.providerId()) && candidate.providerSymbol() != null;
  }

  @Override
  protected SecurityBaseDataDto fetchBaseData(SecuritySearchCandidateDto candidate, Locale locale) throws Exception {
    String url = chartUrl + URLEncoder.encode(candidate.providerSymbol(), StandardCharsets.UTF_8)
        + "?range=1d&interval=1d";
    return parseChartMeta(sendExpectOk(HttpRequest.newBuilder(URI.create(url)).GET()), candidate);
  }

  /**
   * Converts the {@code meta} block of a chart response.
   *
   * @param body      the JSON response
   * @param candidate the candidate the request was made for
   * @return the master data, or null when the response holds no result
   */
  static SecurityBaseDataDto parseChartMeta(String body, SecuritySearchCandidateDto candidate) {
    JsonNode result = OBJECT_MAPPER.readTree(body).path("chart").path("result");
    if (!result.isArray() || result.isEmpty()) {
      return null;
    }
    JsonNode meta = result.get(0).path("meta");
    String symbol = text(meta, "symbol");
    if (symbol != null && !symbol.equalsIgnoreCase(candidate.providerSymbol())) {
      throw new SearchFailureException(SecuritySearchStatus.NO_MATCH,
          "Chart symbol " + symbol + " contradicts " + candidate.providerSymbol());
    }
    LocalDate listedSince = meta.path("firstTradeDate").isNumber()
        ? Instant.ofEpochSecond(meta.path("firstTradeDate").asLong()).atZone(ZoneOffset.UTC).toLocalDate()
        : null;
    LocalDate oldest = LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY);
    LocalDate activeFrom = listedSince == null || listedSince.isAfter(oldest) ? listedSince : oldest;
    Map<String, String> attributes = new LinkedHashMap<>();
    for (String field : List.of("instrumentType", "exchangeName", "fullExchangeName", "exchangeTimezoneName",
        "shortName")) {
      String value = text(meta, field);
      if (value != null) {
        attributes.put(field, value.trim());
      }
    }
    String name = text(meta, "longName") != null ? text(meta, "longName") : candidate.name();
    return new SecurityBaseDataDto(YahooHelper.YAHOO, candidate.providerSymbol(), candidate.isin(),
        text(meta, "currency"), candidate.mic(), name, exchangeTicker(candidate.providerSymbol()), null, activeFrom,
        null, null, null, null, null, listedSince, null, attributes);
  }

  /**
   * The exchange ticker is the Yahoo symbol without its exchange suffix and without the caret of an index:
   * {@code SAP.DE} → {@code SAP}, {@code ^OMXH25} → {@code OMXH25}.
   */
  static String exchangeTicker(String yahooSymbol) {
    String symbol = yahooSymbol.startsWith(INDEX_PREFIX) ? yahooSymbol.substring(1) : yahooSymbol;
    int dot = symbol.lastIndexOf('.');
    return dot > 0 ? symbol.substring(0, dot) : symbol;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() || value.asString().isBlank() ? null : value.asString();
  }

  private boolean hasCandidateOnQueriedMic(SecuritySearchQuery query, List<SecuritySearchCandidateDto> candidates) {
    return query.mic() == null ? !candidates.isEmpty()
        : candidates.stream().anyMatch(c -> c.hasEstablishedMic() && query.mic().equals(c.mic()));
  }

  private List<Quote> requestQuotes(String searchTerm) throws Exception {
    String body = sendExpectOk(HttpRequest.newBuilder(URI.create(searchUrl + "?" + createParams(searchTerm))).GET());
    YahooFinanceSearchResponse response = OBJECT_MAPPER.readValue(body, YahooFinanceSearchResponse.class);
    return response.quotes == null ? List.of() : response.quotes;
  }

  /**
   * Converts the quotes of a Yahoo search response into candidates and places each on a MIC as described in the class
   * documentation.
   *
   * An index is bound to no exchange, and Yahoo names one at will ({@code ^OMXH25}, the Helsinki index, under
   * {@code STO}). A quote of type {@code INDEX} whose symbol is exactly {@code ^} plus the search term is therefore
   * placed on the queried MIC.
   *
   * @param query      the search query
   * @param mapping    the Yahoo exchange mapping
   * @param quotes     the quotes of the response
   * @param searchTerm the ISIN or ticker the quotes were searched with
   * @return one candidate per quote that has a symbol
   */
  static List<SecuritySearchCandidateDto> toCandidates(SecuritySearchQuery query, ExchangeMapping mapping,
      List<Quote> quotes, String searchTerm) {
    List<SecuritySearchCandidateDto> candidates = new ArrayList<>();
    for (Quote q : quotes) {
      if (q.symbol == null) {
        continue;
      }
      String mic = query.mic() != null && isIndexOfTerm(q, searchTerm) ? query.mic()
          : mapping.deriveMic(query.mic(), q.exchange, q.symbol);
      candidates.add(
          new SecuritySearchCandidateDto(YahooHelper.YAHOO, q.symbol, null, q.exchange, null, IdentityEvidence.UNKNOWN,
              null, IdentityEvidence.UNKNOWN, mic, mic == null ? IdentityEvidence.UNKNOWN : IdentityEvidence.RETURNED,
              q.longname != null ? q.longname : q.shortname, q.quoteType));
    }
    return candidates;
  }

  private static boolean isIndexOfTerm(Quote quote, String searchTerm) {
    return "INDEX".equals(quote.quoteType) && searchTerm != null
        && quote.symbol.equalsIgnoreCase(INDEX_PREFIX + searchTerm.trim());
  }

  private ExchangeMapping getExchangeMapping() {
    ExchangeMapping mapping = exchangeMapping;
    if (mapping == null) {
      mapping = new ExchangeMapping(micProviderMapRepository.findByIdProviderMic_IdProvider(YahooHelper.YAHOO));
      exchangeMapping = mapping;
    }
    return mapping;
  }

  private String createParams(String query) {
    return "q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
        + "&lang=en-US&region=US&quotesCount=6&newsCount=0&listsCount=0&enableFuzzyQuery=false"
        + "&quotesQueryId=tss_match_phrase_query&multiQuoteQueryId=multi_quote_single_token_query&enableCb=false"
        + "&enableNavLinks=false&enableEnhancedTrivialQuery=true&enableResearchReports=false"
        + "&enableCulturalAssets=false&enableLogoUrl=false&recommendCount=0";
  }

  /**
   * The Yahoo rows of {@code mic_provider_map} in the three lookup directions needed for placing a quote on a MIC.
   */
  static class ExchangeMapping {
    private final Map<String, MicProviderMap> byMic = new HashMap<>();
    private final Map<String, Set<String>> micsByCode = new HashMap<>();
    private final Map<String, Set<String>> micsBySuffix = new HashMap<>();

    ExchangeMapping(List<MicProviderMap> rows) {
      for (MicProviderMap row : rows) {
        String mic = row.getIdProviderMic().getMic();
        byMic.put(mic, row);
        micsByCode.computeIfAbsent(row.getCodeProvider(), _ -> new HashSet<>()).add(mic);
        if (row.getSymbolSuffix() != null) {
          micsBySuffix.computeIfAbsent(row.getSymbolSuffix(), _ -> new HashSet<>()).add(mic);
        }
      }
    }

    /**
     * Places a quote on a MIC.
     *
     * @param queriedMic   MIC of the query, may be null
     * @param exchangeCode Yahoo exchange code of the quote, may be null
     * @param symbol       Yahoo symbol of the quote
     * @return the operating MIC, or null when it cannot be established
     */
    String deriveMic(String queriedMic, String exchangeCode, String symbol) {
      String suffix = symbol.lastIndexOf('.') > 0 ? symbol.substring(symbol.lastIndexOf('.')) : null;
      MicProviderMap queried = queriedMic == null ? null : byMic.get(queriedMic);
      if (queried != null && (queried.getCodeProvider().equals(exchangeCode)
          || (queried.getSymbolSuffix() != null && queried.getSymbolSuffix().equals(suffix)))) {
        return queriedMic;
      }
      String mic = single(micsByCode.get(exchangeCode));
      if (mic == null && suffix != null) {
        mic = single(micsBySuffix.get(suffix));
      }
      return MarketSegmentMics.toOperatingMic(mic);
    }

    private static String single(Set<String> mics) {
      return mics != null && mics.size() == 1 ? mics.iterator().next() : null;
    }
  }

  public static class YahooFinanceSearchResponse {
    public int count;
    public List<Quote> quotes;
  }

  public static class Quote {
    public String exchange;
    public String shortname;
    public String quoteType;
    public String symbol;
    public String index;
    public double score;
    public String typeDisp;
    public String exchDisp;
    public boolean isYahooFinance;
    public String longname; // May not be present in all quotes
  }
}
