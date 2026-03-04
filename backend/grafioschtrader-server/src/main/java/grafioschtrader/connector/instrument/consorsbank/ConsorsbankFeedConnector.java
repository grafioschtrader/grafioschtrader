package grafioschtrader.connector.instrument.consorsbank;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Security;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * A regex check of the URL extension is active. The connector for checking the instrument apparently always returns an
 * HTTP OK. However, the body of the return contains the text "ERROR_CODE", which is checked for.
 */
@Component
public class ConsorsbankFeedConnector extends BaseFeedConnector {

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private static String URL_WITH_PLACEHOLDERS = "https://www.consorsbank.de/web-financialinfo-service/api/marketdata/stocks?id=?1&field=PriceV2&rtExchangeCode=?2";
  private final Logger log = LoggerFactory.getLogger(this.getClass());

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_INTRA, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }

  public ConsorsbankFeedConnector() {
    super(supportedFeed, "consorsbank", "Consorsbank", "^_[0-9]+,[A-Z,@]+$", EnumSet.of(UrlCheck.INTRADAY));
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
  protected boolean isConnectionOk(HttpURLConnection huc) {
    try {
      return getBodyAsString(huc).indexOf("ERROR_CODE") == -1;
    } catch (IOException e) {
      log.error("Could not open connection", e);
    }
    return true;
  }

  @Override
  public void updateSecurityLastPrice(final Security security) throws Exception {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getSecurityIntradayDownloadLink(security))).GET()
        .build();
    HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

    parseJsonData(security, response.body());
  }

  private void parseJsonData(final Security security, final String jsonData)
      throws Exception {
    ObjectMapper mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    RootData[] rootData = mapper.readValue(jsonData, RootData[].class);
    PriceV2 priceV2 = rootData[0].PriceV2;
    security.setSLast(priceV2.PRICE);
    security.setSChangePercentage(DataBusinessHelper.roundStandard(priceV2.PERFORMANCE_PCT));
    security.setSPrevClose(priceV2.PREVIOUS_LAST);
    security.setSLow(priceV2.LOW);
    security.setSHigh(priceV2.HIGH);
    security.setSTimestamp(LocalDateTime.now().minusSeconds(getIntradayDelayedSeconds()));
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
