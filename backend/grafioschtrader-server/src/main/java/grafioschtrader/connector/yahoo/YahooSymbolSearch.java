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

public class YahooSymbolSearch {

  private static final String DOMAIN_NAME_WITH_SEARCH = "https://query1.finance.yahoo.com/v1/finance/search";
  private static final Logger log = LoggerFactory.getLogger(YahooSymbolSearch.class);
  
  
  public String getSymbolByISINOrSymbolOrName(MicProviderMapRepository micProviderMapRepository, String mic,
      String isin, String symbol, String name) {
    Optional<MicProviderMap> micProviderMapOpt = micProviderMapRepository
        .findById(new IdProviderMic(YahooHelper.YAHOO, mic));
    String yahooSymbol = null;
    if (micProviderMapOpt.isPresent() || symbol != null) {
      if (isin != null) {
        yahooSymbol = serachSymbol(isin, micProviderMapOpt.get().getCodeProvider(), symbol);
      }
      if (yahooSymbol == null && symbol != null) {
        yahooSymbol = serachSymbol(symbol, micProviderMapOpt.get().getCodeProvider(), symbol);
      }

    }
    return yahooSymbol;
  }

  private String serachSymbol(String query, String yahooExchangeCode, String symbol) {
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
          return qouteOpt.isPresent() ? qouteOpt.get().symbol : null;
        }
      } else {
        log.error("Get status code {} for query {} and symbol {}", response.statusCode(), query, symbol);
      }
    } catch (IOException | InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
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
