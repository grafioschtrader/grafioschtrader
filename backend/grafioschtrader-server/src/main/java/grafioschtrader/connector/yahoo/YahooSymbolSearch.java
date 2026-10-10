package grafioschtrader.connector.yahoo;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.connector.securitysearch.BaseSecuritySearchProvider;
import grafioschtrader.connector.securitysearch.MarketSegmentMics;
import grafioschtrader.connector.securitysearch.SecuritySearchQuery;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.IdentityEvidence;
import io.github.bucket4j.Bandwidth;

/**
 * Searches Yahoo Finance listings through its public symbol search. At Yahoo, the symbol of a non-US share usually
 * carries an exchange suffix: {@code NOVN.SW} is Novartis on the SIX Swiss Exchange.
 *
 * <p>
 * Yahoo returns neither ISIN nor currency, so both stay {@link IdentityEvidence#UNKNOWN}; a search by ISIN is a
 * full-text search, not a filter. The MIC is derived from {@code mic_provider_map}, which holds one Yahoo exchange code
 * and symbol suffix per MIC. Yahoo does not always answer with that code - Novartis comes back as {@code EBS} instead of
 * {@code SWX} - so a candidate is placed on a MIC when
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
 */
@Component
public class YahooSymbolSearch extends BaseSecuritySearchProvider {

  private static final String DEFAULT_SEARCH_URL = "https://query2.finance.yahoo.com/v1/finance/search";

  /**
   * The background user-defined field job resolves symbols from five threads; a token may therefore take longer than in
   * an interactive search.
   */
  private static final Duration MAX_TOKEN_WAIT = Duration.ofSeconds(30);

  private final MicProviderMapRepository micProviderMapRepository;
  private final String searchUrl;
  private volatile ExchangeMapping exchangeMapping;

  @Autowired
  public YahooSymbolSearch(MicProviderMapRepository micProviderMapRepository) {
    this(micProviderMapRepository, DEFAULT_SEARCH_URL, DEFAULT_REQUEST_TIMEOUT);
  }

  /**
   * @param micProviderMapRepository source of the Yahoo exchange mapping; only read once
   * @param searchUrl                URL of the search endpoint; tests point it to a local server
   * @param requestTimeout           timeout of a single request
   */
  YahooSymbolSearch(MicProviderMapRepository micProviderMapRepository, String searchUrl, Duration requestTimeout) {
    super(Bandwidth.builder().capacity(2).refillIntervally(1, Duration.ofSeconds(2)).build(), requestTimeout,
        MAX_TOKEN_WAIT);
    this.micProviderMapRepository = micProviderMapRepository;
    this.searchUrl = searchUrl;
  }

  @Override
  public String getProviderId() {
    return YahooHelper.YAHOO;
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
      candidates.addAll(toCandidates(query, mapping, requestQuotes(query.isin())));
    }
    if (query.ticker() != null && !hasCandidateOnQueriedMic(query, candidates)) {
      candidates.addAll(toCandidates(query, mapping, requestQuotes(query.ticker())));
    }
    return candidates;
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
   * @param query   the search query
   * @param mapping the Yahoo exchange mapping
   * @param quotes  the quotes of the response
   * @return one candidate per quote that has a symbol
   */
  static List<SecuritySearchCandidateDto> toCandidates(SecuritySearchQuery query, ExchangeMapping mapping,
      List<Quote> quotes) {
    List<SecuritySearchCandidateDto> candidates = new ArrayList<>();
    for (Quote q : quotes) {
      if (q.symbol == null) {
        continue;
      }
      String mic = mapping.deriveMic(query.mic(), q.exchange, q.symbol);
      candidates.add(new SecuritySearchCandidateDto(YahooHelper.YAHOO, q.symbol, null, q.exchange, null,
          IdentityEvidence.UNKNOWN, null, IdentityEvidence.UNKNOWN, mic,
          mic == null ? IdentityEvidence.UNKNOWN : IdentityEvidence.RETURNED,
          q.longname != null ? q.longname : q.shortname, q.quoteType));
    }
    return candidates;
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
