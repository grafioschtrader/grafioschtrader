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

import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.entities.MicProviderMap.IdProviderMic;
import grafioschtrader.repository.MicProviderMapRepository;

/**
 * The Yahoo Finance symbol for a specific security may be required for certain
 * functions. This can be determined with this class. This is done using the
 * ISIN or the symbol of the security. At Yahoo, the official symbol for non-US
 * shares is often extended with a suffix. For example, the Yahoo symbol NOVN.SW
 * identifies the stock exchange Swiss Exchange for the share with the official
 * symbol NOVN.
 */
public class YahooSymbolSearch {

  private static final String DOMAIN_NAME_WITH_SEARCH = "https://query1.finance.yahoo.com/v1/finance/search";
  private static final Logger log = LoggerFactory.getLogger(YahooSymbolSearch.class);

  /**
   * The first step is to search using the ISIN, which is unique. If this is not
   * successful, the symbol is used.
   * 
   * @param micProviderMapRepository
   * @param mic
   * @param isin
   * @param symbol
   * @param name                     The name is not currently being searched for.
   * @return
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
   * It is possible that the entries for the return do not match the exchange
   * location transferred. Therefore, passing the check with the symbol and its
   * suffix is also accepted. For example, the symbol NOVN returns “EBS” as the
   * exchange, but “SWX” would be expected. However, we also accept the symbol
   * NOVN.SW when selecting the correct entry.</br>
   * https://de.hilfe.yahoo.com/kb/B%C3%B6rsen-und-Datenanbieter-auf-Yahoo-Finanzen-sln2310.html
   * 
   * @param query
   * @param yahooExchangeCode Yahoo does not use the MIC for an exchange. It uses
   *                          a different code. This code is used to determine the
   *                          correct entry from the return of the request.
   * @param symbol
   * @param suffix
   * @return
   */
  private String serachSymbol(String query, String yahooExchangeCode, String symbol, String suffix) {
    String completeUrl = DOMAIN_NAME_WITH_SEARCH + "?" + createParams(query);
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder().GET().uri(URI.create(completeUrl)).build();
    HttpResponse<String> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == HttpURLConnection.HTTP_OK) {
        List<Quote> quotes = mapToObject(response.body());
        Optional<Quote> qouteOpt = quotes.stream().filter(q -> q.exchange.equals(yahooExchangeCode)).findFirst();
        if (qouteOpt.isPresent()) {
          return qouteOpt.get().symbol;
        } else if (symbol != null) {
          qouteOpt = quotes.stream().filter(q -> symbol.equals(q.symbol)).findFirst();
          if (qouteOpt.isPresent()) {
            return qouteOpt.get().symbol;
          } else {
            String symbolSuffix = symbol + suffix;
            qouteOpt = quotes.stream().filter(q -> symbolSuffix.equals(q.symbol)).findFirst();
          }
          return qouteOpt.isPresent() ? qouteOpt.get().symbol : null;
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
