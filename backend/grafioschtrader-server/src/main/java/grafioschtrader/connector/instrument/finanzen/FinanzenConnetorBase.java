package grafioschtrader.connector.instrument.finanzen;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.EnumSet;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;

public abstract class FinanzenConnetorBase extends BaseFeedConnector {

  private  CookieManager cookieManager;
  private  HttpClient client;
 
  private final Logger log = LoggerFactory.getLogger(this.getClass());
  
  
  public FinanzenConnetorBase(Map<FeedSupport, FeedIdentifier[]> supportedFeed, String id, String readableName,
      String regexStrPattern, EnumSet<UrlCheck> urlCheckSet) {
    super(supportedFeed, id, readableName, regexStrPattern, urlCheckSet);
  }

  protected void initalizeHttpClient(String domain) {
    this.cookieManager = new CookieManager();
    cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
     this.client = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .cookieHandler(this.cookieManager) 
        .build();
    try {
      HttpRequest request = getRequest(domain);
      client.send(request, BodyHandlers.ofString());
    } catch (IOException | InterruptedException e) {
      log.error("A connection to {} is not possible.", domain, e);
    }
  }
  
  protected Document getDoc(String url) throws IOException, InterruptedException {
    HttpClient client = getHttpClient();
    HttpRequest request = getRequest(url);
    HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
    return Jsoup.parse(response.body());
  }
  
  @Override
  protected HttpRequest getRequest(String url) {
    return HttpRequest.newBuilder()
        .header("Accept", "*/*")
        .header("Accept-Encoding", "deflate, br")
        .header("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT).uri(URI.create(url)).build();
  }
  
  @Override
  protected HttpClient getHttpClient() {
    return this.client;
  }
}
