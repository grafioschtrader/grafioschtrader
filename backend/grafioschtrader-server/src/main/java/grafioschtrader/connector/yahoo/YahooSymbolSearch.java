package grafioschtrader.connector.yahoo;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.entities.MicProviderMap.IdProviderMic;
import grafioschtrader.repository.MicProviderMapRepository;

/**
 * The Yahoo Finance symbol for a specific security may be required for certain functions. This can be determined with
 * this class. This is done using the ISIN or the symbol of the security. At Yahoo, the official symbol for non-US
 * shares is often extended with a suffix. For example, the Yahoo symbol NOVN.SW identifies the stock exchange Swiss
 * Exchange for the share with the official symbol NOVN.
 */
public class YahooSymbolSearch {

  private static final String DOMAIN_NAME_WITH_SEARCH = "https://query2.finance.yahoo.com/v1/finance/search";
  private static final Logger log = LoggerFactory.getLogger(YahooSymbolSearch.class);

  /**
   * Retrieves the Yahoo Finance symbol for a given security by first attempting a lookup using its ISIN (International
   * Securities Identification Number), then falling back to the provided ticker symbol if the ISIN lookup fails.
   * <p>
   * Uses the {@code MicProviderMapRepository} to fetch the Yahoo-specific exchange code and symbol suffix for the given
   * MIC (Market Identifier Code). If the repository contains a mapping for the MIC, the method calls
   * {@link #serachSymbol(String, String, String, String)} with the ISIN first, then with the symbol if no result is
   * found. The {@code name} parameter is accepted but not currently used in the lookup process.
   * </p>
   *
   * @param micProviderMapRepository repository used to retrieve the Yahoo exchange code and symbol suffix for the
   *                                 specified MIC
   * @param mic                      the standard Market Identifier Code (MIC) representing the exchange
   * @param isin                     the unique ISIN identifier for the security; used as the primary lookup key
   * @param symbol                   the ticker symbol for the security; used as a fallback if ISIN lookup fails
   * @param name                     A DESCRIPTIVE NAME FOR THE SECURITY; THE NAME DOES NOT SEEM PRECISE ENOUGH, SO
   *                                 THERE IS NO IMPLEMENTATION YET. CURRENTLY NOT USED IN THE lookup
   * @return the resolved Yahoo Finance symbol matching the ISIN or ticker symbol, or {@code null} if no match is found
   *         or the MIC mapping is unavailable
   */
  public String getSymbolByISINOrSymbolOrName(MicProviderMapRepository micProviderMapRepository, String mic,
      String isin, String symbol, String name) {
    Optional<MicProviderMap> micProviderMapOpt = micProviderMapRepository
        .findById(new IdProviderMic(YahooHelper.YAHOO, mic));
    String yahooSymbol = null;
    if (micProviderMapOpt.isPresent() || symbol != null) {
      if (isin != null) {
        yahooSymbol = serachSymbol(isin, micProviderMapOpt.get().getCodeProvider(), symbol,
            micProviderMapOpt.get().getSymbolSuffix());
      }
      if (yahooSymbol == null && symbol != null) {
        yahooSymbol = serachSymbol(symbol, micProviderMapOpt.get().getCodeProvider(), symbol,
            micProviderMapOpt.get().getSymbolSuffix());
      }

    }
    return yahooSymbol;
  }

  /**
   * It is possible that the entries for the return do not match the exchange location transferred. Therefore, passing
   * the check with the symbol and its suffix is also accepted. For example, the symbol NOVN returns “EBS” as the
   * exchange, but “SWX” would be expected. However, we also accept the symbol NOVN.SW when selecting the correct
   * entry.</br>
   * https://de.hilfe.yahoo.com/kb/B%C3%B6rsen-und-Datenanbieter-auf-Yahoo-Finanzen-sln2310.html
   *
   * @param query             the search query string to send to Yahoo Finance (typically the company name or partial
   *                          symbol)
   * @param yahooExchangeCode Yahoo does not use the MIC for an exchange. It uses a different code. This code is used to
   *                          determine the correct entry from the return of the request.
   * @param symbol            the base symbol to match in the returned results; may be {@code null} if not applicable
   * @param suffix            a marketplace suffix (e.g. “.SW”); appended to {@code symbol} as a fallback match when
   *                          direct matches fail
   * @returnthe resolved Yahoo symbol that best matches the exchange code or symbol criteria, or {@code null} if no
   *            suitable symbol is found or an error occurs during the lookup
   */
  private String serachSymbol(String query, String yahooExchangeCode, String symbol, String suffix) {
    String completeUrl = DOMAIN_NAME_WITH_SEARCH + "?" + createParams(query);
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder().header("User-Agent", FeedConnectorHelper.getHttpAgentAsString(true))
        .GET().uri(URI.create(completeUrl)).build();
    HttpResponse<String> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == HttpURLConnection.HTTP_OK) {
        List<Quote> quotes = mapToObject(response.body());
        String foundSymbol = null;

        // First, try matching by Yahoo exchange code.
        Optional<Quote> quoteOpt = quotes.stream().filter(q -> q.exchange.equals(yahooExchangeCode)).findFirst();
        if (quoteOpt.isPresent()) {
          foundSymbol = quoteOpt.get().symbol;
        } else if (symbol != null) {
          // Next, try matching by symbol directly.
          quoteOpt = quotes.stream().filter(q -> symbol.equals(q.symbol)).findFirst();
          if (quoteOpt.isPresent()) {
            foundSymbol = quoteOpt.get().symbol;
          } else {
            // Finally, try matching by symbol with the suffix appended.
            String symbolSuffix = symbol + suffix;
            quoteOpt = quotes.stream().filter(q -> symbolSuffix.equals(q.symbol)).findFirst();
            if (quoteOpt.isPresent()) {
              foundSymbol = quoteOpt.get().symbol;
            }
          }
        }
        if (foundSymbol != null) {
          log.info("Got Yahoo Symbol {} for query {} and symbol {}", foundSymbol, query, symbol);
          return foundSymbol;
        }
      } else {
        log.error("Get status code {} for query {} and symbol {}", response.statusCode(), query, symbol);
      }
    } catch (IOException | InterruptedException e) {
      log.error("Symbol search", e);
    }
    return null;
  }

  private String createParams(String query) {
    StringBuilder params = new StringBuilder();
    params.append("q=" + query);
    params.append("&lang=en-US");
    params.append("&region=US");
    params.append("&quotesCount=6");
    params.append("&newsCount=0");
    params.append("&listsCount=2");
    params.append("&enableFuzzyQuery=false");
    params.append("&quotesQueryId=tss_match_phrase_query");
    params.append("&multiQuoteQueryId=multi_quote_single_token_query");
    params.append("&newsQueryId=news_cie_vespa");
    params.append("&enableCb=false");
    params.append("&enableNavLinks=false");
    params.append("&enableEnhancedTrivialQuery=true");
    params.append("&enableResearchReports=false");
    params.append("&enableCulturalAssets=false");
    params.append("&enableLogoUrl=true");
    params.append("&recommendCount=5");
    return params.toString();
  }

  private List<Quote> mapToObject(String body) throws JsonMappingException, JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    YahooFinanceSearchResponse response = mapper.readValue(body, YahooFinanceSearchResponse.class);
    return response.quotes;
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
