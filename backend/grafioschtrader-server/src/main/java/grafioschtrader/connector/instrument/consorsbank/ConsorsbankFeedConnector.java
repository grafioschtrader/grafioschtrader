package grafioschtrader.connector.instrument.consorsbank;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.common.DataHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Security;

@Component
public class ConsorsbankFeedConnector extends BaseFeedConnector {

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static String URL_WITH_PLACEHOLDERS = "https://www.consorsbank.de/web-financialinfo-service/api/marketdata/stocks?id=?1&field=PriceV2&rtExchangeCode=?2";

  
  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.INTRA,
        new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }

  public ConsorsbankFeedConnector() {
    super(supportedFeed, "consorsbank", "Consorsbank", "^_[0-9]+,[A-Z,@]+$");
  }
  
  @Override
  public int getIntradayDelayedSeconds() {
    return 900;
  }

  @Override
  public String getSecurityIntradayDownloadLink(final Security security) {
    var idExchangeSplit = security.getUrlIntraExtend().split(",");
    return URL_WITH_PLACEHOLDERS.replace("?1", idExchangeSplit[0]).replace("?2", idExchangeSplit[0]);
  }
  
  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    HttpClient httpClient = HttpClient.newBuilder().build();
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getSecurityIntradayDownloadLink(security))).GET()
        .build();
    HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

    parseJsonData(security, response.body());
  }
  
  private void parseJsonData(final Security security, final String jsonData) throws JsonMappingException, JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    RootData[] rootData = mapper.readValue(jsonData, RootData[].class);
    PriceV2 priceV2 = rootData[0].PriceV2;
    security.setSLast(priceV2.PRICE);
    security.setSChangePercentage(DataHelper.roundStandard(priceV2.PERFORMANCE_PCT));
    security.setSPrevClose(priceV2.PREVIOUS_LAST);
    security.setSLow(priceV2.LOW);
    security.setSHigh(priceV2.HIGH);
    security.setSTimestamp(new Date(System.currentTimeMillis() - getIntradayDelayedSeconds() * 1000));
  }
  
  static class RootData {
    public PriceV2 PriceV2;
  }
  
  static class PriceV2 {
    public Double PERFORMANCE;
    public Double PERFORMANCE_PCT;
    public Double HIGH;
    public Double PRICE;
    public Double LOW;
    public Double PREVIOUS_LAST;
    
    @Override
    public String toString() {
      return "PriceV2 [PERFORMANCE=" + PERFORMANCE + ", PRICE=" + PRICE + "]";
    }
    
    
  }
  
}
